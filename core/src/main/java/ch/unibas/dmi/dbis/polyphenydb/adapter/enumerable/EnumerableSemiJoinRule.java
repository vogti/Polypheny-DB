/*
 * Copyright 2019-2020 The Polypheny Project
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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable;


import ch.unibas.dmi.dbis.polyphenydb.plan.Convention;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptRule;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.convert.ConverterRule;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.SemiJoin;
import java.util.ArrayList;
import java.util.List;


/**
 * Planner rule that converts a {@link SemiJoin} relational expression {@link EnumerableConvention enumerable calling convention}.
 */
class EnumerableSemiJoinRule extends ConverterRule {

    EnumerableSemiJoinRule() {
        super( SemiJoin.class, Convention.NONE, EnumerableConvention.INSTANCE, "EnumerableSemiJoinRule" );
    }


    @Override
    public RelNode convert( RelNode rel ) {
        final SemiJoin semiJoin = (SemiJoin) rel;
        final List<RelNode> newInputs = new ArrayList<>();
        for ( RelNode input : semiJoin.getInputs() ) {
            if ( !(input.getConvention() instanceof EnumerableConvention) ) {
                input = RelOptRule.convert( input, input.getTraitSet().replace( EnumerableConvention.INSTANCE ) );
            }
            newInputs.add( input );
        }
        return EnumerableSemiJoin.create( newInputs.get( 0 ), newInputs.get( 1 ), semiJoin.getCondition(), semiJoin.leftKeys, semiJoin.rightKeys );
    }
}

