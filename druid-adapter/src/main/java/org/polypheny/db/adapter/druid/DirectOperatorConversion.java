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

package org.polypheny.db.adapter.druid;


import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.sql.SqlOperator;
import java.util.List;


/**
 * Direct operator conversion for expression like Function(exp_1,...exp_n)
 */
public class DirectOperatorConversion implements DruidSqlOperatorConverter {

    private final SqlOperator operator;
    private final String druidFunctionName;


    public DirectOperatorConversion( final SqlOperator operator, final String druidFunctionName ) {
        this.operator = operator;
        this.druidFunctionName = druidFunctionName;
    }


    @Override
    public SqlOperator polyphenyDbOperator() {
        return operator;
    }


    @Override
    public String toDruidExpression( RexNode rexNode, RelDataType rowType, DruidQuery druidQuery ) {
        final RexCall call = (RexCall) rexNode;
        final List<String> druidExpressions = DruidExpressions.toDruidExpressions( druidQuery, rowType, call.getOperands() );
        if ( druidExpressions == null ) {
            return null;
        }
        return DruidExpressions.functionCall( druidFunctionName, druidExpressions );
    }
}

