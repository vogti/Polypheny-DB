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

package ch.unibas.dmi.dbis.polyphenydb.sql;


import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParserPos;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeName;
import ch.unibas.dmi.dbis.polyphenydb.util.TimestampString;


/**
 * A SQL literal representing a DATE, TIME or TIMESTAMP value.
 *
 * Examples:
 *
 * <ul>
 * <li>DATE '2004-10-22'</li>
 * <li>TIME '14:33:44.567'</li>
 * <li><code>TIMESTAMP '1969-07-21 03:15 GMT'</code></li>
 * </ul>
 */
public abstract class SqlAbstractDateTimeLiteral extends SqlLiteral {

    protected final boolean hasTimeZone;
    protected final int precision;


    /**
     * Constructs a datetime literal.
     */
    protected SqlAbstractDateTimeLiteral( Object d, boolean tz, SqlTypeName typeName, int precision, SqlParserPos pos ) {
        super( d, typeName, pos );
        this.hasTimeZone = tz;
        this.precision = precision;
    }


    /**
     * Converts this literal to a {@link TimestampString}.
     */
    protected TimestampString getTimestamp() {
        return (TimestampString) value;
    }


    public int getPrec() {
        return precision;
    }


    /**
     * Returns e.g. <code>DATE '1969-07-21'</code>.
     */
    public abstract String toString();

    /**
     * Returns e.g. <code>1969-07-21</code>.
     */
    public abstract String toFormattedString();


    @Override
    public RelDataType createSqlType( RelDataTypeFactory typeFactory ) {
        return typeFactory.createSqlType( getTypeName(), getPrec() );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        writer.literal( this.toString() );
    }
}


