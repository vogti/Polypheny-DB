/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.db.languages.mql;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.Catalog.QueryLanguage;
import org.polypheny.db.core.enums.Kind;
import org.polypheny.db.core.nodes.Operator;
import org.polypheny.db.core.operators.OperatorName;
import org.polypheny.db.languages.NodeParseException;
import org.polypheny.db.languages.StdOperatorRegistry;
import org.polypheny.db.languages.core.LanguageManagerDependant;
import org.polypheny.db.languages.mql.parser.MqlParser;
import org.polypheny.db.languages.mql.parser.MqlParser.MqlParserConfig;
import org.polypheny.db.languages.mql2rel.MqlMockCatalog;
import org.polypheny.db.util.SourceStringReader;


public abstract class MqlTest extends LanguageManagerDependant {

    private static final MqlParserConfig parserConfig;
    @Getter
    private static final Map<String, Operator> biComparisons;
    @Getter
    private static final Map<String, Kind> logicalOperators;

    public static final Operator eq = StdOperatorRegistry.get( QueryLanguage.MONGOQL, OperatorName.MQL_EQUALS );
    public static final Operator ne = StdOperatorRegistry.get( OperatorName.NOT_EQUALS );
    public static final Operator gt = StdOperatorRegistry.get( QueryLanguage.MONGOQL, OperatorName.MQL_GT );
    public static final Operator gte = StdOperatorRegistry.get( QueryLanguage.MONGOQL, OperatorName.MQL_GTE );
    public static final Operator lt = StdOperatorRegistry.get( QueryLanguage.MONGOQL, OperatorName.MQL_LT );
    public static final Operator lte = StdOperatorRegistry.get( QueryLanguage.MONGOQL, OperatorName.MQL_LTE );


    static {
        Catalog.setAndGetInstance( new MqlMockCatalog() );
        MqlParser.ConfigBuilder configConfigBuilder = MqlParser.configBuilder();
        parserConfig = configConfigBuilder.build();
        biComparisons = new HashMap<>();

        biComparisons.put( "$eq", eq );
        biComparisons.put( "$ne", ne );
        biComparisons.put( "$gt", gt );
        biComparisons.put( "$gte", gte );
        biComparisons.put( "$lt", lt );
        biComparisons.put( "$lte", lte );

        logicalOperators = new HashMap<>();

        logicalOperators.put( "$and", Kind.AND );
        logicalOperators.put( "$or", Kind.OR );
        // logicalOperators.put( "nor", Kind.N);
        // logicalOperators.put( "not", Kind.NOT );
    }


    public MqlNode parse( String mql ) {
        final MqlParser parser = MqlParser.create( new SourceStringReader( mql ), parserConfig );
        try {
            return parser.parseStmt();
        } catch ( NodeParseException e ) {
            throw new RuntimeException( e );
        }
    }

}
