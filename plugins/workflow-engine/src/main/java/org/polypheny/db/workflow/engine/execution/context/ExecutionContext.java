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
 */

package org.polypheny.db.workflow.engine.execution.context;

import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.workflow.engine.storage.DocWriter;
import org.polypheny.db.workflow.engine.storage.LpgWriter;
import org.polypheny.db.workflow.engine.storage.RelWriter;

public interface ExecutionContext {

    boolean checkInterrupted() throws Exception;

    void updateProgress( double value );

    /**
     * Creates a {@link RelWriter} for the specified output index with the given tuple type.
     *
     * @param idx the output index.
     * @param tupleType the schema of the output.
     * @param resetPk whether to reset the primary key (=> first column) (allowed only for single integer-type keys).
     * @return a {@link RelWriter} for writing data to the output.
     */
    RelWriter createRelWriter( int idx, AlgDataType tupleType, boolean resetPk );

    DocWriter createDocWriter( int idx );

    LpgWriter createLpgWriter( int idx );

}
