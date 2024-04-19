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

package org.polypheny.db.protointerface.statementProcessing;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.catalog.logistic.DataModel;
import org.polypheny.db.languages.LanguageManager;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.nodes.Node;
import org.polypheny.db.processing.ImplementationContext;
import org.polypheny.db.processing.Processor;
import org.polypheny.db.processing.QueryContext;
import org.polypheny.db.protointerface.PIServiceException;
import org.polypheny.db.protointerface.proto.Frame;
import org.polypheny.db.protointerface.proto.StatementResult;
import org.polypheny.db.protointerface.relational.RelationalMetaRetriever;
import org.polypheny.db.protointerface.statements.PIPreparedStatement;
import org.polypheny.db.protointerface.statements.PIStatement;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.util.Pair;

public class StatementProcessor {

    private static final String ORIGIN = "Proto-Interface";

    private static final Map<DataModel, Executor> RESULT_RETRIEVERS =
            ImmutableMap.<DataModel, Executor>builder()
                    .put( DataModel.RELATIONAL, new RelationalExecutor() )
                    .put( DataModel.DOCUMENT, new DocumentExecutor() )
                    .build();


    public static void implement( PIStatement piStatement ) {
        Statement statement = piStatement.getStatement();
        if ( statement == null ) {
            throw new PIServiceException( "Statement is not linked to a PolyphenyStatement",
                    "I9003",
                    9003
            );
        }
        QueryContext context = QueryContext.builder()
                .query( piStatement.getQuery() )
                .language( piStatement.getLanguage() )
                .namespaceId( piStatement.getNamespace().id )
                .origin( ORIGIN )
                .build();
        List<ImplementationContext> implementations = LanguageManager.getINSTANCE().anyPrepareQuery( context, statement );
        if ( implementations.get( 0 ).getImplementation() == null ) {
            throw new GenericRuntimeException( implementations.get( 0 ).getException().orElseThrow() );
        }
        piStatement.setImplementation( implementations.get( 0 ).getImplementation() );
    }


    public static StatementResult executeAndGetResult( PIStatement piStatement ) {
        Executor executor = RESULT_RETRIEVERS.get( piStatement.getLanguage().dataModel() );
        if ( executor == null ) {
            throw new PIServiceException( "No result retriever registered for namespace type "
                    + piStatement.getLanguage().dataModel(),
                    "I9004",
                    9004
            );
        }
        return executor.executeAndGetResult( piStatement );
    }


    public static StatementResult executeAndGetResult( PIStatement piStatement, int fetchSize ) {
        Executor executor = RESULT_RETRIEVERS.get( piStatement.getLanguage().dataModel() );
        if ( executor == null ) {
            throw new PIServiceException( "No result retriever registered for namespace type "
                    + piStatement.getLanguage().dataModel(),
                    "I9004",
                    9004
            );
        }
        try {
            return executor.executeAndGetResult( piStatement, fetchSize );
        } catch ( Exception e ) {
            throw new GenericRuntimeException( e ); // TODO: check if this is still needed, or if executeAndGetResult throws another exception by now
        }
    }


    public static Frame fetch( PIStatement piStatement, int fetchSize ) {
        Executor executor = RESULT_RETRIEVERS.get( piStatement.getLanguage().dataModel() );
        if ( executor == null ) {
            throw new PIServiceException( "No result retriever registered for namespace type "
                    + piStatement.getLanguage().dataModel(),
                    "I9004",
                    9004
            );
        }
        return executor.fetch( piStatement, fetchSize );
    }


    public static void prepare( PIPreparedStatement piStatement ) {
        Transaction transaction = piStatement.getClient().getOrCreateNewTransaction();
        String query = piStatement.getQuery();
        QueryLanguage queryLanguage = piStatement.getLanguage();

        Processor queryProcessor = transaction.getProcessor( queryLanguage );
        Node parsed = queryProcessor.parse( query ).get( 0 );
        // It is important not to add default values for missing fields in insert statements. If we did this, the
        // JDBC driver would expect more parameter fields than there actually are in the query.
        Pair<Node, AlgDataType> validated = queryProcessor.validate( transaction, parsed, false );
        AlgDataType parameterRowType = queryProcessor.getParameterRowType( validated.left );
        piStatement.setParameterMetas( RelationalMetaRetriever.retrieveParameterMetas( parameterRowType ) );
    }

}
