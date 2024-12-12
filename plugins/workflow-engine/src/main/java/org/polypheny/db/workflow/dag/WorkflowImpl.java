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

package org.polypheny.db.workflow.dag;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.util.Pair;
import org.polypheny.db.util.graph.AttributedDirectedGraph;
import org.polypheny.db.util.graph.CycleDetector;
import org.polypheny.db.util.graph.TopologicalOrderIterator;
import org.polypheny.db.workflow.dag.activities.ActivityException;
import org.polypheny.db.workflow.dag.activities.ActivityWrapper;
import org.polypheny.db.workflow.dag.activities.ActivityWrapper.ActivityState;
import org.polypheny.db.workflow.dag.edges.DataEdge;
import org.polypheny.db.workflow.dag.edges.Edge;
import org.polypheny.db.workflow.dag.edges.Edge.EdgeState;
import org.polypheny.db.workflow.dag.settings.SettingDef.SettingsPreview;
import org.polypheny.db.workflow.dag.variables.VariableStore;
import org.polypheny.db.workflow.engine.scheduler.ExecutionEdge;
import org.polypheny.db.workflow.engine.scheduler.ExecutionEdge.ExecutionEdgeFactory;
import org.polypheny.db.workflow.engine.storage.StorageManager;
import org.polypheny.db.workflow.models.ActivityConfigModel.CommonType;
import org.polypheny.db.workflow.models.ActivityModel;
import org.polypheny.db.workflow.models.EdgeModel;
import org.polypheny.db.workflow.models.WorkflowConfigModel;
import org.polypheny.db.workflow.models.WorkflowModel;

public class WorkflowImpl implements Workflow {

    private final Map<UUID, ActivityWrapper> activities;
    private final Map<Pair<UUID, UUID>, List<Edge>> edges;
    @Getter
    private final WorkflowConfigModel config;
    @Getter
    @Setter
    private WorkflowState state = WorkflowState.IDLE;
    @Getter
    private final VariableStore variables = new VariableStore(); // contains "static" variables (= defined before execution starts)


    public WorkflowImpl() {
        this( new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), WorkflowConfigModel.of(), Map.of() );
    }


    private WorkflowImpl( Map<UUID, ActivityWrapper> activities, Map<Pair<UUID, UUID>, List<Edge>> edges, WorkflowConfigModel config, Map<String, JsonNode> variables ) {
        this.activities = activities;
        this.edges = edges;
        this.config = config;
        this.variables.reset( variables );

        // TODO: compute previews & variables
        TopologicalOrderIterator.of( toDag() ).forEach( this::updatePreview );
    }


    public static Workflow fromModel( WorkflowModel model ) {

        Map<UUID, ActivityWrapper> activities = new ConcurrentHashMap<>();
        Map<Pair<UUID, UUID>, List<Edge>> edges = new ConcurrentHashMap<>();

        for ( ActivityModel a : model.getActivities() ) {
            activities.put( a.getId(), ActivityWrapper.fromModel( a ) );
        }
        for ( EdgeModel e : model.getEdges() ) {
            Pair<UUID, UUID> key = Pair.of( e.getFromId(), e.getToId() );
            List<Edge> edgeList = edges.computeIfAbsent( key, k -> new ArrayList<>() );
            edgeList.add( Edge.fromModel( e, activities ) );
        }

        return new WorkflowImpl( activities, edges, model.getConfig(), model.getVariables() );
    }


    @Override
    public List<ActivityWrapper> getActivities() {
        return new ArrayList<>( activities.values() );
    }


    @Override
    public ActivityWrapper getActivity( UUID activityId ) {
        return activities.get( activityId );
    }


    @Override
    public List<Edge> getEdges() {
        return edges.values()
                .stream()
                .flatMap( List::stream )
                .toList();
    }


    @Override
    public List<Edge> getEdges( UUID from, UUID to ) {
        return Collections.unmodifiableList( edges.getOrDefault( Pair.of( from, to ), new ArrayList<>() ) );
    }


    @Override
    public List<Edge> getEdges( ActivityWrapper from, ActivityWrapper to ) {
        return getEdges( from.getId(), to.getId() );
    }


    @Override
    public List<Edge> getInEdges( UUID target ) {
        // TODO: make more efficient
        return getEdges().stream().filter( e -> e.getTo().getId().equals( target ) ).toList();
    }


    @Override
    public List<Edge> getOutEdges( UUID source ) {
        // TODO: make more efficient
        return getEdges().stream().filter( e -> e.getFrom().getId().equals( source ) ).toList();
    }


    @Override
    public Edge getEdge( EdgeModel model ) {
        List<Edge> candidates = getEdges( model.getFromId(), model.getToId() );
        for ( Edge e : candidates ) {
            if ( e.isEquivalent( model ) ) {
                return e;
            }
        }
        return null;
    }


    @Override
    public Edge getEdge( ExecutionEdge execEdge ) {
        List<Edge> candidates = edges.get( Pair.of( execEdge.getSource(), execEdge.getTarget() ) );
        if ( candidates != null ) {
            for ( Edge candidate : candidates ) {
                if ( execEdge.representsEdge( candidate ) ) {
                    return candidate;
                }
            }
        }
        return null;
    }


    @Override
    public DataEdge getDataEdge( UUID to, int toPort ) {
        for ( Edge edge : getInEdges( to ) ) {
            if ( edge instanceof DataEdge dataEdge ) {
                if ( dataEdge.getToPort() == toPort ) {
                    return dataEdge;
                }
            }
        }
        return null;
    }


    @Override
    public void updatePreview( UUID activityId ) {
        try {
            updatePreview( activityId, false );
        } catch ( ActivityException ignored ) {
            assert false;
        }
    }


    @Override
    public void updateValidPreview( UUID activityId ) throws ActivityException {
        updatePreview( activityId, true );
    }


    private void updatePreview( UUID activityId, boolean throwIfInvalid ) throws ActivityException {
        ActivityWrapper wrapper = getActivity( activityId );
        if ( wrapper.getState().isExecuted() ) {
            return; // when an activity can be executed, it's previews won't change anymore
        }
        recomputeInVariables( activityId );
        List<Optional<AlgDataType>> inTypes = getInputTypes( activityId );
        wrapper.setInTypePreview( inTypes );
        try {
            SettingsPreview settings = wrapper.updateOutTypePreview( inTypes, hasStableInVariables( activityId ) );
            wrapper.setSettingsPreview( settings );
        } catch ( ActivityException e ) {
            if ( throwIfInvalid ) {
                throw e;
            } else {
                e.printStackTrace(); // TODO: make sure ignoring inconsistency is okay
            }
        }
    }


    @Override
    public void recomputeInVariables( UUID activityId ) {
        ActivityWrapper wrapper = activities.get( activityId );
        wrapper.getVariables().mergeInputStores( getInEdges( activityId ), wrapper.getDef().getInPorts().length, variables );
    }


    @Override
    public boolean hasStableInVariables( UUID activityId ) {
        for ( Edge edge : getInEdges( activityId ) ) {
            if ( edge.getState() == EdgeState.IDLE && !edge.isIgnored() ) {
                return false;
            }
        }
        return true;
    }


    @Override
    public List<Optional<AlgDataType>> getInputTypes( UUID activityId ) {
        List<Optional<AlgDataType>> inputTypes = new ArrayList<>();

        for ( int i = 0; i < getInPortCount( activityId ); i++ ) {
            DataEdge dataEdge = getDataEdge( activityId, i );
            if ( dataEdge == null ) {
                inputTypes.add( Optional.empty() ); // not yet connected
            } else if ( dataEdge.getState() == EdgeState.INACTIVE ) {
                inputTypes.add( null );
            } else {
                inputTypes.add( dataEdge.getFrom().getOutTypePreview().get( dataEdge.getFromPort() ) );
            }
        }
        return Collections.unmodifiableList( inputTypes );
    }


    @Override
    public int getInPortCount( UUID activityId ) {
        return activities.get( activityId ).getDef().getInPorts().length;
    }


    @Override
    public void addActivity( ActivityWrapper activity ) {
        if ( activities.containsKey( activity.getId() ) ) {
            throw new GenericRuntimeException( "Cannot add activity instance that is already part of this workflow." );
        }
        activities.put( activity.getId(), activity );
        updatePreview( activity.getId() ); // creates empty previews
    }


    @Override
    public void deleteActivity( UUID activityId ) {
        edges.entrySet().removeIf( entry -> entry.getKey().left.equals( activityId ) || entry.getKey().right.equals( activityId ) );
        activities.remove( activityId );
    }


    @Override
    public void deleteEdge( EdgeModel model ) {
        List<Edge> edgeList = edges.get( model.toPair() );
        if ( edgeList == null ) {
            return;
        }
        edgeList.removeIf( e -> e.isEquivalent( model ) );
        // TODO: reset target activity and all successors, update previews
    }


    @Override
    public AttributedDirectedGraph<UUID, ExecutionEdge> toDag() {
        AttributedDirectedGraph<UUID, ExecutionEdge> dag = AttributedDirectedGraph.create( new ExecutionEdgeFactory() );

        activities.keySet().forEach( dag::addVertex );
        getEdges().forEach( edge -> dag.addEdge( edge.getFrom().getId(), edge.getTo().getId(), edge ) );

        return dag;
    }


    @Override
    public void validateStructure( StorageManager sm ) throws Exception {
        validateStructure( sm, toDag() );
    }


    @Override
    public void validateStructure( StorageManager sm, AttributedDirectedGraph<UUID, ExecutionEdge> subDag ) throws IllegalStateException {
        if ( subDag.vertexSet().isEmpty() && subDag.edgeSet().isEmpty() ) {
            return;
        }

        for ( ExecutionEdge execEdge : subDag.edgeSet() ) {
            if ( !activities.containsKey( execEdge.getSource() ) || !activities.containsKey( execEdge.getTarget() ) ) {
                throw new IllegalStateException( "Source and target activities of an edge must be part of the workflow: " + execEdge );
            }
            Edge edge = getEdge( execEdge );
            if ( edge instanceof DataEdge data && !data.isCompatible() ) {
                throw new IllegalStateException( "Incompatible port types for data edge: " + edge );
            }
        }

        if ( !(new CycleDetector<>( subDag ).findCycles().isEmpty()) ) {
            throw new IllegalStateException( "A workflow must not contain cycles" );
        }

        for ( UUID n : TopologicalOrderIterator.of( subDag ) ) {
            ActivityWrapper wrapper = getActivity( n );
            CommonType type = wrapper.getConfig().getCommonType();

            if ( wrapper.getState() == ActivityState.SAVED ) {
                if ( !sm.hasAllCheckpoints( n, wrapper.getDef().getOutPorts().length ) ) {
                    throw new IllegalStateException( "Found missing checkpoint for saved activity: " + wrapper );
                }
            } else if ( wrapper.getState() != ActivityState.FINISHED ) {
                for ( int i = 0; i < wrapper.getDef().getOutPorts().length; i++ ) {
                    if ( sm.hasCheckpoint( n, i ) ) {
                        throw new IllegalStateException( "Found a checkpoint for an activity that has not yet been executed successfully: " + wrapper );
                    }
                }
            }

            Set<Integer> requiredInPorts = wrapper.getDef().getRequiredInPorts();
            Set<Integer> occupiedInPorts = new HashSet<>();
            for ( ExecutionEdge execEdge : subDag.getInwardEdges( n ) ) {
                ActivityWrapper source = getActivity( execEdge.getSource() );
                CommonType sourceType = source.getConfig().getCommonType();
                int toPort = execEdge.getToPort();

                requiredInPorts.remove( toPort );

                if ( occupiedInPorts.contains( toPort ) ) {
                    throw new IllegalStateException( "InPort " + toPort + " is already occupied: " + execEdge );
                }
                occupiedInPorts.add( toPort );

                if ( wrapper.getState().isExecuted() && !source.getState().isExecuted() ) {
                    throw new IllegalStateException( "An activity that is executed cannot have a not yet executed predecessor: " + execEdge );
                }
                if ( type == CommonType.EXTRACT ) {
                    if ( sourceType != CommonType.EXTRACT ) {
                        throw new IllegalStateException( "An activity with CommonType EXTRACT must only have EXTRACT predecessors: " + execEdge );
                    }
                    if ( execEdge.isControl() && !execEdge.isOnSuccess() ) {
                        throw new IllegalStateException( "Cannot have a onFail control edge between common EXTRACT activities" + execEdge );
                    }
                } else if ( sourceType == CommonType.LOAD ) {
                    if ( type != CommonType.LOAD ) {
                        throw new IllegalStateException( "An activity with CommonType LOAD must only have LOAD successors: " + execEdge );
                    }
                    if ( execEdge.isControl() && !execEdge.isOnSuccess() ) {
                        throw new IllegalStateException( "Cannot have a onFail control edge between common LOAD activities" + execEdge );
                    }
                }

            }
            if ( !requiredInPorts.isEmpty() ) {
                throw new IllegalStateException( "Activity is missing the required data input(s) " + requiredInPorts + ": " + wrapper );
            }
        }

        // compatible settings ?

    }


    @Override
    public String toString() {
        return "WorkflowImpl{" +
                "\n    activities=" + getActivities() +
                ", \n    edges=" + getEdges() +
                ", \n    config=" + config +
                ", \n    state=" + state +
                ", \n    variables=" + variables +
                "\n}";
    }

}
