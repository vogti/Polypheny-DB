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

package org.polypheny.db.languages.sql.fun;


import java.util.Locale;
import java.util.Objects;
import org.polypheny.db.core.JsonArrayAgg;
import org.polypheny.db.core.Node;
import org.polypheny.db.languages.sql.validate.SqlValidator;
import org.polypheny.db.languages.sql.validate.SqlValidatorImpl;
import org.polypheny.db.languages.sql.validate.SqlValidatorScope;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.languages.sql.SqlAggFunction;
import org.polypheny.db.languages.sql.SqlCall;
import org.polypheny.db.languages.sql.SqlFunctionCategory;
import org.polypheny.db.languages.sql.SqlJsonConstructorNullClause;
import org.polypheny.db.core.Kind;
import org.polypheny.db.languages.sql.SqlNode;
import org.polypheny.db.languages.sql.SqlWriter;
import org.polypheny.db.type.PolyTypeFamily;
import org.polypheny.db.type.checker.OperandTypes;
import org.polypheny.db.type.inference.ReturnTypes;
import org.polypheny.db.util.Optionality;


/**
 * The <code>JSON_OBJECTAGG</code> aggregate function.
 */
public class SqlJsonArrayAggAggFunction extends SqlAggFunction implements JsonArrayAgg {

    private final SqlJsonConstructorNullClause nullClause;


    public SqlJsonArrayAggAggFunction( String name, SqlJsonConstructorNullClause nullClause ) {
        super(
                name,
                null,
                Kind.JSON_ARRAYAGG,
                ReturnTypes.VARCHAR_2000,
                null,
                OperandTypes.family( PolyTypeFamily.ANY ),
                SqlFunctionCategory.SYSTEM,
                false,
                false,
                Optionality.FORBIDDEN );
        this.nullClause = Objects.requireNonNull( nullClause );
    }


    @Override
    public void unparse( SqlWriter writer, SqlCall call, int leftPrec, int rightPrec ) {
        assert call.operandCount() == 1;
        final SqlWriter.Frame frame = writer.startFunCall( "JSON_ARRAYAGG" );
        call.operand( 0 ).unparse( writer, leftPrec, rightPrec );
        writer.keyword( nullClause.sql );
        writer.endFunCall( frame );
    }


    @Override
    public RelDataType deriveType( SqlValidator validator, SqlValidatorScope scope, SqlCall call ) {
        // To prevent operator rewriting by SqlFunction#deriveType.
        for ( Node operand : call.getOperandList() ) {
            RelDataType nodeType = validator.deriveType( scope, operand );
            ((SqlValidatorImpl) validator).setValidatedNodeType( (SqlNode) operand, nodeType );
        }
        return validateOperands( validator, scope, call );
    }


    @Override
    public String toString() {
        return getName() + String.format( Locale.ROOT, "<%s>", nullClause );
    }


    public SqlJsonArrayAggAggFunction with( SqlJsonConstructorNullClause nullClause ) {
        return this.nullClause == nullClause
                ? this
                : new SqlJsonArrayAggAggFunction( getName(), nullClause );
    }


    public SqlJsonConstructorNullClause getNullClause() {
        return nullClause;
    }
}

