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

package org.polypheny.db.workflow.dag.edges;

import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.polypheny.db.util.Pair;
import org.polypheny.db.workflow.dag.activities.Activity;
import org.polypheny.db.workflow.models.EdgeModel;

public abstract class Edge {

    @Getter
    final Activity from;
    @Getter
    final Activity to;
    @Getter
    @Setter
    private EdgeState state = EdgeState.IDLE;


    public Edge( Activity from, Activity to ) {
        this.from = from;
        this.to = to;
    }


    public abstract EdgeModel toModel( boolean includeState );


    public static Edge fromModel( EdgeModel model, Map<UUID, Activity> activities ) {
        Activity from = activities.get( model.getFromId() );
        Activity to = activities.get( model.getToId() );
        if ( model.isControl() ) {
            return new ControlEdge( from, to, model.getFromPort() );
        } else {
            return new DataEdge( from, to, model.getFromPort(), model.getToPort() );
        }
    }


    /**
     * Returns true if this Edge is equivalent to the specified EdgeModel in a static context.
     * This means the EdgeStates do not have to be equal.
     * At the very least, this guarantees that source and target activity are the same between model and edge.
     *
     * @param model the model to compare this Edge to
     * @return true if the model is equivalent.
     */
    public abstract boolean isEquivalent( EdgeModel model );


    public boolean hasSameEndpoints( EdgeModel model ) {
        return from.getId().equals( model.getFromId() ) && to.getId().equals( model.getToId() );
    }


    public Pair<UUID, UUID> toPair() {
        return Pair.of( from.getId(), to.getId() );
    }


    public enum EdgeState {
        IDLE,
        ACTIVE,
        INACTIVE
    }

}
