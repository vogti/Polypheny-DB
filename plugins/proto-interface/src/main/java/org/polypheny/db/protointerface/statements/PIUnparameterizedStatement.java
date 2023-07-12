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

package org.polypheny.db.protointerface.statements;

import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.protointerface.PIStatementProperties;
import org.polypheny.db.protointerface.PIClient;
import org.polypheny.db.protointerface.proto.StatementResult;
import org.polypheny.db.transaction.Statement;

@Slf4j
public class PIUnparameterizedStatement extends PIStatement {

    private PIUnparameterizedStatement(Builder builder) {
        super(builder);
    }

    public StatementResult execute() throws Exception {
        Statement currentStatement = protoInterfaceClient.getCurrentOrCreateNewTransaction().createStatement();
        return execute(currentStatement);

    }

    public static Builder newBuilder() {
        return new Builder();
    }


    static class Builder extends PIStatement.Builder {

        private Builder() {
            super();
        }

        public Builder setStatementId(int statementId) {
            this.statementId = statementId;
            return this;
        }


        public Builder setProtoInterfaceClient(PIClient protoInterfaceClient) {
            this.protoInterfaceClient = protoInterfaceClient;
            return this;
        }


        public Builder setQueryLanguage(QueryLanguage queryLanguage) {
            this.queryLanguage = queryLanguage;
            return this;
        }

        public Builder setQuery(String query) {
            this.query = query;
            return this;
        }

        public Builder setProperties(PIStatementProperties properties) {
            this.properties = properties;
            return this;
        }

        public PIUnparameterizedStatement build() {
            return new PIUnparameterizedStatement(this);
        }
    }
}