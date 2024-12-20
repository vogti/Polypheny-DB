/*
 * Copyright 2019-2024 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.PolyphenyDb;
import org.polypheny.db.adapter.java.AdapterTemplate;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.LogicalAdapter.AdapterType;
import org.polypheny.db.catalog.logistic.DataModel;
import org.polypheny.db.ddl.DdlManager;
import org.polypheny.db.util.RunMode;
import org.polypheny.db.webui.ConfigService.HandlerType;
import org.polypheny.db.webui.HttpServer;
import org.polypheny.db.workflow.dag.activities.ActivityWrapper;
import org.polypheny.db.workflow.engine.execution.context.ExecutionContextImpl;
import org.polypheny.db.workflow.engine.storage.StorageManager;
import org.polypheny.db.workflow.engine.storage.StorageManagerImpl;
import org.polypheny.db.workflow.engine.storage.reader.CheckpointReader;
import org.polypheny.db.workflow.models.ActivityConfigModel;
import org.polypheny.db.workflow.models.ActivityModel;
import org.polypheny.db.workflow.models.EdgeModel;
import org.polypheny.db.workflow.models.RenderModel;
import org.polypheny.db.workflow.models.WorkflowConfigModel;
import org.polypheny.db.workflow.models.WorkflowModel;
import org.polypheny.db.workflow.models.requests.CreateSessionRequest;
import org.polypheny.db.workflow.models.requests.SaveSessionRequest;
import org.polypheny.db.workflow.repo.WorkflowRepo;
import org.polypheny.db.workflow.repo.WorkflowRepo.WorkflowRepoException;
import org.polypheny.db.workflow.repo.WorkflowRepoImpl;
import org.polypheny.db.workflow.session.SessionManager;
import org.polypheny.db.workflow.session.WorkflowWebSocket;

public class WorkflowManager {

    private final SessionManager sessionManager;
    private final WorkflowRepo repo;
    public final String PATH = "/workflows";
    private final ObjectMapper mapper = new ObjectMapper();


    public WorkflowManager() {
        repo = WorkflowRepoImpl.getInstance();
        sessionManager = SessionManager.getInstance();
        registerEndpoints();

        createExecuteDummyWorkflowTest();

        // waiting with test to ensure everything has started
        new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        registerAdapter(); // TODO: only register adapter when the first workflow is opened
                        addSampleWorkflows();
                    }
                },
                1000
        );
    }


    /**
     * Add a route that tests the relExtract activity.
     * A target relational entity can be specified via path parameters.
     * The entity is read by the relExtract activity and the result is written to a checkpoint.
     */
    private void createExecuteDummyWorkflowTest() {
        HttpServer server = HttpServer.getInstance();
        server.addSerializedRoute( PATH + "/executeDummy/{namespaceName}/{tableName}/{storeName}", ctx -> {
            System.out.println( "handling dummy execution..." );

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode setting = mapper.createObjectNode();
            setting.put( "namespace", ctx.pathParam( "namespaceName" ) );
            setting.put( "name", ctx.pathParam( "tableName" ) );

            String storeName = ctx.pathParam( "storeName" );
            Map<DataModel, String> stores = Map.of(
                    DataModel.RELATIONAL, storeName,
                    DataModel.DOCUMENT, storeName,
                    DataModel.GRAPH, storeName
            );

            try ( StorageManager sm = new StorageManagerImpl( UUID.randomUUID(), stores ) ) {
                UUID activityId = UUID.randomUUID();

                ActivityWrapper wrapper = ActivityWrapper.fromModel( new ActivityModel(
                        "relExtract",
                        activityId,
                        Map.of( "table", setting ),
                        ActivityConfigModel.of(),
                        RenderModel.of()
                ) );

                long start = System.currentTimeMillis();
                wrapper.getActivity().execute( List.of(), wrapper.resolveSettings(), new ExecutionContextImpl( wrapper, sm, null ) );

                sm.commitTransaction( activityId );
                long extractTimeMs = System.currentTimeMillis() - start;
                System.out.println( "Extract time in ms: " + extractTimeMs );

                UUID activityId2 = UUID.randomUUID();
                ActivityWrapper loadWrapper = ActivityWrapper.fromModel( new ActivityModel(
                        "relLoad",
                        activityId2,
                        Map.of( "table", setting ),
                        ActivityConfigModel.of(),
                        RenderModel.of()
                ) );

                start = System.currentTimeMillis();
                try ( CheckpointReader reader = sm.readCheckpoint( activityId, 0 ) ) {
                    loadWrapper.getActivity().execute( List.of( reader ), loadWrapper.resolveSettings(), new ExecutionContextImpl( loadWrapper, sm, null ) );
                }
                sm.commitTransaction( activityId2 );
                long loadTimeMs = System.currentTimeMillis() - start;
                System.out.println( "Load time in ms: " + loadTimeMs );

                ctx.json( Map.of( "Extract Time", extractTimeMs, "Load Time", loadTimeMs ) );
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }, HandlerType.GET );

    }


    private void addSampleWorkflows() {
        try {
            UUID id = repo.createWorkflow( "Test Workflow" );
            repo.writeVersion( id, "Initial Version", getWorkflow1() );

        } catch ( WorkflowRepoException e ) {
            throw new RuntimeException( e );
        }
    }


    // TODO: replace with workflows in resource directory
    private WorkflowModel getWorkflow( List<ActivityModel> activities, List<EdgeModel> edges, boolean fusionEnabled, boolean pipelineEnabled, int maxWorkers ) {
        WorkflowConfigModel config = new WorkflowConfigModel(
                Map.of( DataModel.RELATIONAL, "hsqldb", DataModel.DOCUMENT, "hsqldb", DataModel.GRAPH, "hsqldb" ),
                fusionEnabled,
                pipelineEnabled,
                maxWorkers,
                10 // low on purpose to observe blocking
        );
        Map<String, JsonNode> variables = Map.of( "creationTime", TextNode.valueOf( LocalDateTime.now().format( DateTimeFormatter.ISO_DATE_TIME ) ) );
        return new WorkflowModel( activities, edges, config, variables );
    }


    // TODO: replace with workflows in resource directory
    private WorkflowModel getWorkflow1() {
        List<ActivityModel> activities = List.of(
                new ActivityModel( "relValues" ),
                new ActivityModel( "debug" )
        );
        List<EdgeModel> edges = List.of(
                EdgeModel.of( activities.get( 0 ), activities.get( 1 ) )
        );
        return getWorkflow( activities, edges, false, false, 1 );
    }


    private void registerAdapter() {
        if ( PolyphenyDb.mode == RunMode.TEST || Catalog.getInstance().getAdapters().values().stream().anyMatch( a -> a.adapterName.equals( "hsqldb_disk" ) ) ) {
            return;
        }

        AdapterTemplate storeTemplate = Catalog.snapshot().getAdapterTemplate( "HSQLDB", AdapterType.STORE ).orElseThrow();
        Map<String, String> settings = new HashMap<>( storeTemplate.getDefaultSettings() );
        settings.put( "trxControlMode", "locks" );
        settings.put( "type", "File" );
        settings.put( "tableType", "Cached" );

        DdlManager.getInstance().createStore( "hsqldb_disk", storeTemplate.getAdapterName(), AdapterType.STORE, settings, storeTemplate.getDefaultMode() );
    }


    private void registerEndpoints() {
        HttpServer server = HttpServer.getInstance();

        server.addWebsocketRoute( PATH + "/webSocket/{sessionId}", new WorkflowWebSocket() );

        server.addSerializedRoute( PATH + "/sessions", this::getSessions, HandlerType.GET );
        server.addSerializedRoute( PATH + "/sessions/{sessionId}", this::getSession, HandlerType.GET );
        server.addSerializedRoute( PATH + "/sessions/{sessionId}/workflow", this::getActiveWorkflow, HandlerType.GET );
        server.addSerializedRoute( PATH + "/sessions/{sessionId}/workflow/config", this::getWorkflowConfig, HandlerType.GET );
        server.addSerializedRoute( PATH + "/sessions/{sessionId}/workflow/{activityId}", this::getActivity, HandlerType.GET );
        server.addSerializedRoute( PATH + "/sessions/{sessionId}/workflow/{activityId}/{outIndex}", this::getIntermediaryResult, HandlerType.GET );
        server.addSerializedRoute( PATH + "/workflows", this::getWorkflowDefs, HandlerType.GET );

        server.addSerializedRoute( PATH + "/sessions", this::createSession, HandlerType.POST );
        server.addSerializedRoute( PATH + "/sessions/{sessionId}/save", this::saveSession, HandlerType.POST );
        server.addSerializedRoute( PATH + "/workflows/{workflowId}/{version}", this::openWorkflow, HandlerType.POST );

        server.addSerializedRoute( PATH + "/sessions/{sessionId}", this::terminateSession, HandlerType.DELETE );
    }


    private void getSessions( final Context ctx ) {
        process( ctx, sessionManager::getSessionModels );
    }


    private void getSession( final Context ctx ) {
        UUID sessionId = UUID.fromString( ctx.pathParam( "sessionId" ) );
        process( ctx, () -> sessionManager.getSessionModel( sessionId ) );
    }


    private void getActiveWorkflow( final Context ctx ) {
        UUID sessionId = UUID.fromString( ctx.pathParam( "sessionId" ) );
        process( ctx, () -> sessionManager.getSessionOrThrow( sessionId ).getWorkflowModel( true ) );
    }


    private void getWorkflowConfig( final Context ctx ) {
        UUID sessionId = UUID.fromString( ctx.pathParam( "sessionId" ) );
        process( ctx, () -> sessionManager.getSessionOrThrow( sessionId ).getWorkflowConfig() );
    }


    private void getActivity( final Context ctx ) {
        UUID sessionId = UUID.fromString( ctx.pathParam( "sessionId" ) );
        UUID activityId = UUID.fromString( ctx.pathParam( "activityId" ) );
        process( ctx, () -> sessionManager.getSessionOrThrow( sessionId ).getActivityModel( activityId ) );
    }


    private void getIntermediaryResult( final Context ctx ) {
        UUID sessionId = UUID.fromString( ctx.pathParam( "sessionId" ) );
        UUID activityId = UUID.fromString( ctx.pathParam( "activityId" ) );
        int outIndex = Integer.parseInt( ctx.pathParam( "outIndex" ) );
        throw new NotImplementedException();
        //process( ctx, () -> sessionManager.getSessionOrThrow( sessionId ) );
    }


    private void getWorkflowDefs( final Context ctx ) {
        process( ctx, repo::getWorkflowDefs );
    }


    private void createSession( final Context ctx ) {
        CreateSessionRequest request = ctx.bodyAsClass( CreateSessionRequest.class );
        process( ctx, () -> sessionManager.createUserSession( request.getName() ) );
    }


    private void openWorkflow( final Context ctx ) {
        UUID workflowId = UUID.fromString( ctx.pathParam( "workflowId" ) );
        int version = Integer.parseInt( ctx.pathParam( "version" ) );
        // TODO: combine with CreateSessionRequest into createSession endpoint?
        process( ctx, () -> sessionManager.createUserSession( workflowId, version ) );
    }


    private void saveSession( final Context ctx ) {
        UUID sessionId = UUID.fromString( ctx.pathParam( "sessionId" ) );
        SaveSessionRequest request = ctx.bodyAsClass( SaveSessionRequest.class );
        process( ctx, () -> {
            sessionManager.saveUserSession( sessionId, request.getMessage() );
            return "success";
        } );
    }


    private void terminateSession( final Context ctx ) {
        UUID sessionId = UUID.fromString( ctx.pathParam( "sessionId" ) );
        process( ctx, () -> {
            sessionManager.terminateSession( sessionId );
            return "success";
        } );
    }


    private void process( Context ctx, ResultSupplier s ) {
        try {
            sendResult( ctx, s.get() );
        } catch ( Exception e ) {
            // TODO: better error handling
            ctx.status( HttpCode.INTERNAL_SERVER_ERROR );
            ctx.json( e );
            e.printStackTrace();
        }
    }


    private void sendResult( Context ctx, Object model ) {
        ctx.contentType( ContentType.JSON );
        try {
            ctx.result( mapper.writeValueAsString( model ) );
        } catch ( JsonProcessingException e ) {
            throw new RuntimeException( e );
        }
    }


    @FunctionalInterface
    private interface ResultSupplier {

        Object get() throws WorkflowRepoException;

    }

}