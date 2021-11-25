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

package org.polypheny.db.languages;

import java.io.Reader;
import java.util.List;
import java.util.TimeZone;
import org.apache.calcite.avatica.util.TimeUnit;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.catalog.Catalog.QueryLanguage;
import org.polypheny.db.core.Conformance;
import org.polypheny.db.core.IntervalQualifier;
import org.polypheny.db.core.OperatorTable;
import org.polypheny.db.core.ParserPos;
import org.polypheny.db.core.enums.FunctionCategory;
import org.polypheny.db.core.enums.Kind;
import org.polypheny.db.core.fun.AggFunction;
import org.polypheny.db.core.nodes.DataTypeSpec;
import org.polypheny.db.core.nodes.Identifier;
import org.polypheny.db.core.nodes.Literal;
import org.polypheny.db.core.nodes.Operator;
import org.polypheny.db.core.validate.Validator;
import org.polypheny.db.jdbc.Context;
import org.polypheny.db.languages.Parser.ParserConfig;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.plan.RelOptTable.ViewExpander;
import org.polypheny.db.prepare.PolyphenyDbCatalogReader;
import org.polypheny.db.prepare.Prepare.CatalogReader;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeFactory;
import org.polypheny.db.schema.AggregateFunction;
import org.polypheny.db.schema.Function;
import org.polypheny.db.schema.TableFunction;
import org.polypheny.db.schema.TableMacro;
import org.polypheny.db.type.PolyIntervalQualifier;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.checker.FamilyOperandTypeChecker;
import org.polypheny.db.type.checker.PolySingleOperandTypeChecker;
import org.polypheny.db.type.inference.PolyOperandTypeInference;
import org.polypheny.db.type.inference.PolyReturnTypeInference;
import org.polypheny.db.util.Optionality;
import org.slf4j.Logger;

public abstract class LanguageManager {

    private static LanguageManager instance;


    public static LanguageManager getInstance() {
        return instance;
    }


    public static synchronized LanguageManager setAndGetInstance( LanguageManager manager ) {
        instance = manager;
        return instance;
    }


    public abstract Validator createValidator( QueryLanguage language, Context context, PolyphenyDbCatalogReader catalogReader );

    public abstract NodeToRelConverter createToRelConverter( QueryLanguage sql,
            ViewExpander polyphenyDbPreparingStmt,
            Validator validator,
            CatalogReader catalogReader,
            RelOptCluster cluster,
            RexConvertletTable convertletTable,
            NodeToRelConverter.Config config );

    public abstract RexConvertletTable getStandardConvertlet();

    public abstract OperatorTable getStdOperatorTable();

    public abstract Validator createPolyphenyValidator( QueryLanguage sql, OperatorTable operatorTable, PolyphenyDbCatalogReader catalogReader, JavaTypeFactory typeFactory, Conformance conformance );

    public abstract ParserFactory getFactory( QueryLanguage sql );

    public abstract Parser getParser( QueryLanguage sql, Reader reader, ParserConfig sqlParserConfig );

    public abstract OperatorTable getOracleOperatorTable();

    public abstract Logger getLogger( QueryLanguage queryLanguage, Class<RelNode> relNodeClass );

    public abstract Identifier createIdentifier( QueryLanguage sql, String name, ParserPos zero );

    public abstract Identifier createIdentifier( QueryLanguage sql, List<String> names, ParserPos zero );

    public abstract DataTypeSpec createDataTypeSpec( QueryLanguage sql, Identifier typeIdentifier, int precision, int scale, String charSetName, TimeZone o, ParserPos zero );

    public abstract DataTypeSpec createDataTypeSpec( QueryLanguage sql, Identifier typeIdentifier, Identifier componentTypeIdentifier, int precision, int scale, int dimension, int cardinality, String charSetName, TimeZone o, boolean nullable, ParserPos zero );

    public abstract IntervalQualifier createIntervalQualifier( QueryLanguage queryLanguage, TimeUnit startUnit, int startPrecision, TimeUnit endUnit, int fractionalSecondPrecision, ParserPos zero );

    public abstract Literal createLiteral( PolyType polyType, Object o, ParserPos pos );

    public abstract AggFunction createMinMaxAggFunction( Kind kind );

    public abstract AggFunction createSumEmptyIsZeroFunction();

    public abstract AggFunction createBitOpAggFunction( Kind kind );

    public abstract AggFunction createSumAggFunction( RelDataType type );

    public abstract Operator createFunction( String artificial_selectivity, Kind otherFunction, PolyReturnTypeInference aBoolean, PolyOperandTypeInference o, PolySingleOperandTypeChecker numeric, FunctionCategory system );

    public abstract void createIntervalTypeString( StringBuilder sb, PolyIntervalQualifier intervalQualifier );

    public abstract Operator createUserDefinedFunction( QueryLanguage queryLanguage, Identifier name, PolyReturnTypeInference infer, PolyOperandTypeInference explicit, FamilyOperandTypeChecker typeChecker, List<RelDataType> paramTypes, Function function );

    public abstract Operator createUserDefinedAggFunction( QueryLanguage queryLanguage, Identifier name, PolyReturnTypeInference infer, PolyOperandTypeInference explicit, FamilyOperandTypeChecker typeChecker, AggregateFunction function, boolean b, boolean b1, Optionality forbidden, RelDataTypeFactory typeFactory );

    public abstract Operator createUserDefinedTableMacro( QueryLanguage sql, Identifier name, PolyReturnTypeInference cursor, PolyOperandTypeInference explicit, FamilyOperandTypeChecker typeChecker, List<RelDataType> paramTypes, TableMacro function );

    public abstract Operator createUserDefinedTableFunction( QueryLanguage sql, Identifier name, PolyReturnTypeInference cursor, PolyOperandTypeInference explicit, FamilyOperandTypeChecker typeChecker, List<RelDataType> paramTypes, TableFunction function );

}
