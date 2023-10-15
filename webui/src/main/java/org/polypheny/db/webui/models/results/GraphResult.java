/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.db.webui.models.results;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.webui.models.catalog.FieldDefinition;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@Value
public class GraphResult extends Result<String[], FieldDefinition> {

    public GraphResult(
            @JsonProperty("namespaceType") NamespaceType namespaceType,
            @JsonProperty("namespace") String namespace,
            @JsonProperty("data") String[][] data,
            @JsonProperty("header") FieldDefinition[] header,
            @JsonProperty("exception") Throwable exception,
            @JsonProperty("query") String query,
            @JsonProperty("xid") String xid,
            @JsonProperty("error") String error,
            @JsonProperty("currentPage") int currentPage,
            @JsonProperty("highestPage") int highestPage,
            @JsonProperty("hasMore") boolean hasMore,
            @JsonProperty("language") QueryLanguage language,
            @JsonProperty("affectedTuples") int affectedTuples ) {
        super( namespaceType, namespace, data, header, exception, query, xid, error, currentPage, highestPage, hasMore, language, affectedTuples );
    }

    public static abstract class GraphResultBuilder<C extends GraphResult, B extends GraphResultBuilder<C, B>> extends ResultBuilder<String[], FieldDefinition, C, B> {

    }

}