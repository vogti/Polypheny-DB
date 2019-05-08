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

package ch.unibas.dmi.dbis.polyphenydb.jdbc;


import static ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbEmbeddedMetaImpl.MetaColumn;
import static ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbEmbeddedMetaImpl.MetaTable;

import ch.unibas.dmi.dbis.polyphenydb.schema.Schema;
import ch.unibas.dmi.dbis.polyphenydb.schema.Table;
import ch.unibas.dmi.dbis.polyphenydb.schema.impl.AbstractSchema;
import com.google.common.collect.ImmutableMap;
import java.sql.SQLException;
import java.util.Map;
import org.apache.calcite.linq4j.Enumerator;


/**
 * Schema that contains metadata tables such as "TABLES" and "COLUMNS".
 */
class MetadataSchema extends AbstractSchema {

    private static final Map<String, Table> TABLE_MAP =
            ImmutableMap.of(
                    "COLUMNS",
                    new PolyphenyDbEmbeddedMetaImpl.MetadataTable<MetaColumn>( MetaColumn.class ) {
                        public Enumerator<MetaColumn> enumerator( final PolyphenyDbEmbeddedMetaImpl meta ) {
                            final String catalog;
                            try {
                                catalog = meta.getConnection().getCatalog();
                            } catch ( SQLException e ) {
                                throw new RuntimeException( e );
                            }
                            return meta.tables( catalog ).selectMany( meta::columns ).enumerator();
                        }
                    },
                    "TABLES",
                    new PolyphenyDbEmbeddedMetaImpl.MetadataTable<MetaTable>( MetaTable.class ) {
                        public Enumerator<MetaTable> enumerator( PolyphenyDbEmbeddedMetaImpl meta ) {
                            final String catalog;
                            try {
                                catalog = meta.getConnection().getCatalog();
                            } catch ( SQLException e ) {
                                throw new RuntimeException( e );
                            }
                            return meta.tables( catalog ).enumerator();
                        }
                    } );

    public static final Schema INSTANCE = new MetadataSchema();


    /**
     * Creates the data dictionary, also called the information schema. It is a schema called "metadata" that contains tables "TABLES", "COLUMNS" etc.
     */
    private MetadataSchema() {
    }


    @Override
    protected Map<String, Table> getTableMap() {
        return TABLE_MAP;
    }
}

