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
 */

package ch.unibas.dmi.dbis.polyphenydb.sql.utils;


import static org.junit.Assert.assertNotNull;

import ch.unibas.dmi.dbis.polyphenydb.sql.SqlNode;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParserUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParserUtil.StringAndPos;
import ch.unibas.dmi.dbis.polyphenydb.sql.utils.SqlTests.Stage;
import ch.unibas.dmi.dbis.polyphenydb.sql.validate.SqlValidator;
import ch.unibas.dmi.dbis.polyphenydb.test.SqlTestFactory;


/**
 * Tester of {@link SqlValidator} and runtime execution of the input SQL.
 */
public class SqlRuntimeTester extends AbstractSqlTester {

    public SqlRuntimeTester( SqlTestFactory factory ) {
        super( factory );
    }


    @Override
    protected SqlTester with( SqlTestFactory factory ) {
        return new SqlRuntimeTester( factory );
    }


    @Override
    public void checkFails( String expression, String expectedError, boolean runtime ) {
        final String sql = runtime
                ? buildQuery2( expression )
                : buildQuery( expression );
        assertExceptionIsThrown( sql, expectedError, runtime );
    }


    @Override
    public void assertExceptionIsThrown( String sql, String expectedMsgPattern ) {
        assertExceptionIsThrown( sql, expectedMsgPattern, false );
    }


    public void assertExceptionIsThrown( String sql, String expectedMsgPattern, boolean runtime ) {
        final SqlNode sqlNode;
        final StringAndPos sap = SqlParserUtil.findPos( sql );
        try {
            sqlNode = parseQuery( sap.sql );
        } catch ( Throwable e ) {
            checkParseEx( e, expectedMsgPattern, sap.sql );
            return;
        }

        Throwable thrown = null;
        final Stage stage;
        final SqlValidator validator = getValidator();
        if ( runtime ) {
            stage = SqlTests.Stage.RUNTIME;
            SqlNode validated = validator.validate( sqlNode );
            assertNotNull( validated );
            try {
                check( sap.sql, SqlTests.ANY_TYPE_CHECKER, SqlTests.ANY_PARAMETER_CHECKER, SqlTests.ANY_RESULT_CHECKER );
            } catch ( Throwable ex ) {
                // get the real exception in runtime check
                thrown = ex;
            }
        } else {
            stage = SqlTests.Stage.VALIDATE;
            try {
                validator.validate( sqlNode );
            } catch ( Throwable ex ) {
                thrown = ex;
            }
        }

        SqlTests.checkEx( thrown, expectedMsgPattern, sap, stage );
    }
}
