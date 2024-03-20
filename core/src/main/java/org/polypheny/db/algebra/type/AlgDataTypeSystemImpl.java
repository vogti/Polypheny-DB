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

package org.polypheny.db.algebra.type;


import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.PolyTypeFamily;


/**
 * Default implementation of {@link AlgDataTypeSystem}, providing parameters from the SQL standard.
 *
 * To implement other type systems, create a derived class and override values as needed.
 *
 * <table border='1'>
 * <caption>Parameter values</caption>
 * <tr><th>Parameter</th>         <th>Value</th></tr>
 * <tr><td>MAX_NUMERIC_SCALE</td> <td>19</td></tr>
 * </table>
 */
public abstract class AlgDataTypeSystemImpl implements AlgDataTypeSystem {

    @Override
    public int getMaxScale( PolyType typeName ) {
        return switch ( typeName ) {
            case DECIMAL -> getMaxNumericScale();
            case INTERVAL_MILLISECONDS, INTERVAL_MONTH -> PolyType.MAX_INTERVAL_FRACTIONAL_SECOND_PRECISION;
            default -> -1;
        };
    }


    @Override
    public int getDefaultPrecision( PolyType typeName ) {
        // Following BasicPolyType precision as the default
        switch ( typeName ) {
            case CHAR:
            case BINARY:
                return 1;
            case JSON:
            case VARCHAR:
            case VARBINARY:
                return AlgDataType.PRECISION_NOT_SPECIFIED;
            case DECIMAL:
                return getMaxNumericPrecision();
            case INTERVAL_MONTH:
            case INTERVAL_MILLISECONDS:
                return PolyType.DEFAULT_INTERVAL_START_PRECISION;
            case BOOLEAN:
                return 1;
            case TINYINT:
                return 3;
            case SMALLINT:
                return 5;
            case INTEGER:
                return 10;
            case BIGINT:
                return 19;
            case REAL:
                return 7;
            case FLOAT:
            case DOUBLE:
                return 15;
            case TIME:
            case TIME_WITH_LOCAL_TIME_ZONE:
            case DATE:
                return 0; // SQL99 part 2 section 6.1 syntax rule 30
            case TIMESTAMP:
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                // farrago supports only 0 (see
                // PolyType.getDefaultPrecision), but it should be 6
                // (microseconds) per SQL99 part 2 section 6.1 syntax rule 30.
                return 0;
            default:
                return -1;
        }
    }


    @Override
    public int getMaxPrecision( PolyType typeName ) {
        switch ( typeName ) {
            case DECIMAL:
                return getMaxNumericPrecision();
            case JSON:
            case VARCHAR:
            case CHAR:
                return 65536;
            case VARBINARY:
            case BINARY:
                return 65536;
            case TIME:
            case TIME_WITH_LOCAL_TIME_ZONE:
            case TIMESTAMP:
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
                return PolyType.MAX_DATETIME_PRECISION;
            case INTERVAL_MILLISECONDS:
            case INTERVAL_MONTH:
                return PolyType.MAX_INTERVAL_START_PRECISION;
            default:
                return getDefaultPrecision( typeName );
        }
    }


    @Override
    public int getMaxNumericScale() {
        return 19;
    }


    @Override
    public int getMaxNumericPrecision() {
        return 19;
    }


    @Override
    public String getLiteral( PolyType typeName, boolean isPrefix ) {
        return switch ( typeName ) {
            case VARBINARY, VARCHAR, JSON, CHAR -> "'";
            case BINARY -> isPrefix ? "x'" : "'";
            case TIMESTAMP -> isPrefix ? "TIMESTAMP '" : "'";
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE -> isPrefix ? "TIMESTAMP WITH LOCAL TIME ZONE '" : "'";
            case INTERVAL_MILLISECONDS -> isPrefix ? "INTERVAL '" : "' DAY";
            case INTERVAL_MONTH -> isPrefix ? "INTERVAL '" : "' YEAR TO MONTH";
            case TIME -> isPrefix ? "TIME '" : "'";
            case TIME_WITH_LOCAL_TIME_ZONE -> isPrefix ? "TIME WITH LOCAL TIME ZONE '" : "'";
            case DATE -> isPrefix ? "DATE '" : "'";
            case ARRAY -> isPrefix ? "(" : ")";
            default -> null;
        };
    }


    @Override
    public boolean isCaseSensitive( PolyType typeName ) {
        switch ( typeName ) {
            case CHAR:
            case JSON:
            case VARCHAR:
                return true;
            default:
                return false;
        }
    }


    @Override
    public boolean isAutoincrement( PolyType typeName ) {
        return false;
    }


    @Override
    public int getNumTypeRadix( PolyType typeName ) {
        if ( typeName.getFamily() == PolyTypeFamily.NUMERIC && getDefaultPrecision( typeName ) != -1 ) {
            return 10;
        }
        return 0;
    }


    @Override
    public AlgDataType deriveSumType( AlgDataTypeFactory typeFactory, AlgDataType argumentType ) {
        return argumentType;
    }


    @Override
    public AlgDataType deriveAvgAggType( AlgDataTypeFactory typeFactory, AlgDataType argumentType ) {
        return argumentType;
    }


    @Override
    public AlgDataType deriveCovarType( AlgDataTypeFactory typeFactory, AlgDataType arg0Type, AlgDataType arg1Type ) {
        return arg0Type;
    }


    @Override
    public AlgDataType deriveFractionalRankType( AlgDataTypeFactory typeFactory ) {
        return typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.DOUBLE ), false );
    }


    @Override
    public AlgDataType deriveRankType( AlgDataTypeFactory typeFactory ) {
        return typeFactory.createTypeWithNullability( typeFactory.createPolyType( PolyType.BIGINT ), false );
    }


    @Override
    public boolean isSchemaCaseSensitive() {
        return true;
    }


    @Override
    public boolean shouldConvertRaggedUnionTypesToVarying() {
        return false;
    }


    public boolean allowExtendedTrim() {
        return false;
    }

}
