/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.db.adapter.mongodb.rules;


import java.util.List;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonString;
import org.polypheny.db.adapter.mongodb.MongoAlg;
import org.polypheny.db.adapter.mongodb.MongoEntity;
import org.polypheny.db.algebra.AlgNode;
import org.polypheny.db.algebra.core.relational.RelScan;
import org.polypheny.db.algebra.metadata.AlgMetadataQuery;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgRecordType;
import org.polypheny.db.plan.AlgOptCluster;
import org.polypheny.db.plan.AlgOptCost;
import org.polypheny.db.plan.AlgOptPlanner;
import org.polypheny.db.plan.AlgOptRule;
import org.polypheny.db.plan.AlgTraitSet;
import org.polypheny.db.util.Pair;


/**
 * Relational expression representing a scan of a MongoDB collection.
 *
 * Additional operations might be applied, using the "find" or "aggregate" methods.</p>
 */
public class MongoScan extends RelScan<MongoEntity> implements MongoAlg {



    /**
     * Creates a MongoScan.
     *
     * @param cluster Cluster
     * @param traitSet Traits
     * @param table Table
     */
    public MongoScan( AlgOptCluster cluster, AlgTraitSet traitSet, MongoEntity table ) {
        super( cluster, traitSet, table );

        assert getConvention() == CONVENTION;
    }


    @Override
    public AlgNode copy( AlgTraitSet traitSet, List<AlgNode> inputs ) {
        assert inputs.isEmpty();
        return this;
    }


    @Override
    public AlgDataType deriveRowType() {
        return super.deriveRowType();
    }


    @Override
    public AlgOptCost computeSelfCost( AlgOptPlanner planner, AlgMetadataQuery mq ) {
        // scans with a small project list are cheaper
        final float f = getRowType().getFieldCount() / 100f;
        return super.computeSelfCost( planner, mq ).multiplyBy( .1 * f );
    }


    @Override
    public void register( AlgOptPlanner planner ) {
        for ( AlgOptRule rule : MongoRules.RULES ) {
            planner.addRule( rule );
        }
    }


    @Override
    public void implement( Implementor implementor ) {
        implementor.entity = entity;
        //implementor.setStaticRowType( (AlgRecordType) rowType );
        //implementor.physicalMapper.addAll( rowType.getFieldNames() );

        if ( implementor.isDML() ) {
            implementor.setStaticRowType( (AlgRecordType) rowType );
            return;
        }
        implementor.list.add( Pair.of( null, new BsonDocument( "$project", new BsonDocument( rowType.getFieldList().stream().map( p -> new BsonElement( p.getName(), new BsonString( "$" + p.getPhysicalName() ) ) ).collect( Collectors.toList() ) ) ).toJson() ) );
    }

}
