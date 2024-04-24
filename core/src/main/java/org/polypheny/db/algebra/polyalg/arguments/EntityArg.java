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

package org.polypheny.db.algebra.polyalg.arguments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NonNull;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.polyalg.PolyAlgDeclaration.ParamType;
import org.polypheny.db.catalog.entity.Entity;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.catalog.logistic.DataModel;
import org.polypheny.db.catalog.snapshot.Snapshot;

public class EntityArg implements PolyAlgArg {

    @Getter
    private final Entity entity;

    private final String namespaceName;
    private final String entityName; // for graphs, entityName is null


    /**
     * Creates an EntityArg for an entity which is used in an AlgNode with the specified DataModel.
     */
    public EntityArg( Entity entity, Snapshot snapshot, DataModel model ) {
        this.namespaceName = getNamespaceName( entity, snapshot );
        this.entity = entity;

        if ( model == DataModel.GRAPH || entity.dataModel == DataModel.GRAPH ) {
            // origin or target data model is graph -> only namespaceName is relevant
            this.entityName = null;
        } else {
            this.entityName = entity.getName();
        }
    }


    private String getNamespaceName( Entity entity, Snapshot snapshot ) {
        String nsName;
        try {
            nsName = entity.getNamespaceName();
        } catch ( UnsupportedOperationException e ) {
            Optional<LogicalNamespace> ns = snapshot.getNamespace( entity.namespaceId );
            nsName = ns.map( LogicalNamespace::getName ).orElse( null );
        }
        return nsName;
    }


    @Override
    public ParamType getType() {
        return ParamType.ENTITY;
    }


    @Override
    public String toPolyAlg( AlgNode context, @NonNull List<String> inputFieldNames ) {
        if ( entityName == null ) {
            return namespaceName;
        }
        return namespaceName + "." + entityName;
    }


    @Override
    public ObjectNode serialize( AlgNode context, @NonNull List<String> inputFieldNames, ObjectMapper mapper ) {
        ObjectNode node = mapper.createObjectNode();
        node.put( "arg", toPolyAlg( context, inputFieldNames ) );
        if ( entity != null ) {
            node.put( "namespaceId", entity.namespaceId );
            node.put( "id", entity.id );
        }
        return node;
    }


}