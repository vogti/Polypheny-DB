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

package ch.unibas.dmi.dbis.polyphenydb.test;


import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/**
 * Internal util methods for ElasticSearch tests
 */
public class ElasticsearchChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable( SerializationFeature.INDENT_OUTPUT )
            .enable( JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES ) // user-friendly settings to
            .enable( JsonParser.Feature.ALLOW_SINGLE_QUOTES ); // avoid too much quoting


    private ElasticsearchChecker() {
    }


    /**
     * Returns a function that checks that a particular Elasticsearch pipeline is generated to implement a query.
     *
     * @param strings expected expressions
     * @return validation function
     */
    public static Consumer<List> elasticsearchChecker( final String... strings ) {
        Objects.requireNonNull( strings, "strings" );
        return a -> {
            ObjectNode actual = a == null || a.isEmpty() ? null : ((ObjectNode) a.get( 0 ));

            actual = expandDots( actual );
            try {

                String json = "{" + Arrays.stream( strings ).collect( Collectors.joining( "," ) ) + "}";
                ObjectNode expected = (ObjectNode) MAPPER.readTree( json );
                expected = expandDots( expected );

                if ( !expected.equals( actual ) ) {
                    assertEquals( "expected and actual Elasticsearch queries do not match", MAPPER.writeValueAsString( expected ), MAPPER.writeValueAsString( actual ) );
                }
            } catch ( IOException e ) {
                throw new UncheckedIOException( e );
            }
        };
    }


    /**
     * Expands attributes with dots ({@code .}) into sub-nodes. Use for more friendly JSON format:
     *
     * <pre>
     *   {'a.b.c': 1}
     *   expanded to
     *   {a: { b: {c: 1}}}}
     * </pre>
     *
     * @param parent current node
     * @param <T> type of node (usually JsonNode).
     * @return copy of existing node with field {@code a.b.c} expanded.
     */
    @SuppressWarnings("unchecked")
    private static <T extends JsonNode> T expandDots( T parent ) {
        Objects.requireNonNull( parent, "parent" );

        if ( parent.isValueNode() ) {
            return parent.deepCopy();
        }

        // ArrayNode
        if ( parent.isArray() ) {
            ArrayNode arr = (ArrayNode) parent;
            ArrayNode copy = arr.arrayNode();
            arr.elements().forEachRemaining( e -> copy.add( expandDots( e ) ) );
            return (T) copy;
        }

        // ObjectNode
        ObjectNode objectNode = (ObjectNode) parent;
        final ObjectNode copy = objectNode.objectNode();
        objectNode.fields().forEachRemaining( e -> {
            final String property = e.getKey();
            final JsonNode node = e.getValue();

            final String[] names = property.split( "\\." );
            ObjectNode copy2 = copy;
            for ( int i = 0; i < names.length - 1; i++ ) {
                copy2 = copy2.with( names[i] );
            }
            copy2.set( names[names.length - 1], expandDots( node ) );
        } );

        return (T) copy;
    }

}
