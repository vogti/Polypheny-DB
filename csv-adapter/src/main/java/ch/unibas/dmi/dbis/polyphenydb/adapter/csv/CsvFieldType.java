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

package ch.unibas.dmi.dbis.polyphenydb.adapter.csv;


import ch.unibas.dmi.dbis.polyphenydb.PolySqlType;
import ch.unibas.dmi.dbis.polyphenydb.adapter.java.JavaTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import java.util.HashMap;
import java.util.Map;
import org.apache.calcite.linq4j.tree.Primitive;


/**
 * Type of a field in a CSV file.
 *
 * Usually, and unless specified explicitly in the header row, a field is of type {@link #STRING}. But specifying the field type in the header row makes it easier to write SQL.
 */
enum CsvFieldType {
    STRING( String.class, "string" ),
    BOOLEAN( Primitive.BOOLEAN ),
    BYTE( Primitive.BYTE ),
    CHAR( Primitive.CHAR ),
    SHORT( Primitive.SHORT ),
    INT( Primitive.INT ),
    LONG( Primitive.LONG ),
    FLOAT( Primitive.FLOAT ),
    DOUBLE( Primitive.DOUBLE ),
    DATE( java.sql.Date.class, "date" ),
    TIME( java.sql.Time.class, "time" ),
    TIMESTAMP( java.sql.Timestamp.class, "timestamp" );

    private final Class clazz;
    private final String simpleName;

    private static final Map<String, CsvFieldType> MAP = new HashMap<>();


    static {
        for ( CsvFieldType value : values() ) {
            MAP.put( value.simpleName, value );
        }
    }


    CsvFieldType( Primitive primitive ) {
        this( primitive.boxClass, primitive.primitiveClass.getSimpleName() );
    }


    CsvFieldType( Class clazz, String simpleName ) {
        this.clazz = clazz;
        this.simpleName = simpleName;
    }


    public static CsvFieldType getCsvFieldType( PolySqlType type ) {
        switch ( type ) {
            case BOOLEAN:
                return CsvFieldType.BOOLEAN;
            case VARBINARY:
                return CsvFieldType.BYTE;
            case INTEGER:
                return CsvFieldType.INT;
            case BIGINT:
                throw new RuntimeException( "Unsupported datatype: " + type.name() );
            case REAL:
                throw new RuntimeException( "Unsupported datatype: " + type.name() );
            case DOUBLE:
                return CsvFieldType.DOUBLE;
            case DECIMAL:
                throw new RuntimeException( "Unsupported datatype: " + type.name() );
            case VARCHAR:
                return CsvFieldType.STRING;
            case TEXT:
                return CsvFieldType.STRING;
            case DATE:
                return CsvFieldType.DATE;
            case TIME:
                return CsvFieldType.TIME;
            case TIMESTAMP:
                return CsvFieldType.TIMESTAMP;
            default:
                throw new RuntimeException( "Unsupported datatype: " + type.name() );
        }
    }


    public RelDataType toType( JavaTypeFactory typeFactory ) {
        RelDataType javaType = typeFactory.createJavaType( clazz );
        RelDataType sqlType = typeFactory.createSqlType( javaType.getSqlTypeName() );
        return typeFactory.createTypeWithNullability( sqlType, true );
    }


    public static CsvFieldType of( String typeString ) {
        return MAP.get( typeString );
    }
}
