/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
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
 *
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable;


import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCluster;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCost;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptPlanner;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTraitSet;
import ch.unibas.dmi.dbis.polyphenydb.rel.InvalidRelException;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelCollationTraitDef;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.CorrelationId;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Join;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.JoinRelType;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMdCollation;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMetadataQuery;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexProgramBuilder;
import ch.unibas.dmi.dbis.polyphenydb.util.BuiltInMethod;
import ch.unibas.dmi.dbis.polyphenydb.util.Pair;
import com.google.common.collect.ImmutableList;
import java.util.Set;
import org.apache.calcite.linq4j.function.Predicate2;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.ParameterExpression;


/**
 * Implementation of {@link Join} in {@link EnumerableConvention enumerable calling convention} that allows conditions that are not just {@code =} (equals).
 */
public class EnumerableThetaJoin extends Join implements EnumerableRel {

    /**
     * Creates an EnumerableThetaJoin.
     */
    protected EnumerableThetaJoin( RelOptCluster cluster, RelTraitSet traits, RelNode left, RelNode right, RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType ) throws InvalidRelException {
        super( cluster, traits, left, right, condition, variablesSet, joinType );
    }


    @Deprecated // to be removed before 2.0
    protected EnumerableThetaJoin( RelOptCluster cluster, RelTraitSet traits, RelNode left, RelNode right, RexNode condition, JoinRelType joinType, Set<String> variablesStopped ) throws InvalidRelException {
        this( cluster, traits, left, right, condition, CorrelationId.setOf( variablesStopped ), joinType );
    }


    @Override
    public EnumerableThetaJoin copy( RelTraitSet traitSet, RexNode condition, RelNode left, RelNode right, JoinRelType joinType, boolean semiJoinDone ) {
        try {
            return new EnumerableThetaJoin( getCluster(), traitSet, left, right, condition, variablesSet, joinType );
        } catch ( InvalidRelException e ) {
            // Semantic error not possible. Must be a bug. Convert to internal error.
            throw new AssertionError( e );
        }
    }


    /**
     * Creates an EnumerableThetaJoin.
     */
    public static EnumerableThetaJoin create( RelNode left, RelNode right, RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType ) throws InvalidRelException {
        final RelOptCluster cluster = left.getCluster();
        final RelMetadataQuery mq = cluster.getMetadataQuery();
        final RelTraitSet traitSet =
                cluster.traitSetOf( EnumerableConvention.INSTANCE )
                        .replaceIfs( RelCollationTraitDef.INSTANCE, () -> RelMdCollation.enumerableThetaJoin( mq, left, right, joinType ) );
        return new EnumerableThetaJoin( cluster, traitSet, left, right, condition, variablesSet, joinType );
    }


    @Override
    public RelOptCost computeSelfCost( RelOptPlanner planner, RelMetadataQuery mq ) {
        double rowCount = mq.getRowCount( this );

        // Joins can be flipped, and for many algorithms, both versions are viable and have the same cost. To make the results stable between versions of the planner, make one of the versions slightly more expensive.
        switch ( joinType ) {
            case RIGHT:
                rowCount = addEpsilon( rowCount );
                break;
            default:
                if ( left.getId() > right.getId() ) {
                    rowCount = addEpsilon( rowCount );
                }
        }

        final double rightRowCount = right.estimateRowCount( mq );
        final double leftRowCount = left.estimateRowCount( mq );
        if ( Double.isInfinite( leftRowCount ) ) {
            rowCount = leftRowCount;
        }
        if ( Double.isInfinite( rightRowCount ) ) {
            rowCount = rightRowCount;
        }
        return planner.getCostFactory().makeCost( rowCount, 0, 0 );
    }


    private double addEpsilon( double d ) {
        assert d >= 0d;
        final double d0 = d;
        if ( d < 10 ) {
            // For small d, adding 1 would change the value significantly.
            d *= 1.001d;
            if ( d != d0 ) {
                return d;
            }
        }
        // For medium d, add 1. Keeps integral values integral.
        ++d;
        if ( d != d0 ) {
            return d;
        }
        // For large d, adding 1 might not change the value. Add .1%.
        // If d is NaN, this still will probably not change the value. That's OK.
        d *= 1.001d;
        return d;
    }


    @Override
    public Result implement( EnumerableRelImplementor implementor, Prefer pref ) {
        final BlockBuilder builder = new BlockBuilder();
        final Result leftResult = implementor.visitChild( this, 0, (EnumerableRel) left, pref );
        Expression leftExpression = builder.append( "left", leftResult.block );
        final Result rightResult = implementor.visitChild( this, 1, (EnumerableRel) right, pref );
        Expression rightExpression = builder.append( "right", rightResult.block );
        final PhysType physType = PhysTypeImpl.of( implementor.getTypeFactory(), getRowType(), pref.preferArray() );
        final BlockBuilder builder2 = new BlockBuilder();
        return implementor.result(
                physType,
                builder.append(
                        Expressions.call(
                                BuiltInMethod.THETA_JOIN.method,
                                leftExpression,
                                rightExpression,
                                predicate( implementor, builder2, leftResult.physType, rightResult.physType, condition ),
                                EnumUtils.joinSelector( joinType, physType, ImmutableList.of( leftResult.physType, rightResult.physType ) ),
                                Expressions.constant( joinType.generatesNullsOnLeft() ),
                                Expressions.constant( joinType.generatesNullsOnRight() ) )
                ).toBlock() );
    }


    Expression predicate( EnumerableRelImplementor implementor, BlockBuilder builder, PhysType leftPhysType, PhysType rightPhysType, RexNode condition ) {
        final ParameterExpression left_ = Expressions.parameter( leftPhysType.getJavaRowType(), "left" );
        final ParameterExpression right_ = Expressions.parameter( rightPhysType.getJavaRowType(), "right" );
        final RexProgramBuilder program =
                new RexProgramBuilder(
                        implementor.getTypeFactory()
                                .builder()
                                .addAll( left.getRowType().getFieldList() )
                                .addAll( right.getRowType().getFieldList() )
                                .build(),
                        getCluster().getRexBuilder() );
        program.addCondition( condition );
        builder.add(
                Expressions.return_( null,
                        RexToLixTranslator.translateCondition( program.getProgram(),
                                implementor.getTypeFactory(),
                                builder,
                                new RexToLixTranslator.InputGetterImpl( ImmutableList.of( Pair.of( (Expression) left_, leftPhysType ), Pair.of( (Expression) right_, rightPhysType ) ) ),
                                implementor.allCorrelateVariables,
                                implementor.getConformance() ) ) );
        return Expressions.lambda( Predicate2.class, builder.toBlock(), left_, right_ );
    }
}

