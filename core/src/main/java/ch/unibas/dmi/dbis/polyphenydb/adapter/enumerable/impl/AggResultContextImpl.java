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

package ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable.impl;


import ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable.AggResultContext;
import ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable.PhysType;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.AggregateCall;
import java.util.List;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.ParameterExpression;


/**
 * Implementation of {@link ch.unibas.dmi.dbis.polyphenydb.adapter.enumerable.AggResultContext}
 */
public class AggResultContextImpl extends AggResetContextImpl implements AggResultContext {

    private final AggregateCall call;
    private final ParameterExpression key;
    private final PhysType keyPhysType;


    /**
     * Creates aggregate result context.
     *
     * @param block Code block that will contain the result calculation statements
     * @param call Aggregate call
     * @param accumulator Accumulator variables that store the intermediate aggregate state
     * @param key Key
     */
    public AggResultContextImpl( BlockBuilder block, AggregateCall call, List<Expression> accumulator, ParameterExpression key, PhysType keyPhysType ) {
        super( block, accumulator );
        this.call = call;
        this.key = key;
        this.keyPhysType = keyPhysType;
    }


    @Override
    public Expression key() {
        return key;
    }


    @Override
    public Expression keyField( int i ) {
        return keyPhysType.fieldReference( key, i );
    }


    @Override
    public AggregateCall call() {
        return call;
    }
}
