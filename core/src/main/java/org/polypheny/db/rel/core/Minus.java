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

package org.polypheny.db.rel.core;


import java.util.List;
import java.util.stream.Collectors;
import org.polypheny.db.core.enums.Kind;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.plan.RelTraitSet;
import org.polypheny.db.rel.RelInput;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.metadata.RelMdUtil;
import org.polypheny.db.rel.metadata.RelMetadataQuery;


/**
 * Relational expression that returns the rows of its first input minus any matching rows from its other inputs.
 *
 * Corresponds to the SQL {@code EXCEPT} operator.
 *
 * If "all" is true, then multiset subtraction is performed; otherwise, set subtraction is performed (implying no duplicates in the results).
 */
public abstract class Minus extends SetOp {

    public Minus( RelOptCluster cluster, RelTraitSet traits, List<RelNode> inputs, boolean all ) {
        super( cluster, traits, inputs, Kind.EXCEPT, all );
    }


    /**
     * Creates a Minus by parsing serialized output.
     */
    protected Minus( RelInput input ) {
        super( input );
    }


    @Override
    public double estimateRowCount( RelMetadataQuery mq ) {
        return RelMdUtil.getMinusRowCount( mq, this );
    }


    @Override
    public String relCompareString() {
        return this.getClass().getSimpleName() + "$" +
                inputs.stream().map( RelNode::relCompareString ).collect( Collectors.joining( "$" ) ) + "$" +
                all + "&";
    }

}
