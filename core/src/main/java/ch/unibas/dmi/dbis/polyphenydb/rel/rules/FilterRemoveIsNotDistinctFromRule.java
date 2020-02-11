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

package ch.unibas.dmi.dbis.polyphenydb.rel.rules;


import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptRule;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptRuleCall;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptUtil;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Filter;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.RelFactories;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexBuilder;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexCall;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexShuttle;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.fun.SqlStdOperatorTable;
import ch.unibas.dmi.dbis.polyphenydb.tools.RelBuilder;
import ch.unibas.dmi.dbis.polyphenydb.tools.RelBuilderFactory;


/**
 * Planner rule that replaces {@code IS NOT DISTINCT FROM} in a {@link Filter} with logically equivalent operations.
 *
 * @see SqlStdOperatorTable#IS_NOT_DISTINCT_FROM
 */
public final class FilterRemoveIsNotDistinctFromRule extends RelOptRule {

    /**
     * The singleton.
     */
    public static final FilterRemoveIsNotDistinctFromRule INSTANCE = new FilterRemoveIsNotDistinctFromRule( RelFactories.LOGICAL_BUILDER );


    /**
     * Creates a FilterRemoveIsNotDistinctFromRule.
     *
     * @param relBuilderFactory Builder for relational expressions
     */
    public FilterRemoveIsNotDistinctFromRule( RelBuilderFactory relBuilderFactory ) {
        super( operand( Filter.class, any() ), relBuilderFactory, null );
    }


    @Override
    public void onMatch( RelOptRuleCall call ) {
        Filter oldFilter = call.rel( 0 );
        RexNode oldFilterCond = oldFilter.getCondition();

        if ( RexUtil.findOperatorCall( SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, oldFilterCond ) == null ) {
            // no longer contains isNotDistinctFromOperator
            return;
        }

        // Now replace all the "a isNotDistinctFrom b" with the RexNode given by RelOptUtil.isDistinctFrom() method

        RemoveIsNotDistinctFromRexShuttle rewriteShuttle = new RemoveIsNotDistinctFromRexShuttle( oldFilter.getCluster().getRexBuilder() );

        final RelBuilder relBuilder = call.builder();
        final RelNode newFilterRel = relBuilder
                .push( oldFilter.getInput() )
                .filter( oldFilterCond.accept( rewriteShuttle ) )
                .build();

        call.transformTo( newFilterRel );
    }


    /**
     * Shuttle that removes 'x IS NOT DISTINCT FROM y' and converts it to
     * 'CASE WHEN x IS NULL THEN y IS NULL WHEN y IS NULL THEN x IS NULL ELSE x = y END'.
     */
    private class RemoveIsNotDistinctFromRexShuttle extends RexShuttle {

        RexBuilder rexBuilder;


        RemoveIsNotDistinctFromRexShuttle( RexBuilder rexBuilder ) {
            this.rexBuilder = rexBuilder;
        }


        // override RexShuttle
        @Override
        public RexNode visitCall( RexCall call ) {
            RexNode newCall = super.visitCall( call );

            if ( call.getOperator() == SqlStdOperatorTable.IS_NOT_DISTINCT_FROM ) {
                RexCall tmpCall = (RexCall) newCall;
                newCall = RelOptUtil.isDistinctFrom(
                        rexBuilder,
                        tmpCall.operands.get( 0 ),
                        tmpCall.operands.get( 1 ),
                        true );
            }
            return newCall;
        }
    }
}

