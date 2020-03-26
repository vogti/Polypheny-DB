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

package org.polypheny.db.sql;


import java.util.List;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.BitString;
import org.polypheny.db.util.Util;


/**
 * A binary (or hexadecimal) string literal.
 * <p>
 * The {@link #value} field is a {@link BitString} and {@code #typeName} is {@link PolyType#BINARY}.
 */
public class SqlBinaryStringLiteral extends SqlAbstractStringLiteral {


    protected SqlBinaryStringLiteral( BitString val, SqlParserPos pos ) {
        super( val, PolyType.BINARY, pos );
    }


    /**
     * @return the underlying BitString
     */
    public BitString getBitString() {
        return (BitString) value;
    }


    @Override
    public SqlBinaryStringLiteral clone( SqlParserPos pos ) {
        return new SqlBinaryStringLiteral( (BitString) value, pos );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        assert value instanceof BitString;
        writer.literal( "X'" + ((BitString) value).toHexString() + "'" );
    }


    @Override
    protected SqlAbstractStringLiteral concat1( List<SqlLiteral> literals ) {
        return new SqlBinaryStringLiteral(
                BitString.concat( Util.transform( literals, literal -> ((SqlBinaryStringLiteral) literal).getBitString() ) ),
                literals.get( 0 ).getParserPosition() );
    }
}

