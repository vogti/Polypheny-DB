/*
 * Copyright 2019-2020 The Polypheny Project
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
 *
 * This file incorporates code covered by the following terms:
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
 */

package ch.unibas.dmi.dbis.polyphenydb.sql.fun;


import ch.unibas.dmi.dbis.polyphenydb.sql.SqlCall;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlFunction;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlFunctionCategory;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlKind;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlWriter;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.OperandTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.ReturnTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlOperandTypeChecker;


/**
 * The <code>OVERLAY</code> function.
 */
public class SqlOverlayFunction extends SqlFunction {

    private static final SqlOperandTypeChecker OTC_CUSTOM =
            OperandTypes.or(
                    OperandTypes.STRING_STRING_INTEGER,
                    OperandTypes.STRING_STRING_INTEGER_INTEGER );


    public SqlOverlayFunction() {
        super(
                "OVERLAY",
                SqlKind.OTHER_FUNCTION,
                ReturnTypes.DYADIC_STRING_SUM_PRECISION_NULLABLE_VARYING,
                null,
                OTC_CUSTOM,
                SqlFunctionCategory.STRING );
    }


    @Override
    public void unparse( SqlWriter writer, SqlCall call, int leftPrec, int rightPrec ) {
        final SqlWriter.Frame frame = writer.startFunCall( getName() );
        call.operand( 0 ).unparse( writer, leftPrec, rightPrec );
        writer.sep( "PLACING" );
        call.operand( 1 ).unparse( writer, leftPrec, rightPrec );
        writer.sep( "FROM" );
        call.operand( 2 ).unparse( writer, leftPrec, rightPrec );
        if ( 4 == call.operandCount() ) {
            writer.sep( "FOR" );
            call.operand( 3 ).unparse( writer, leftPrec, rightPrec );
        }
        writer.endFunCall( frame );
    }


    @Override
    public String getSignatureTemplate( final int operandsCount ) {
        switch ( operandsCount ) {
            case 3:
                return "{0}({1} PLACING {2} FROM {3})";
            case 4:
                return "{0}({1} PLACING {2} FROM {3} FOR {4})";
        }
        assert false;
        return null;
    }
}

