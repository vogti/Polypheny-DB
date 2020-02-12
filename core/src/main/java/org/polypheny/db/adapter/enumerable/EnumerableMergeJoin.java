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

package org.polypheny.db.adapter.enumerable;


import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.plan.RelOptCost;
import org.polypheny.db.plan.RelOptPlanner;
import org.polypheny.db.plan.RelTraitSet;
import org.polypheny.db.rel.InvalidRelException;
import org.polypheny.db.rel.RelCollation;
import org.polypheny.db.rel.RelCollationTraitDef;
import org.polypheny.db.rel.RelCollations;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.core.CorrelationId;
import org.polypheny.db.rel.core.EquiJoin;
import org.polypheny.db.rel.core.Join;
import org.polypheny.db.rel.core.JoinInfo;
import org.polypheny.db.rel.core.JoinRelType;
import org.polypheny.db.rel.metadata.RelMdCollation;
import org.polypheny.db.rel.metadata.RelMetadataQuery;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.util.BuiltInMethod;
import org.polypheny.db.util.ImmutableIntList;
import org.polypheny.db.util.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.ParameterExpression;
import org.apache.calcite.linq4j.tree.Types;


/**
 * Implementation of {@link Join} in {@link EnumerableConvention enumerable calling convention} using a merge algorithm.
 */
public class EnumerableMergeJoin extends EquiJoin implements EnumerableRel {

    EnumerableMergeJoin( RelOptCluster cluster, RelTraitSet traits, RelNode left, RelNode right, RexNode condition, ImmutableIntList leftKeys, ImmutableIntList rightKeys, Set<CorrelationId> variablesSet, JoinRelType joinType ) throws InvalidRelException {
        super( cluster, traits, left, right, condition, leftKeys, rightKeys, variablesSet, joinType );
        final List<RelCollation> collations = traits.getTraits( RelCollationTraitDef.INSTANCE );
        assert collations == null || RelCollations.contains( collations, leftKeys );
    }


    public static EnumerableMergeJoin create( RelNode left, RelNode right, RexLiteral condition, ImmutableIntList leftKeys, ImmutableIntList rightKeys, JoinRelType joinType ) throws InvalidRelException {
        final RelOptCluster cluster = right.getCluster();
        RelTraitSet traitSet = cluster.traitSet();
        if ( traitSet.isEnabled( RelCollationTraitDef.INSTANCE ) ) {
            final RelMetadataQuery mq = cluster.getMetadataQuery();
            final List<RelCollation> collations = RelMdCollation.mergeJoin( mq, left, right, leftKeys, rightKeys );
            traitSet = traitSet.replace( collations );
        }
        return new EnumerableMergeJoin( cluster, traitSet, left, right, condition, leftKeys, rightKeys, ImmutableSet.of(), joinType );
    }


    @Override
    public EnumerableMergeJoin copy( RelTraitSet traitSet, RexNode condition, RelNode left, RelNode right, JoinRelType joinType, boolean semiJoinDone ) {
        final JoinInfo joinInfo = JoinInfo.of( left, right, condition );
        assert joinInfo.isEqui();
        try {
            return new EnumerableMergeJoin( getCluster(), traitSet, left, right, condition, joinInfo.leftKeys, joinInfo.rightKeys, variablesSet, joinType );
        } catch ( InvalidRelException e ) {
            // Semantic error not possible. Must be a bug. Convert to internal error.
            throw new AssertionError( e );
        }
    }


    @Override
    public RelOptCost computeSelfCost( RelOptPlanner planner, RelMetadataQuery mq ) {
        // We assume that the inputs are sorted. The price of sorting them has already been paid. The cost of the join is therefore proportional to the input and output size.
        final double rightRowCount = right.estimateRowCount( mq );
        final double leftRowCount = left.estimateRowCount( mq );
        final double rowCount = mq.getRowCount( this );
        final double d = leftRowCount + rightRowCount + rowCount;
        return planner.getCostFactory().makeCost( d, 0, 0 );
    }


    @Override
    public Result implement( EnumerableRelImplementor implementor, Prefer pref ) {
        BlockBuilder builder = new BlockBuilder();
        final Result leftResult = implementor.visitChild( this, 0, (EnumerableRel) left, pref );
        final Expression leftExpression = builder.append( "left", leftResult.block );
        final ParameterExpression left_ = Expressions.parameter( leftResult.physType.getJavaRowType(), "left" );
        final Result rightResult = implementor.visitChild( this, 1, (EnumerableRel) right, pref );
        final Expression rightExpression = builder.append( "right", rightResult.block );
        final ParameterExpression right_ = Expressions.parameter( rightResult.physType.getJavaRowType(), "right" );
        final JavaTypeFactory typeFactory = implementor.getTypeFactory();
        final PhysType physType = PhysTypeImpl.of( typeFactory, getRowType(), pref.preferArray() );
        final List<Expression> leftExpressions = new ArrayList<>();
        final List<Expression> rightExpressions = new ArrayList<>();
        for ( Pair<Integer, Integer> pair : Pair.zip( leftKeys, rightKeys ) ) {
            final RelDataType keyType = typeFactory.leastRestrictive( ImmutableList.of( left.getRowType().getFieldList().get( pair.left ).getType(), right.getRowType().getFieldList().get( pair.right ).getType() ) );
            final Type keyClass = typeFactory.getJavaClass( keyType );
            leftExpressions.add( Types.castIfNecessary( keyClass, leftResult.physType.fieldReference( left_, pair.left ) ) );
            rightExpressions.add( Types.castIfNecessary( keyClass, rightResult.physType.fieldReference( right_, pair.right ) ) );
        }
        final PhysType leftKeyPhysType = leftResult.physType.project( leftKeys, JavaRowFormat.LIST );
        final PhysType rightKeyPhysType = rightResult.physType.project( rightKeys, JavaRowFormat.LIST );
        return implementor.result(
                physType,
                builder.append(
                        Expressions.call(
                                BuiltInMethod.MERGE_JOIN.method,
                                Expressions.list(
                                        leftExpression,
                                        rightExpression,
                                        Expressions.lambda( leftKeyPhysType.record( leftExpressions ), left_ ),
                                        Expressions.lambda( rightKeyPhysType.record( rightExpressions ), right_ ),
                                        EnumUtils.joinSelector( joinType, physType, ImmutableList.of( leftResult.physType, rightResult.physType ) ),
                                        Expressions.constant( joinType.generatesNullsOnLeft() ),
                                        Expressions.constant( joinType.generatesNullsOnRight() ) ) ) ).toBlock() );
    }
}

