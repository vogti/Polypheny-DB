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

package ch.unibas.dmi.dbis.polyphenydb.rel.stream;


import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptCluster;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTraitSet;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelInput;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.SingleRel;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.TableScan;


/**
 * Relational operator that converts a relation to a stream.
 *
 * For example, if {@code Orders} is a table, and {@link TableScan}(Orders) is a relational operator that returns the current contents of the table, then {@link Delta}(TableScan(Orders)) is a relational operator that returns
 * all inserts into the table.
 *
 * If unrestricted, Delta returns all previous inserts into the table (from time -&infin; to now) and all future inserts into the table (from now to +&infin;) and never terminates.
 */
public abstract class Delta extends SingleRel {

    protected Delta( RelOptCluster cluster, RelTraitSet traits, RelNode input ) {
        super( cluster, traits, input );
    }


    /**
     * Creates a Delta by parsing serialized output.
     */
    protected Delta( RelInput input ) {
        this( input.getCluster(), input.getTraitSet(), input.getInput() );
    }
}

