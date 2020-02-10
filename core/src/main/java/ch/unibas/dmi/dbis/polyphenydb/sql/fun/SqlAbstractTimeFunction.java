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


import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlFunction;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlFunctionCategory;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlKind;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlOperatorBinding;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlSyntax;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.OperandTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlOperandTypeChecker;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeName;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.validate.SqlMonotonicity;
import ch.unibas.dmi.dbis.polyphenydb.util.Static;


/**
 * Base class for time functions such as "LOCALTIME", "LOCALTIME(n)".
 */
public class SqlAbstractTimeFunction extends SqlFunction {

    private static final SqlOperandTypeChecker OTC_CUSTOM = OperandTypes.or( OperandTypes.POSITIVE_INTEGER_LITERAL, OperandTypes.NILADIC );

    private final SqlTypeName typeName;


    protected SqlAbstractTimeFunction( String name, SqlTypeName typeName ) {
        super( name, SqlKind.OTHER_FUNCTION, null, null, OTC_CUSTOM, SqlFunctionCategory.TIMEDATE );
        this.typeName = typeName;
    }


    @Override
    public SqlSyntax getSyntax() {
        return SqlSyntax.FUNCTION_ID;
    }


    @Override
    public RelDataType inferReturnType( SqlOperatorBinding opBinding ) {
        // REVIEW jvs: Need to take care of time zones.
        int precision = 0;
        if ( opBinding.getOperandCount() == 1 ) {
            RelDataType type = opBinding.getOperandType( 0 );
            if ( SqlTypeUtil.isNumeric( type ) ) {
                precision = opBinding.getOperandLiteralValue( 0, Integer.class );
            }
        }
        assert precision >= 0;
        if ( precision > SqlTypeName.MAX_DATETIME_PRECISION ) {
            throw opBinding.newError( Static.RESOURCE.argumentMustBeValidPrecision( opBinding.getOperator().getName(), 0, SqlTypeName.MAX_DATETIME_PRECISION ) );
        }
        return opBinding.getTypeFactory().createSqlType( typeName, precision );
    }


    // All of the time functions are increasing. Not strictly increasing.
    @Override
    public SqlMonotonicity getMonotonicity( SqlOperatorBinding call ) {
        return SqlMonotonicity.INCREASING;
    }


    // Plans referencing context variables should never be cached
    @Override
    public boolean isDynamicFunction() {
        return true;
    }
}

