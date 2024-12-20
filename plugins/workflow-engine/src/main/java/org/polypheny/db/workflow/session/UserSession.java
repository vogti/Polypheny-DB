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

package org.polypheny.db.workflow.session;

import java.util.Map.Entry;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.NotImplementedException;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.catalog.logistic.DataModel;
import org.polypheny.db.workflow.dag.Workflow;
import org.polypheny.db.workflow.dag.Workflow.WorkflowState;
import org.polypheny.db.workflow.dag.activities.ActivityWrapper;
import org.polypheny.db.workflow.models.SessionModel;
import org.polypheny.db.workflow.models.SessionModel.SessionModelType;
import org.polypheny.db.workflow.models.requests.WsRequest.CreateActivityRequest;
import org.polypheny.db.workflow.models.requests.WsRequest.CreateEdgeRequest;
import org.polypheny.db.workflow.models.requests.WsRequest.DeleteActivityRequest;
import org.polypheny.db.workflow.models.requests.WsRequest.DeleteEdgeRequest;
import org.polypheny.db.workflow.models.requests.WsRequest.ExecuteRequest;
import org.polypheny.db.workflow.models.requests.WsRequest.InterruptRequest;
import org.polypheny.db.workflow.models.requests.WsRequest.ResetRequest;
import org.polypheny.db.workflow.models.requests.WsRequest.UpdateActivityRequest;
import org.polypheny.db.workflow.models.requests.WsRequest.UpdateConfigRequest;
import org.polypheny.db.workflow.models.responses.WsResponse.ActivityUpdateResponse;
import org.polypheny.db.workflow.models.responses.WsResponse.RenderingUpdateResponse;
import org.polypheny.db.workflow.models.responses.WsResponse.StateUpdateResponse;

public class UserSession extends AbstractSession {

    @Getter
    private final UUID wId;
    @Getter
    @Setter
    private int openedVersion;


    public UserSession( UUID sessionId, Workflow wf, UUID workflowId, int openedVersion ) {
        super( wf, sessionId );
        this.wId = workflowId;
        this.openedVersion = openedVersion;
    }


    @Override
    public void terminate() {
        if ( workflow.getState() == WorkflowState.EXECUTING ) {
            scheduler.interruptExecution( sessionId );
        }
        throw new NotImplementedException();
    }


    @Override
    public synchronized void handleRequest( CreateActivityRequest request ) {
        throwIfNotEditable();
        ActivityWrapper activity = workflow.addActivity( request.activityType, request.rendering );
        broadcastMessage( new ActivityUpdateResponse( request.msgId, activity ) );
    }


    @Override
    public synchronized void handleRequest( DeleteActivityRequest request ) {
        throwIfNotEditable();
        workflow.deleteActivity( request.targetId, sm );
        broadcastMessage( new StateUpdateResponse( request.msgId, workflow ) );
    }


    @Override
    public void handleRequest( UpdateActivityRequest request ) {
        throwIfNotEditable();
        ActivityWrapper activity = workflow.updateActivity( request.targetId, request.settings, request.config, request.rendering, sm );

        if ( request.rendering != null && request.settings == null && request.config == null ) {
            broadcastMessage( new RenderingUpdateResponse( request.msgId, activity ) );
            return;
        }
        broadcastMessage( new StateUpdateResponse( request.msgId, workflow ) );
        broadcastMessage( new ActivityUpdateResponse( request.msgId, activity ) );
    }


    @Override
    public void handleRequest( CreateEdgeRequest request ) {
        throwIfNotEditable();
        workflow.addEdge( request.edge, sm );
        broadcastMessage( new StateUpdateResponse( request.msgId, workflow ) );
    }


    @Override
    public void handleRequest( DeleteEdgeRequest request ) {
        throwIfNotEditable();
    }


    @Override
    public void handleRequest( UpdateConfigRequest request ) {
        throwIfNotEditable();
        workflow.setConfig( request.workflowConfig );
        for ( Entry<DataModel, String> entry : request.workflowConfig.getPreferredStores().entrySet() ) {
            sm.setDefaultStore( entry.getKey(), entry.getValue() );
        }
        // broadcasting the updated config is not required
    }


    @Override
    public void handleRequest( ResetRequest request ) {
        throwIfNotEditable();
        workflow.reset( request.rootId, sm );
        broadcastMessage( new StateUpdateResponse( request.msgId, workflow ) );
    }


    @Override
    public void handleRequest( ExecuteRequest request ) {
        throwIfNotEditable();
        startExecution( request.targetId );
    }


    @Override
    public void handleRequest( InterruptRequest request ) {
        throwIfNotExecuting();
        interruptExecution();
    }


    @Override
    public SessionModel toModel() {
        return new SessionModel( SessionModelType.USER_SESSION, sessionId, getSubscriberCount(), wId, openedVersion );
    }


    private boolean isEditable() {
        // While we could perform this check within the workflow, we follow the approach where the workflow is not
        // aware of the semantic meaning of its state.
        return workflow.getState() == WorkflowState.IDLE;
    }


    private void throwIfNotEditable() {
        if ( !isEditable() ) {
            throw new GenericRuntimeException( "Workflow is currently not editable." );
        }
    }

}