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

package org.polypheny.db.algebra.mutable;


import java.util.List;
import org.polypheny.db.algebra.core.Minus;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.plan.AlgCluster;


/**
 * Mutable equivalent of {@link Minus}.
 */
public class MutableMinus extends MutableSetOp {

    private MutableMinus( AlgCluster cluster, AlgDataType rowType, List<MutableAlg> inputs, boolean all ) {
        super( cluster, rowType, MutableAlgType.MINUS, inputs, all );
    }


    /**
     * Creates a MutableMinus.
     *
     * @param rowType Row type
     * @param inputs Input relational expressions
     * @param all Whether to perform a multiset subtraction or a set subtraction
     */
    public static MutableMinus of( AlgDataType rowType, List<MutableAlg> inputs, boolean all ) {
        assert inputs.size() >= 2;
        final MutableAlg input0 = inputs.get( 0 );
        return new MutableMinus( input0.cluster, rowType, inputs, all );
    }


    @Override
    public StringBuilder digest( StringBuilder buf ) {
        return buf.append( "Minus(all: " ).append( all ).append( ")" );
    }


    @Override
    public MutableAlg clone() {
        return MutableMinus.of( rowType, cloneChildren(), all );
    }

}

