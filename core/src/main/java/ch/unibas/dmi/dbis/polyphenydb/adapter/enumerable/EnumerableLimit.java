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


import ch.unibas.dmi.dbis.polyphenydb.DataContext;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCluster;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTraitSet;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelCollationTraitDef;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelDistributionTraitDef;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelWriter;
import ch.unibas.dmi.dbis.polyphenydb.rel.SingleRel;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMdCollation;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMdDistribution;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMetadataQuery;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexDynamicParam;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexLiteral;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.util.BuiltInMethod;
import java.util.List;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;


/**
 * Relational expression that applies a limit and/or offset to its input.
 */
public class EnumerableLimit extends SingleRel implements EnumerableRel {

    public final RexNode offset;
    public final RexNode fetch;


    /**
     * Creates an EnumerableLimit.
     *
     * Use {@link #create} unless you know what you're doing.
     */
    public EnumerableLimit( RelOptCluster cluster, RelTraitSet traitSet, RelNode input, RexNode offset, RexNode fetch ) {
        super( cluster, traitSet, input );
        this.offset = offset;
        this.fetch = fetch;
        assert getConvention() instanceof EnumerableConvention;
        assert getConvention() == input.getConvention();
    }


    /**
     * Creates an EnumerableLimit.
     */
    public static EnumerableLimit create( final RelNode input, RexNode offset, RexNode fetch ) {
        final RelOptCluster cluster = input.getCluster();
        final RelMetadataQuery mq = cluster.getMetadataQuery();
        final RelTraitSet traitSet =
                cluster.traitSetOf( EnumerableConvention.INSTANCE )
                        .replaceIfs( RelCollationTraitDef.INSTANCE, () -> RelMdCollation.limit( mq, input ) )
                        .replaceIf( RelDistributionTraitDef.INSTANCE, () -> RelMdDistribution.limit( mq, input ) );
        return new EnumerableLimit( cluster, traitSet, input, offset, fetch );
    }


    @Override
    public EnumerableLimit copy( RelTraitSet traitSet, List<RelNode> newInputs ) {
        return new EnumerableLimit( getCluster(), traitSet, sole( newInputs ), offset, fetch );
    }


    @Override
    public RelWriter explainTerms( RelWriter pw ) {
        return super.explainTerms( pw )
                .itemIf( "offset", offset, offset != null )
                .itemIf( "fetch", fetch, fetch != null );
    }


    @Override
    public Result implement( EnumerableRelImplementor implementor, Prefer pref ) {
        final BlockBuilder builder = new BlockBuilder();
        final EnumerableRel child = (EnumerableRel) getInput();
        final Result result = implementor.visitChild( this, 0, child, pref );
        final PhysType physType = PhysTypeImpl.of( implementor.getTypeFactory(), getRowType(), result.format );

        Expression v = builder.append( "child", result.block );
        if ( offset != null ) {
            v = builder.append(
                    "offset",
                    Expressions.call( v, BuiltInMethod.SKIP.method, getExpression( offset ) ) );
        }
        if ( fetch != null ) {
            v = builder.append(
                    "fetch",
                    Expressions.call( v, BuiltInMethod.TAKE.method, getExpression( fetch ) ) );
        }

        builder.add( Expressions.return_( null, v ) );
        return implementor.result( physType, builder.toBlock() );
    }


    private static Expression getExpression( RexNode offset ) {
        if ( offset instanceof RexDynamicParam ) {
            final RexDynamicParam param = (RexDynamicParam) offset;
            return Expressions.convert_(
                    Expressions.call( DataContext.ROOT, BuiltInMethod.DATA_CONTEXT_GET.method, Expressions.constant( "?" + param.getIndex() ) ),
                    Integer.class );
        } else {
            return Expressions.constant( RexLiteral.intValue( offset ) );
        }
    }
}

