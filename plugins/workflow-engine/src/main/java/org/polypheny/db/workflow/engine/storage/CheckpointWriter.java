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

package org.polypheny.db.workflow.engine.storage;

import java.util.Iterator;
import org.polypheny.db.catalog.entity.logical.LogicalEntity;
import org.polypheny.db.type.entity.PolyValue;

public abstract class CheckpointWriter {

    private final LogicalEntity entity;


    public CheckpointWriter( LogicalEntity entity ) {
        this.entity = entity;
    }


    /**
     * Writes the given tuple to this checkpoint.
     * The tuple type of the checkpoint must be compatible with this tuple.
     * The tuple should be discarded after calling this method, as its contents
     * can get modified during writing.
     *
     * @param tuple the tuple to write to this checkpoint
     */
    public abstract void write( PolyValue[] tuple );


    public void write( Iterator<PolyValue[]> iterator ) {
        while ( iterator.hasNext() ) {
            write( iterator.next() );
        }
    }

}
