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

package ch.unibas.dmi.dbis.polyphenydb.sql.fun;


import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlBinaryOperator;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlCall;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlCallBinding;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlKind;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlOperandCountRange;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlWriter;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.InferTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.OperandTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.ReturnTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlOperandCountRanges;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlSingleOperandTypeChecker;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeUtil;
import com.google.common.collect.ImmutableList;


/**
 * SqlOverlapsOperator represents the SQL:1999 standard {@code OVERLAPS} function. Determines whether two anchored time intervals overlap.
 */
public class SqlOverlapsOperator extends SqlBinaryOperator {


    SqlOverlapsOperator( SqlKind kind ) {
        super(
                kind.sql,
                kind,
                30,
                true,
                ReturnTypes.BOOLEAN_NULLABLE,
                InferTypes.FIRST_KNOWN,
                OperandTypes.sequence( "'<PERIOD> " + kind.sql + " <PERIOD>'", OperandTypes.PERIOD, OperandTypes.PERIOD ) );
    }


    @Override
    public void unparse( SqlWriter writer, SqlCall call, int leftPrec, int rightPrec ) {
        final SqlWriter.Frame frame = writer.startList( SqlWriter.FrameTypeEnum.SIMPLE );
        arg( writer, call, leftPrec, rightPrec, 0 );
        writer.sep( getName() );
        arg( writer, call, leftPrec, rightPrec, 1 );
        writer.endList( frame );
    }


    void arg( SqlWriter writer, SqlCall call, int leftPrec, int rightPrec, int i ) {
        if ( SqlUtil.isCallTo( call.operand( i ), SqlStdOperatorTable.ROW ) ) {
            SqlCall row = call.operand( i );
            writer.keyword( "PERIOD" );
            writer.sep( "(", true );
            row.operand( 0 ).unparse( writer, leftPrec, rightPrec );
            writer.sep( ",", true );
            row.operand( 1 ).unparse( writer, leftPrec, rightPrec );
            writer.sep( ")", true );
        } else {
            call.operand( i ).unparse( writer, leftPrec, rightPrec );
        }
    }


    @Override
    public SqlOperandCountRange getOperandCountRange() {
        return SqlOperandCountRanges.of( 2 );
    }


    @Override
    public String getAllowedSignatures( String opName ) {
        final String d = "DATETIME";
        final String i = "INTERVAL";
        String[] typeNames = {
                d, d,
                d, i,
                i, d,
                i, i
        };

        StringBuilder ret = new StringBuilder();
        for ( int y = 0; y < typeNames.length; y += 2 ) {
            if ( y > 0 ) {
                ret.append( NL );
            }
            ret.append( SqlUtil.getAliasedSignature( this, opName, ImmutableList.of( d, typeNames[y], d, typeNames[y + 1] ) ) );
        }
        return ret.toString();
    }


    @Override
    public boolean checkOperandTypes( SqlCallBinding callBinding, boolean throwOnFailure ) {
        if ( !OperandTypes.PERIOD.checkSingleOperandType( callBinding, callBinding.operand( 0 ), 0, throwOnFailure ) ) {
            return false;
        }
        final SqlSingleOperandTypeChecker rightChecker;
        switch ( kind ) {
            case CONTAINS:
                rightChecker = OperandTypes.PERIOD_OR_DATETIME;
                break;
            default:
                rightChecker = OperandTypes.PERIOD;
                break;
        }
        if ( !rightChecker.checkSingleOperandType( callBinding, callBinding.operand( 1 ), 0, throwOnFailure ) ) {
            return false;
        }
        final RelDataType t0 = callBinding.getOperandType( 0 );
        final RelDataType t1 = callBinding.getOperandType( 1 );
        if ( !SqlTypeUtil.isDatetime( t1 ) ) {
            final RelDataType t00 = t0.getFieldList().get( 0 ).getType();
            final RelDataType t10 = t1.getFieldList().get( 0 ).getType();
            if ( !SqlTypeUtil.sameNamedType( t00, t10 ) ) {
                if ( throwOnFailure ) {
                    throw callBinding.newValidationSignatureError();
                }
                return false;
            }
        }
        return true;
    }
}

