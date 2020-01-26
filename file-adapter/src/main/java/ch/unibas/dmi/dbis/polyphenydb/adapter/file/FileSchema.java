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

package ch.unibas.dmi.dbis.polyphenydb.adapter.file;


import ch.unibas.dmi.dbis.polyphenydb.adapter.csv.CsvFilterableTable;
import ch.unibas.dmi.dbis.polyphenydb.adapter.csv.JsonTable;
import ch.unibas.dmi.dbis.polyphenydb.schema.SchemaPlus;
import ch.unibas.dmi.dbis.polyphenydb.schema.Table;
import ch.unibas.dmi.dbis.polyphenydb.schema.impl.AbstractSchema;
import ch.unibas.dmi.dbis.polyphenydb.util.Source;
import ch.unibas.dmi.dbis.polyphenydb.util.Sources;
import ch.unibas.dmi.dbis.polyphenydb.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;


/**
 * Schema mapped onto a set of URLs / HTML tables. Each table in the schema is an HTML table on a URL.
 */
class FileSchema extends AbstractSchema {

    private final ImmutableList<Map<String, Object>> tables;
    private final File baseDirectory;


    /**
     * Creates an HTML tables schema.
     *
     * @param parentSchema Parent schema
     * @param name Schema name
     * @param baseDirectory Base directory to look for relative files, or null
     * @param tables List containing HTML table identifiers
     */
    FileSchema( SchemaPlus parentSchema, String name, File baseDirectory, List<Map<String, Object>> tables ) {
        this.tables = ImmutableList.copyOf( tables );
        this.baseDirectory = baseDirectory;
    }


    /**
     * Looks for a suffix on a string and returns either the string with the suffix removed or the original string.
     */
    private static String trim( String s, String suffix ) {
        String trimmed = trimOrNull( s, suffix );
        return trimmed != null ? trimmed : s;
    }


    /**
     * Looks for a suffix on a string and returns either the string with the suffix removed or null.
     */
    private static String trimOrNull( String s, String suffix ) {
        return s.endsWith( suffix )
                ? s.substring( 0, s.length() - suffix.length() )
                : null;
    }


    @Override
    protected Map<String, Table> getTableMap() {
        final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder();

        for ( Map<String, Object> tableDef : this.tables ) {
            String tableName = (String) tableDef.get( "name" );
            try {
                addTable( builder, tableDef );
            } catch ( MalformedURLException e ) {
                throw new RuntimeException( "Unable to instantiate table for: " + tableName );
            }
        }

        // Look for files in the directory ending in ".csv", ".csv.gz", ".json", ".json.gz".
        final Source baseSource = Sources.of( baseDirectory );
        File[] files = baseDirectory.listFiles( ( dir, name ) -> {
            final String nameSansGz = trim( name, ".gz" );
            return nameSansGz.endsWith( ".csv" ) || nameSansGz.endsWith( ".json" );
        } );
        if ( files == null ) {
            System.out.println( "directory " + baseDirectory + " not found" );
            files = new File[0];
        }
        // Build a map from table name to table; each file becomes a table.
        for ( File file : files ) {
            Source source = Sources.of( file );
            Source sourceSansGz = source.trim( ".gz" );
            final Source sourceSansJson = sourceSansGz.trimOrNull( ".json" );
            if ( sourceSansJson != null ) {
                JsonTable table = new JsonTable( source );
                builder.put( sourceSansJson.relative( baseSource ).path(), table );
                continue;
            }
            final Source sourceSansCsv = sourceSansGz.trimOrNull( ".csv" );
            if ( sourceSansCsv != null ) {
                addTable( builder, source, sourceSansCsv.relative( baseSource ).path(), null );
            }
        }

        return builder.build();
    }


    private boolean addTable( ImmutableMap.Builder<String, Table> builder, Map<String, Object> tableDef ) throws MalformedURLException {
        final String tableName = (String) tableDef.get( "name" );
        final String url = (String) tableDef.get( "url" );
        final Source source0 = Sources.url( url );
        final Source source;
        if ( baseDirectory == null ) {
            source = source0;
        } else {
            source = Sources.of( baseDirectory ).append( source0 );
        }
        return addTable( builder, source, tableName, tableDef );
    }


    private boolean addTable( ImmutableMap.Builder<String, Table> builder, Source source, String tableName, Map<String, Object> tableDef ) {
        final Source sourceSansGz = source.trim( ".gz" );
        final Source sourceSansJson = sourceSansGz.trimOrNull( ".json" );
        if ( sourceSansJson != null ) {
            JsonTable table = new JsonTable( source );
            builder.put( Util.first( tableName, sourceSansJson.path() ), table );
            return true;
        }
        final Source sourceSansCsv = sourceSansGz.trimOrNull( ".csv" );
        if ( sourceSansCsv != null ) {
            //
            // TODO: MV: This three nulls most properly introduce trouble. Fix to have the correct row details at this point.
            //
            final Table table = new CsvFilterableTable( source, null, null, null );
            builder.put( Util.first( tableName, sourceSansCsv.path() ), table );
            return true;
        }

        if ( tableDef != null ) {
            try {
                FileTable table = FileTable.create( source, tableDef );
                builder.put( Util.first( tableName, source.path() ), table );
                return true;
            } catch ( Exception e ) {
                throw new RuntimeException( "Unable to instantiate table for: " + tableName );
            }
        }

        return false;
    }
}
