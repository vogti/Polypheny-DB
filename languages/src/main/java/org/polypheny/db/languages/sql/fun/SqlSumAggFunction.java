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


import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.languages.sql.SqlAggFunction;
import org.polypheny.db.core.FunctionCategory;
import org.polypheny.db.core.Kind;
import org.polypheny.db.languages.sql.SqlSplittableAggFunction;
import org.polypheny.db.type.checker.OperandTypes;
import org.polypheny.db.type.inference.ReturnTypes;
import org.polypheny.db.util.Optionality;


/**
 * <code>Sum</code> is an aggregator which returns the sum of the values which go into it. It has precisely one argument of numeric type (<code>int</code>, <code>long</code>, <code>float</code>, <code>double</code>),
 * and the result is the same type.
 */
public class SqlSumAggFunction extends SqlAggFunction {


    public SqlSumAggFunction( RelDataType type ) {
        super(
                "SUM",
                null,
                Kind.SUM,
                ReturnTypes.AGG_SUM,
                null,
                OperandTypes.NUMERIC,
                FunctionCategory.NUMERIC,
                false,
                false,
                Optionality.FORBIDDEN );
    }


    @Override
    public <T> T unwrap( Class<T> clazz ) {
        if ( clazz == SqlSplittableAggFunction.class ) {
            return clazz.cast( SqlSplittableAggFunction.SumSplitter.INSTANCE );
        }
        return super.unwrap( clazz );
    }
}

