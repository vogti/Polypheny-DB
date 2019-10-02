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

package ch.unibas.dmi.dbis.polyphenydb.util;


import java.nio.ByteOrder;
import java.text.NumberFormat;
import java.util.Locale;


/**
 * Utility functions for converting from one type to another
 */
public class ConversionUtil {

    private ConversionUtil() {
    }


    public static final String NATIVE_UTF16_CHARSET_NAME = (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) ? "UTF-16BE" : "UTF-16LE";

    /**
     * A constant string which can be used wherever a Java string containing Unicode characters is needed in a test. It spells 'anthropos' in Greek.
     */
    public static final String TEST_UNICODE_STRING = "\u03B1\u03BD\u03B8\u03C1\u03C9\u03C0\u03BF\u03C2";

    /**
     * A constant string which can be used wherever a SQL literal containing Unicode escape characters is needed in a test. It spells 'anthropos' in Greek.
     * The escape character is the SQL default (backslash); note that the backslash-doubling here is for Java only, so by the time the SQL parser
     * gets it, there is only one backslash.
     */
    public static final String TEST_UNICODE_SQL_ESCAPED_LITERAL = "\\03B1\\03BD\\03B8\\03C1\\03C9\\03C0\\03BF\\03C2";


    /**
     * Converts a byte array into a bit string or a hex string.
     *
     * For example, <code>toStringFromByteArray(new byte[] {0xAB, 0xCD}, 16)</code> returns <code>ABCD</code>.
     */
    public static String toStringFromByteArray( byte[] value, int radix ) {
        assert (2 == radix) || (16 == radix) : "Make sure that the algorithm below works for your radix";
        if ( 0 == value.length ) {
            return "";
        }

        int trick = radix * radix;
        StringBuilder ret = new StringBuilder();
        for ( byte b : value ) {
            ret.append( Integer.toString( trick | (0x0ff & b), radix ).substring( 1 ) );
        }

        return ret.toString().toUpperCase( Locale.ROOT );
    }


    /**
     * Converts a string into a byte array. The inverse of {@link #toStringFromByteArray(byte[], int)}.
     */
    public static byte[] toByteArrayFromString( String value, int radix ) {
        assert 16 == radix : "Specified string to byte array conversion not supported yet";
        assert (value.length() % 2) == 0 : "Hex binary string must contain even number of characters";

        byte[] ret = new byte[value.length() / 2];
        for ( int i = 0; i < ret.length; i++ ) {
            int digit1 = Character.digit( value.charAt( i * 2 ), radix );
            int digit2 = Character.digit( value.charAt( (i * 2) + 1 ), radix );
            assert (digit1 != -1) && (digit2 != -1) : "String could not be converted to byte array";
            ret[i] = (byte) ((digit1 * radix) + digit2);
        }
        return ret;
    }


    /**
     * Converts an approximate value into a string, following the SQL 2003 standard.
     */
    public static String toStringFromApprox( double d, boolean isFloat ) {
        NumberFormat nf = NumberUtil.getApproxFormatter( isFloat );
        return nf.format( d );
    }


    /**
     * Converts a string into a boolean
     */
    public static Boolean toBoolean( String str ) {
        if ( str == null ) {
            return null;
        }
        str = str.trim();
        if ( str.equalsIgnoreCase( "TRUE" ) ) {
            return Boolean.TRUE;
        } else if ( str.equalsIgnoreCase( "FALSE" ) ) {
            return Boolean.FALSE;
        } else if ( str.equalsIgnoreCase( "UNKNOWN" ) ) {
            return null;
        } else {
            throw Static.RESOURCE.invalidBoolean( str ).ex();
        }
    }
}

