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

package org.polypheny.db.algebra.enumerable;

import java.util.List;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.function.Function2;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.common.Identifier;
import org.polypheny.db.algebra.metadata.AlgMetadataQuery;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.Entity;
import org.polypheny.db.catalog.entity.logical.LogicalEntity;
import org.polypheny.db.plan.AlgCluster;
import org.polypheny.db.plan.AlgOptCost;
import org.polypheny.db.plan.AlgPlanner;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.transaction.locking.IdentifierUtils;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.document.PolyDocument;
import org.polypheny.db.type.entity.graph.GraphPropertyHolder;
import org.polypheny.db.type.entity.numerical.PolyLong;
import org.polypheny.db.util.BuiltInMethod;

public class EnumerableIdentifier extends Identifier implements EnumerableAlg {

    protected EnumerableIdentifier( AlgCluster cluster, AlgTraitSet traits, Entity entity, AlgNode input ) {
        super( cluster, traits, entity, input );
        assert getConvention() instanceof EnumerableConvention;
    }


    @Override
    public AlgOptCost computeSelfCost( AlgPlanner planner, AlgMetadataQuery mq ) {
        double dRows = mq.getTupleCount( getInput() );
        return planner.getCostFactory().makeCost( dRows, 0, 0 );
    }


    @Override
    public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
        return new EnumerableIdentifier( inputs.get( 0 ).getCluster(), traitSet, entity, inputs.get( 0 ) );
    }


    @Override
    public Result implement( EnumerableAlgImplementor implementor, Prefer pref ) {
        final BlockBuilder builder = new BlockBuilder();
        final EnumerableAlg input = (EnumerableAlg) getInput();
        final Result result = implementor.visitChild( this, 0, input, Prefer.ANY );
        final PhysType physType = result.physType();

        Expression input_ = builder.append( "input", result.block() );
        Expression entityId_ = Expressions.constant( entity.getId() );
        Expression identification_ = null;
        switch ( input.getModel() ) {
            case RELATIONAL -> {
                Expression addRelIdentifiers_ = Expressions.call( EnumerableIdentifier.class, "addRelIdentifiers" );
                Expression addRelIdentifierWithId_ = Expressions.call( EnumerableIdentifier.class, "bindLogicalId", addRelIdentifiers_, entityId_ );
                identification_ = builder.append( "identification", Expressions.call( BuiltInMethod.PROCESS_AND_STREAM_RIGHT.method, input_, addRelIdentifierWithId_ ) );
            }
            case DOCUMENT -> {
                Expression addDocIdentifiers_ = Expressions.call( EnumerableIdentifier.class, "addDocIdentifiers" );
                Expression addDocIdentifierWithId_ = Expressions.call( EnumerableIdentifier.class, "bindLogicalId", addDocIdentifiers_, entityId_ );
                identification_ = builder.append( "identification", Expressions.call( BuiltInMethod.PROCESS_AND_STREAM_RIGHT.method, input_, addDocIdentifierWithId_ ) );
            }
            case GRAPH -> {
                Expression addLpgIdentifiers_ = Expressions.call( EnumerableIdentifier.class, "addLpgIdentifiers" );
                Expression addLpgIdentifiersWithId_ = Expressions.call( EnumerableIdentifier.class, "bindLogicalId", addLpgIdentifiers_, entityId_ );
                identification_ = builder.append( "identification", Expressions.call( BuiltInMethod.PROCESS_AND_STREAM_RIGHT.method, input_, addLpgIdentifiersWithId_ ) );
            }
        }
        assert identification_ != null;
        builder.add( Expressions.return_( null, identification_ ) );
        return implementor.result( physType, builder.toBlock() );

    }


    public static Function1<Enumerable<PolyValue[]>, Enumerable<PolyValue[]>> bindLogicalId(
            Function2<Enumerable<PolyValue[]>, Long, Enumerable<PolyValue[]>> function,
            long logicalId ) {
        return input -> function.apply( input, logicalId );
    }


    public static Function2<Enumerable<PolyValue[]>, Long, Enumerable<PolyValue[]>> addRelIdentifiers() {
        return ( input, logicalId ) -> {
            LogicalEntity entity = Catalog.getInstance().getSnapshot()
                    .getLogicalEntity( logicalId )
                    .orElseThrow();
            return input.select( row -> {
                row[0] = entity.getEntryIdentifiers()
                        .getNextEntryIdentifier()
                        .getEntryIdentifierAsPolyLong();
                row[1] = new PolyLong( IdentifierUtils.MISSING_IDENTIFIER );
                return row;
            } );
        };
    }


    public static Function2<Enumerable<PolyValue[]>, Long, Enumerable<PolyValue[]>> addDocIdentifiers() {
        return ( input, logicalId ) -> {
            LogicalEntity entity = Catalog.getInstance().getSnapshot()
                    .getLogicalEntity( logicalId )
                    .orElseThrow();
            return input.select( row -> {
                for ( PolyValue value : row ) {
                    PolyLong entryIdentifier = entity.getEntryIdentifiers()
                            .getNextEntryIdentifier()
                            .getEntryIdentifierAsPolyLong();
                    if ( value instanceof PolyDocument ) {
                        ((PolyDocument) value).put( IdentifierUtils.getIdentifierKeyAsPolyString(), entryIdentifier );
                        ((PolyDocument) value).put( IdentifierUtils.getVersionKeyAsPolyString(), new PolyLong( IdentifierUtils.MISSING_VERSION ) );
                    }
                }
                return row;
            } );
        };
    }


    public static Function2<Enumerable<PolyValue[]>, Long, Enumerable<PolyValue[]>> addLpgIdentifiers() {
        return ( input, logicalId ) -> {
            LogicalEntity entity = Catalog.getInstance().getSnapshot()
                    .getLogicalEntity( logicalId )
                    .orElseThrow();
            return input.select( row -> {
                for ( PolyValue value : row ) {
                    PolyLong entryIdentifier = entity.getEntryIdentifiers()
                            .getNextEntryIdentifier()
                            .getEntryIdentifierAsPolyLong();
                    if ( value instanceof GraphPropertyHolder ) {
                        ((GraphPropertyHolder) value).getProperties()
                                .put( IdentifierUtils.getIdentifierKeyAsPolyString(), entryIdentifier );
                        ((GraphPropertyHolder) value).getProperties()
                                .put( IdentifierUtils.getVersionKeyAsPolyString(), new PolyLong( IdentifierUtils.MISSING_VERSION ) );
                    }
                }
                return row;
            } );
        };
    }

}