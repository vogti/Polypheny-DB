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


import com.google.common.collect.Iterables;
import java.util.List;
import org.polypheny.db.core.nodes.Operator;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexNode;


/**
 * Unary suffix operator conversion, used to convert function like: expression Unary_Operator
 */
public class UnarySuffixOperatorConversion implements DruidSqlOperatorConverter {

    private final Operator operator;
    private final String druidOperator;


    public UnarySuffixOperatorConversion( Operator operator, String druidOperator ) {
        this.operator = operator;
        this.druidOperator = druidOperator;
    }


    @Override
    public Operator polyphenyDbOperator() {
        return operator;
    }


    @Override
    public String toDruidExpression( RexNode rexNode, RelDataType rowType, DruidQuery druidQuery ) {
        final RexCall call = (RexCall) rexNode;

        final List<String> druidExpressions = DruidExpressions.toDruidExpressions( druidQuery, rowType, call.getOperands() );

        if ( druidExpressions == null ) {
            return null;
        }

        return DruidQuery.format( "(%s %s)", Iterables.getOnlyElement( druidExpressions ), druidOperator );
    }

}

