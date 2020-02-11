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

package ch.unibas.dmi.dbis.polyphenydb.schema;


/**
 * An interface to represent a version ID that can be used to create a read-consistent view of a Schema. This interface assumes a strict partial ordering contract that is:
 * <ol>
 * <li>irreflexive: !a.isBefore(a), which means a cannot happen before itself;</li>
 * <li>transitive: if a.isBefore(b) and b.isBefore(c) then a.isBefore(c);</li>
 * <li>antisymmetric: if a.isBefore(b) then !b.isBefore(a).</li>
 * </ol>
 * Implementation classes of this interface must also override equals(Object), hashCode() and toString().
 *
 * @see Schema#snapshot(SchemaVersion)
 */
public interface SchemaVersion {

    /**
     * Returns if this Version happens before the other Version.
     *
     * @param other the other Version object
     */
    boolean isBefore( SchemaVersion other );
}

