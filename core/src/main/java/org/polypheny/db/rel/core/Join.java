/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.db.rel.core;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.polypheny.db.core.util.ValidatorUtil;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.plan.RelOptCost;
import org.polypheny.db.plan.RelOptPlanner;
import org.polypheny.db.plan.RelTraitSet;
import org.polypheny.db.rel.BiRel;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.RelWriter;
import org.polypheny.db.rel.metadata.RelMdUtil;
import org.polypheny.db.rel.metadata.RelMetadataQuery;
import org.polypheny.db.rel.rules.JoinAddRedundantSemiJoinRule;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeField;
import org.polypheny.db.rex.RexChecker;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.rex.RexShuttle;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Litmus;
import org.polypheny.db.util.Util;


/**
 * Relational expression that combines two relational expressions according to some condition.
 *
 * Each output row has columns from the left and right inputs. The set of output rows is a subset of the cartesian product
 * of the two inputs; precisely which subset depends on the join condition.
 */
public abstract class Join extends BiRel {

    protected final RexNode condition;
    protected final ImmutableSet<CorrelationId> variablesSet;

    /**
     * Values must be of enumeration {@link JoinRelType}, except that {@link JoinRelType#RIGHT} is disallowed.
     */
    protected final JoinRelType joinType;

    // Next time we need to change the constructor of Join, let's change the "Set<String> variablesStopped" parameter to "Set<CorrelationId> variablesSet".
    // At that point we would deprecate RelNode.getVariablesStopped().


    /**
     * Creates a Join.
     *
     * Note: We plan to change the {@code variablesStopped} parameter to {@code Set&lt;CorrelationId&gt; variablesSet}
     * because {@link #getVariablesSet()} is preferred over {@link #getVariablesStopped()}. This constructor is not
     * deprecated, for now, because maintaining overloaded constructors in multiple sub-classes would be onerous.
     *
     * @param cluster Cluster
     * @param traitSet Trait set
     * @param left Left input
     * @param right Right input
     * @param condition Join condition
     * @param joinType Join type
     * @param variablesSet Set variables that are set by the LHS and used by the RHS and are not available to nodes above this Join in the tree
     */
    protected Join( RelOptCluster cluster, RelTraitSet traitSet, RelNode left, RelNode right, RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType ) {
        super( cluster, traitSet, left, right );
        this.condition = Objects.requireNonNull( condition );
        this.variablesSet = ImmutableSet.copyOf( variablesSet );
        this.joinType = Objects.requireNonNull( joinType );
    }


    @Override
    public List<RexNode> getChildExps() {
        return ImmutableList.of( condition );
    }


    @Override
    public RelNode accept( RexShuttle shuttle ) {
        RexNode condition = shuttle.apply( this.condition );
        if ( this.condition == condition ) {
            return this;
        }
        return copy( traitSet, condition, left, right, joinType, isSemiJoinDone() );
    }


    public RexNode getCondition() {
        return condition;
    }


    public JoinRelType getJoinType() {
        return joinType;
    }


    @Override
    public boolean isValid( Litmus litmus, Context context ) {
        if ( !super.isValid( litmus, context ) ) {
            return false;
        }
        if ( getRowType().getFieldCount() != getSystemFieldList().size() + left.getRowType().getFieldCount() + (this instanceof SemiJoin ? 0 : right.getRowType().getFieldCount()) ) {
            return litmus.fail( "field count mismatch" );
        }
        if ( condition != null ) {
            if ( condition.getType().getPolyType() != PolyType.BOOLEAN ) {
                return litmus.fail( "condition must be boolean: {}", condition.getType() );
            }
            // The input to the condition is a row type consisting of system fields, left fields, and right fields. Very similar to the output row type, except that fields
            // have not yet been made due due to outer joins.
            RexChecker checker =
                    new RexChecker(
                            getCluster().getTypeFactory().builder()
                                    .addAll( getSystemFieldList() )
                                    .addAll( getLeft().getRowType().getFieldList() )
                                    .addAll( getRight().getRowType().getFieldList() )
                                    .build(),
                            context, litmus );
            condition.accept( checker );
            if ( checker.getFailureCount() > 0 ) {
                return litmus.fail( checker.getFailureCount() + " failures in condition " + condition );
            }
        }
        return litmus.succeed();
    }


    @Override
    public RelOptCost computeSelfCost( RelOptPlanner planner, RelMetadataQuery mq ) {
        // REVIEW jvs: Just for now...
        double rowCount = mq.getRowCount( this );
        return planner.getCostFactory().makeCost( rowCount, 0, 0 );
    }


    @Override
    public double estimateRowCount( RelMetadataQuery mq ) {
        return Util.first( RelMdUtil.getJoinRowCount( mq, this, condition ), 1D );
    }


    @Override
    public ImmutableSet<CorrelationId> getVariablesSet() {
        return variablesSet;
    }


    @Override
    public RelWriter explainTerms( RelWriter pw ) {
        return super.explainTerms( pw )
                .item( "condition", condition )
                .item( "joinType", joinType.lowerName )
                .itemIf( "systemFields", getSystemFieldList(), !getSystemFieldList().isEmpty() );
    }


    @Override
    protected RelDataType deriveRowType() {
        return ValidatorUtil.deriveJoinRowType( left.getRowType(), right.getRowType(), joinType, getCluster().getTypeFactory(), null, getSystemFieldList() );
    }


    /**
     * Returns whether this LogicalJoin has already spawned a {@link SemiJoin} via {@link JoinAddRedundantSemiJoinRule}.
     *
     * The base implementation returns false.
     *
     * @return whether this join has already spawned a semi join
     */
    public boolean isSemiJoinDone() {
        return false;
    }


    /**
     * Returns a list of system fields that will be prefixed to output row type.
     *
     * @return list of system fields
     */
    public List<RelDataTypeField> getSystemFieldList() {
        return Collections.emptyList();
    }


    @Override
    public final Join copy( RelTraitSet traitSet, List<RelNode> inputs ) {
        assert inputs.size() == 2;
        return copy( traitSet, getCondition(), inputs.get( 0 ), inputs.get( 1 ), joinType, isSemiJoinDone() );
    }


    /**
     * Creates a copy of this join, overriding condition, system fields and inputs.
     *
     * General contract as {@link RelNode#copy}.
     *
     * @param traitSet Traits
     * @param conditionExpr Condition
     * @param left Left input
     * @param right Right input
     * @param joinType Join type
     * @param semiJoinDone Whether this join has been translated to a semi-join
     * @return Copy of this join
     */
    public abstract Join copy( RelTraitSet traitSet, RexNode conditionExpr, RelNode left, RelNode right, JoinRelType joinType, boolean semiJoinDone );


    /**
     * Analyzes the join condition.
     *
     * @return Analyzed join condition
     */
    public JoinInfo analyzeCondition() {
        return JoinInfo.of( left, right, condition );
    }


    @Override
    public String relCompareString() {
        return this.getClass().getSimpleName() + "$" +
                left.relCompareString() + "$" +
                right.relCompareString() + "$" +
                (condition != null ? condition.hashCode() : "") + "$" +
                (joinType != null ? joinType.name() : "") + "&";
    }

}

