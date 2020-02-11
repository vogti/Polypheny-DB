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


import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlCall;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlKind;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlSpecialOperator;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlWriter;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.OperandTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeName;


/**
 * The JSON value expression operator that indicates that the value expression should be parsed as JSON.
 */
public class SqlJsonValueExpressionOperator extends SqlSpecialOperator {

    private final boolean structured;


    public SqlJsonValueExpressionOperator( String name, boolean structured ) {
        super(
                name,
                SqlKind.JSON_VALUE_EXPRESSION,
                100,
                true,
                opBinding -> {
                    final RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
                    return typeFactory.createTypeWithNullability( typeFactory.createSqlType( SqlTypeName.ANY ), true );
                },
                ( callBinding, returnType, operandTypes ) -> {
                    if ( callBinding.isOperandNull( 0, false ) ) {
                        final RelDataTypeFactory typeFactory = callBinding.getTypeFactory();
                        operandTypes[0] = typeFactory.createTypeWithNullability( typeFactory.createSqlType( SqlTypeName.ANY ), true );
                    }
                },
                structured ? OperandTypes.ANY : OperandTypes.STRING );
        this.structured = structured;
    }


    @Override
    public void unparse( SqlWriter writer, SqlCall call, int leftPrec, int rightPrec ) {
        call.operand( 0 ).unparse( writer, 0, 0 );
        if ( !structured ) {
            writer.keyword( "FORMAT JSON" );
        }
    }
}

