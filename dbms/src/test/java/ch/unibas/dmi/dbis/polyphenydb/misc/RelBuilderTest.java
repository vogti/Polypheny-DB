/*
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
 *
 */

package ch.unibas.dmi.dbis.polyphenydb.misc;


import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import ch.unibas.dmi.dbis.polyphenydb.DataContext.SlimDataContext;
import ch.unibas.dmi.dbis.polyphenydb.TestHelper;
import ch.unibas.dmi.dbis.polyphenydb.TestHelper.JdbcConnection;
import ch.unibas.dmi.dbis.polyphenydb.Transaction;
import ch.unibas.dmi.dbis.polyphenydb.TransactionException;
import ch.unibas.dmi.dbis.polyphenydb.adapter.java.JavaTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.config.RuntimeConfig;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.ContextImpl;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.JavaTypeFactoryImpl;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelTraitDef;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelCollations;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelDistributions;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.JoinRelType;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeField;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexBuilder;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexCorrelVariable;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexInputRef;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexNode;
import ch.unibas.dmi.dbis.polyphenydb.runtime.PolyphenyDbException;
import ch.unibas.dmi.dbis.polyphenydb.schema.PolyphenyDbSchema;
import ch.unibas.dmi.dbis.polyphenydb.schema.SchemaPlus;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlMatchRecognize;
import ch.unibas.dmi.dbis.polyphenydb.sql.fun.SqlStdOperatorTable;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParser.SqlParserConfig;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.SqlTypeName;
import ch.unibas.dmi.dbis.polyphenydb.test.Matchers;
import ch.unibas.dmi.dbis.polyphenydb.tools.FrameworkConfig;
import ch.unibas.dmi.dbis.polyphenydb.tools.Frameworks;
import ch.unibas.dmi.dbis.polyphenydb.tools.Programs;
import ch.unibas.dmi.dbis.polyphenydb.tools.RelBuilder;
import ch.unibas.dmi.dbis.polyphenydb.util.Holder;
import ch.unibas.dmi.dbis.polyphenydb.util.ImmutableBitSet;
import ch.unibas.dmi.dbis.polyphenydb.util.Util;
import ch.unibas.dmi.dbis.polyphenydb.util.mapping.Mappings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matcher;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;


/**
 * Unit test for {@link RelBuilder}.
 */
@Slf4j
public class RelBuilderTest {

    private static Transaction transaction;


    @BeforeClass
    public static void start() {
        // Ensures that Polypheny-DB is running
        //noinspection ResultOfMethodCallIgnored
        TestHelper.getInstance();
        addTestSchema();
        transaction = TestHelper.getInstance().getTransaction();
    }


    @AfterClass
    public static void tearDown() {
        try {
            transaction.commit();
        } catch ( TransactionException e ) {
            throw new RuntimeException( e );
        }
    }


    @SuppressWarnings({ "SqlNoDataSourceInspection", "SqlDialectInspection" })
    private static void addTestSchema() {
        try ( JdbcConnection jdbcConnection = new JdbcConnection() ) {
            Connection connection = jdbcConnection.getConnection();
            try ( Statement statement = connection.createStatement() ) {
                statement.executeUpdate( "CREATE TABLE department( deptno INTEGER NOT NULL, name VARCHAR(20) NOT NULL, loc VARCHAR(50) NULL )" );
                statement.executeUpdate( "CREATE TABLE employee( empid BIGINT NOT NULL, ename VARCHAR(20), job VARCHAR(10), mgr INTEGER, hiredate DATE, salary DECIMAL(7,2), commission DECIMAL(7,2), deptno INTEGER NOT NULL) " );
                connection.commit();
            }
        } catch ( SQLException e ) {
            log.error( "Exception while testing getTables()", e );
        }
    }


    private RelBuilder createRelBuilder() {
        final SchemaPlus rootSchema = transaction.getSchema().plus();
        FrameworkConfig config = Frameworks.newConfigBuilder()
                .parserConfig( SqlParserConfig.DEFAULT )
                .defaultSchema( rootSchema.getSubSchema( transaction.getDefaultSchema().name ) )
                .traitDefs( (List<RelTraitDef>) null )
                .programs( Programs.heuristicJoinOrder( Programs.RULE_SET, true, 2 ) )
                .prepareContext( new ContextImpl(
                        PolyphenyDbSchema.from( rootSchema ),
                        new SlimDataContext() {
                            @Override
                            public JavaTypeFactory getTypeFactory() {
                                return new JavaTypeFactoryImpl();
                            }
                        },
                        "",
                        0,
                        0,
                        null ) ).build();
        return RelBuilder.create( config );
    }


    @Test
    public void testScan() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM employee
        final RelNode root =
                createRelBuilder()
                        .scan( "employee" )
                        .build();
        assertThat( root, Matchers.hasTree( "LogicalTableScan(table=[[public, employee]])\n" ) );
    }


    @Test
    public void testScanQualifiedTable() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM "public"."employee"
        final RelNode root =
                createRelBuilder()
                        .scan( "public", "employee" )
                        .build();
        assertThat( root, Matchers.hasTree( "LogicalTableScan(table=[[public, employee]])\n" ) );
    }


    @Test
    public void testScanInvalidTable() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM zzz
        try {
            final RelNode root =
                    createRelBuilder()
                            .scan( "ZZZ" ) // this relation does not exist
                            .build();
            fail( "expected error, got " + root );
        } catch ( Exception e ) {
            assertThat( e.getMessage(), is( "Table 'ZZZ' not found" ) );
        }
    }


    @Test
    public void testScanInvalidSchema() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM "zzz"."employee"
        try {
            final RelNode root =
                    createRelBuilder()
                            .scan( "ZZZ", "employee" ) // the table exists, but the schema does not
                            .build();
            fail( "expected error, got " + root );
        } catch ( Exception e ) {
            assertThat( e.getMessage(), is( "Table 'ZZZ.employee' not found" ) );
        }
    }


    @Test
    public void testScanInvalidQualifiedTable() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM "public"."zzz"
        try {
            final RelNode root =
                    createRelBuilder()
                            .scan( "public", "ZZZ" ) // the schema is valid, but the table does not exist
                            .build();
            fail( "expected error, got " + root );
        } catch ( Exception e ) {
            assertThat( e.getMessage(), is( "Table 'public.ZZZ' not found" ) );
        }
    }


    @Test
    public void testScanValidTableWrongCase() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM "employee"
        final boolean oldCaseSensitiveValue = RuntimeConfig.CASE_SENSITIVE.getBoolean();
        try {
            RuntimeConfig.CASE_SENSITIVE.setBoolean( true );
            final RelNode root =
                    createRelBuilder()
                            .scan( "EMPLOYEE" ) // the table is named 'employee', not 'EMPLOYEE'
                            .build();
            fail( "Expected error (table names are case-sensitive), but got " + root );
        } catch ( Exception e ) {
            assertThat( e.getMessage(), is( "Table 'EMPLOYEE' not found" ) );
        } finally {
            RuntimeConfig.CASE_SENSITIVE.setBoolean( oldCaseSensitiveValue );
        }
    }


    @Test
    public void testScanFilterTrue() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE TRUE
        final RelBuilder builder = createRelBuilder();
        RelNode root = builder.scan( "employee" )
                .filter( builder.literal( true ) )
                .build();
        assertThat( root, Matchers.hasTree( "LogicalTableScan(table=[[public, employee]])\n" ) );
    }


    @Test
    public void testScanFilterTriviallyFalse() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE 1 = 2
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .filter( builder.equals( builder.literal( 1 ), builder.literal( 2 ) ) )
                        .build();
        assertThat( root, Matchers.hasTree( "LogicalValues(tuples=[[]])\n" ) );
    }


    @Test
    public void testScanFilterEquals() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE deptno = 20
        final RelBuilder builder = createRelBuilder();
        RelNode root = builder.scan( "employee" )
                .filter( builder.equals( builder.field( "deptno" ), builder.literal( 20 ) ) )
                .build();
        final String expected = "LogicalFilter(condition=[=($7, 20)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testScanFilterOr() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE (deptno = 20 OR commission IS NULL) AND mgr IS NOT NULL
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .filter(
                                builder.call(
                                        SqlStdOperatorTable.OR, builder.call( SqlStdOperatorTable.EQUALS, builder.field( "deptno" ), builder.literal( 20 ) ),
                                        builder.isNull( builder.field( 6 ) ) ),
                                builder.isNotNull( builder.field( 3 ) ) )
                        .build();
        final String expected = "LogicalFilter(condition=[AND(OR(=($7, 20), IS NULL($6)), IS NOT NULL($3))])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testScanFilterOr2() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE deptno = 20 OR deptno = 20
        // simplifies to
        //   SELECT *
        //   FROM emp
        //   WHERE deptno = 20
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .filter(
                                builder.call( SqlStdOperatorTable.OR,
                                        builder.call( SqlStdOperatorTable.GREATER_THAN,
                                                builder.field( "deptno" ),
                                                builder.literal( 20 ) ),
                                        builder.call( SqlStdOperatorTable.GREATER_THAN,
                                                builder.field( "deptno" ),
                                                builder.literal( 20 ) ) ) )
                        .build();
        final String expected = "LogicalFilter(condition=[>($7, 20)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testScanFilterAndFalse() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE deptno = 20 AND FALSE
        // simplifies to
        //   VALUES
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .filter(
                                builder.call( SqlStdOperatorTable.GREATER_THAN,
                                        builder.field( "deptno" ),
                                        builder.literal( 20 ) ),
                                builder.literal( false ) )
                        .build();
        final String expected = "LogicalValues(tuples=[[]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testScanFilterAndTrue() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE deptno = 20 AND TRUE
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .filter(
                                builder.call( SqlStdOperatorTable.GREATER_THAN,
                                        builder.field( "deptno" ),
                                        builder.literal( 20 ) ),
                                builder.literal( true ) )
                        .build();
        final String expected = "LogicalFilter(condition=[>($7, 20)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "RelBuilder incorrectly simplifies a filter with duplicate conjunction to empty".
     */
    @Test
    public void testScanFilterDuplicateAnd() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE deptno > 20 AND deptno > 20 AND deptno > 20
        final RelBuilder builder = createRelBuilder();
        builder.scan( "employee" );
        final RexNode condition = builder.call( SqlStdOperatorTable.GREATER_THAN,
                builder.field( "deptno" ),
                builder.literal( 20 ) );
        final RexNode condition2 = builder.call( SqlStdOperatorTable.LESS_THAN,
                builder.field( "deptno" ),
                builder.literal( 30 ) );
        final RelNode root = builder.filter( condition, condition, condition )
                .build();
        final String expected = "LogicalFilter(condition=[>($7, 20)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );

        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   WHERE deptno > 20 AND deptno < 30 AND deptno > 20
        final RelNode root2 = builder.scan( "employee" )
                .filter( condition, condition2, condition, condition )
                .build();
        final String expected2 = ""
                + "LogicalFilter(condition=[AND(>($7, 20), <($7, 30))])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root2, Matchers.hasTree( expected2 ) );
    }


    @Test
    public void testBadFieldName() {
        final RelBuilder builder = createRelBuilder();
        try {
            RexInputRef ref = builder.scan( "employee" ).field( "foo" );
            fail( "expected error, got " + ref );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "field [foo] not found; input fields are: [empid, ename, job, mgr, hiredate, salary, commission, deptno]" ) );
        }
    }


    @Test
    public void testBadFieldOrdinal() {
        final RelBuilder builder = createRelBuilder();
        try {
            RexInputRef ref = builder.scan( "department" ).field( 20 );
            fail( "expected error, got " + ref );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "field ordinal [20] out of range; input fields are: [deptno, name, loc]" ) );
        }
    }


    @Test
    public void testBadType() {
        final RelBuilder builder = createRelBuilder();
        try {
            builder.scan( "employee" );
            RexNode call = builder.call( SqlStdOperatorTable.PLUS, builder.field( 1 ), builder.field( 3 ) );
            fail( "expected error, got " + call );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "Cannot infer return type for +; operand types: [VARCHAR(20), INTEGER]" ) );
        }
    }


    @Test
    public void testProject() {
        // Equivalent SQL:
        //   SELECT deptno, CAST(commission AS SMALLINT) AS commission, 20 AS $f2,
        //     commission AS commission3, commission AS c
        //   FROM emp
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .project( builder.field( "deptno" ),
                                builder.cast( builder.field( 6 ), SqlTypeName.SMALLINT ),
                                builder.literal( 20 ),
                                builder.field( 6 ),
                                builder.alias( builder.field( 6 ), "C" ) )
                        .build();
        // Note: CAST(commission) gets the commission alias because it occurs first
        // Note: AS(commission, C) becomes just $6
        final String expected = ""
                + "LogicalProject(deptno=[$7], commission=[CAST($6):SMALLINT NOT NULL], $f2=[20], commission0=[$6], C=[$6])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Tests each method that creates a scalar expression.
     */
    @Test
    @Ignore
    public void testProject2() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .project( builder.field( "deptno" ),
                                builder.cast( builder.field( 6 ), SqlTypeName.INTEGER ),
                                builder.or(
                                        builder.equals( builder.field( "deptno" ), builder.literal( 20 ) ),
                                        builder.and( builder.literal( null ),
                                                builder.equals( builder.field( "deptno" ), builder.literal( 10 ) ),
                                                builder.and( builder.isNull( builder.field( 6 ) ), builder.not( builder.isNotNull( builder.field( 7 ) ) ) ) ),
                                        builder.equals( builder.field( "deptno" ),
                                                builder.literal( 20 ) ),
                                        builder.equals( builder.field( "deptno" ),
                                                builder.literal( 30 ) ) ),
                                builder.alias( builder.isNull( builder.field( 2 ) ), "n2" ),
                                builder.alias( builder.isNotNull( builder.field( 3 ) ), "nn2" ),
                                builder.literal( 20 ),
                                builder.field( 6 ),
                                builder.alias( builder.field( 6 ), "C" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(deptno=[$7], commission=[CAST($6):INTEGER NOT NULL],"
                + " $f2=[OR(=($7, 20), AND(null:NULL, =($7, 10), IS NULL($6),"
                + " IS NULL($7)), =($7, 30))], n2=[IS NULL($2)],"
                + " nn2=[IS NOT NULL($3)], $f5=[20], commission0=[$6], C=[$6])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testProjectIdentity() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.fields( Mappings.bijection( Arrays.asList( 0, 1, 2 ) ) ) )
                        .build();
        final String expected = "LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "RelBuilder does not translate identity projects even if they rename fields".
     */
    @Test
    public void testProjectIdentityWithFieldsRename() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.alias( builder.field( 0 ), "a" ),
                                builder.alias( builder.field( 1 ), "b" ),
                                builder.alias( builder.field( 2 ), "c" ) )
                        .as( "t1" )
                        .project( builder.field( "a" ), builder.field( "t1", "c" ) )
                        .build();
        final String expected = "LogicalProject(a=[$0], c=[$2])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Variation on {@link #testProjectIdentityWithFieldsRename}: don't use a table alias, and make sure the field names propagate through a filter.
     */
    @Test
    public void testProjectIdentityWithFieldsRenameFilter() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.alias( builder.field( 0 ), "a" ),
                                builder.alias( builder.field( 1 ), "b" ),
                                builder.alias( builder.field( 2 ), "c" ) )
                        .filter(
                                builder.call( SqlStdOperatorTable.EQUALS, builder.field( "a" ), builder.literal( 20 ) ) )
                        .aggregate( builder.groupKey( 0, 1, 2 ),
                                builder.aggregateCall( SqlStdOperatorTable.SUM, builder.field( 0 ) ) )
                        .project( builder.field( "c" ),
                                builder.field( "a" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(c=[$2], a=[$0])\n"
                + "  LogicalAggregate(group=[{0, 1, 2}], agg#0=[SUM($0)])\n"
                + "    LogicalFilter(condition=[=($0, 20)])\n"
                + "      LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testProjectLeadingEdge() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .project( builder.fields( Mappings.bijection( Arrays.asList( 0, 1, 2 ) ) ) )
                        .build();
        final String expected = "LogicalProject(empid=[$0], ename=[$1], job=[$2])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    private void project1( int value, SqlTypeName sqlTypeName, String message, String expected ) {
        final RelBuilder builder = createRelBuilder();
        RexBuilder rex = builder.getRexBuilder();
        RelNode actual =
                builder.values( new String[]{ "x" }, 42 )
                        .empty()
                        .project( rex.makeLiteral( value, rex.getTypeFactory().createSqlType( sqlTypeName ), false ) )
                        .build();
        assertThat( message, actual, Matchers.hasTree( expected ) );
    }


    @Test
    public void testProject1asInt() {
        project1( 1, SqlTypeName.INTEGER,
                "project(1 as INT) might omit type of 1 in the output plan as it is convention to omit INTEGER for integer literals",
                "LogicalProject($f0=[1])\n"
                        + "  LogicalValues(tuples=[[]])\n" );
    }


    @Test
    public void testProject1asBigInt() {
        project1( 1, SqlTypeName.BIGINT, "project(1 as BIGINT) should contain type of 1 in the output plan since the convention is to omit type of INTEGER",
                "LogicalProject($f0=[1:BIGINT])\n"
                        + "  LogicalValues(tuples=[[]])\n" );
    }


    @Test
    public void testRename() {
        final RelBuilder builder = createRelBuilder();

        // No rename necessary (null name is ignored)
        RelNode root =
                builder.scan( "department" )
                        .rename( Arrays.asList( "deptno", null ) )
                        .build();
        final String expected = "LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );

        // No rename necessary (prefix matches)
        root =
                builder.scan( "department" )
                        .rename( ImmutableList.of( "deptno" ) )
                        .build();
        assertThat( root, Matchers.hasTree( expected ) );

        // Add project to rename fields
        root =
                builder.scan( "department" )
                        .rename( Arrays.asList( "NAME", null, "deptno" ) )
                        .build();
        final String expected2 = ""
                + "LogicalProject(NAME=[$0], name=[$1], deptno=[$2])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected2 ) );

        // If our requested list has non-unique names, we might get the same field names we started with. Don't add a useless project.
        root =
                builder.scan( "department" )
                        .rename( Arrays.asList( "deptno", null, "deptno" ) )
                        .build();
        final String expected3 = ""
                + "LogicalProject(deptno=[$0], name=[$1], deptno0=[$2])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected3 ) );
        root =
                builder.scan( "department" )
                        .rename( Arrays.asList( "deptno", null, "deptno" ) )
                        .rename( Arrays.asList( "deptno", null, "deptno" ) )
                        .build();
        // No extra Project
        assertThat( root, Matchers.hasTree( expected3 ) );

        // Name list too long
        try {
            root =
                    builder.scan( "department" )
                            .rename( ImmutableList.of( "NAME", "deptno", "Y", "Z" ) )
                            .build();
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "More names than fields" ) );
        }
    }


    @Test
    public void testRenameValues() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.values( new String[]{ "a", "b" }, true, 1, false, -50 )
                        .build();
        final String expected = "LogicalValues(tuples=[[{ true, 1 }, { false, -50 }]])\n";
        assertThat( root, Matchers.hasTree( expected ) );

        // When you rename Values, you get a Values with a new row type, no Project
        root =
                builder.push( root )
                        .rename( ImmutableList.of( "x", "y z" ) )
                        .build();
        assertThat( root, Matchers.hasTree( expected ) );
        assertThat( root.getRowType().getFieldNames().toString(), is( "[x, y z]" ) );
    }


    @Test
    public void testPermute() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .permute( Mappings.bijection( Arrays.asList( 1, 2, 0 ) ) )
                        .build();
        final String expected = "LogicalProject(job=[$2], empid=[$0], ename=[$1])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testConvert() {
        final RelBuilder builder = createRelBuilder();
        RelDataType rowType =
                builder.getTypeFactory().builder()
                        .add( "a", SqlTypeName.BIGINT )
                        .add( "b", SqlTypeName.VARCHAR, 10 )
                        .add( "c", SqlTypeName.VARCHAR, 10 )
                        .build();
        RelNode root =
                builder.scan( "department" )
                        .convert( rowType, false )
                        .build();
        final String expected = ""
                + "LogicalProject(deptno=[CAST($0):BIGINT NOT NULL], name=[CAST($1):VARCHAR(10) NOT NULL], loc=[CAST($2):VARCHAR(10) NOT NULL])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testConvertRename() {
        final RelBuilder builder = createRelBuilder();
        RelDataType rowType =
                builder.getTypeFactory().builder()
                        .add( "a", SqlTypeName.BIGINT )
                        .add( "b", SqlTypeName.VARCHAR, 10 )
                        .add( "c", SqlTypeName.VARCHAR, 10 )
                        .build();
        RelNode root =
                builder.scan( "department" )
                        .convert( rowType, true )
                        .build();
        final String expected = ""
                + "LogicalProject(a=[CAST($0):BIGINT NOT NULL], b=[CAST($1):VARCHAR(10) NOT NULL], c=[CAST($2):VARCHAR(10) NOT NULL])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAggregate() {
        // Equivalent SQL:
        //   SELECT COUNT(DISTINCT deptno) AS c
        //   FROM emp
        //   GROUP BY ()
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .aggregate( builder.groupKey(), builder.count( true, "C", builder.field( "deptno" ) ) )
                        .build();
        final String expected = ""
                + "LogicalAggregate(group=[{}], C=[COUNT(DISTINCT $7)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAggregate2() {
        // Equivalent SQL:
        //   SELECT COUNT(*) AS c, SUM(mgr + 1) AS s
        //   FROM emp
        //   GROUP BY ename, hiredate + mgr
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .aggregate(
                                builder.groupKey( builder.field( 1 ),
                                        builder.call( SqlStdOperatorTable.PLUS,
                                                builder.field( 4 ),
                                                builder.field( 3 ) ),
                                        builder.field( 1 ) ),
                                builder.countStar( "C" ),
                                builder.sum(
                                        builder.call( SqlStdOperatorTable.PLUS, builder.field( 3 ),
                                                builder.literal( 1 ) ) ).as( "S" ) )
                        .build();
        final String expected = ""
                + "LogicalAggregate(group=[{1, 8}], C=[COUNT()], S=[SUM($9)])\n"
                + "  LogicalProject(empid=[$0], ename=[$1], job=[$2], mgr=[$3], hiredate=[$4], salary=[$5], commission=[$6], deptno=[$7], $f8=[+($4, $3)], $f9=[+($3, 1)])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "RelBuilder wrongly skips creation of Aggregate that prunes columns if input is unique".
     */
    @Test
    public void testAggregate3() {
        // Equivalent SQL:
        //   SELECT DISTINCT deptno FROM (
        //     SELECT deptno, COUNT(*)
        //     FROM emp
        //     GROUP BY deptno)
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .aggregate( builder.groupKey( builder.field( 1 ) ), builder.count().as( "C" ) )
                        .aggregate( builder.groupKey( builder.field( 0 ) ) )
                        .build();
        final String expected = ""
                + "LogicalProject(ename=[$0])\n"
                + "  LogicalAggregate(group=[{1}], C=[COUNT()])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * As {@link #testAggregate3()} but with Filter.
     */
    @Test
    public void testAggregate4() {
        // Equivalent SQL:
        //   SELECT DISTINCT deptno FROM (
        //     SELECT deptno, COUNT(*)
        //     FROM emp
        //     GROUP BY deptno
        //     HAVING COUNT(*) > 3)
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .aggregate(
                                builder.groupKey( builder.field( 1 ) ),
                                builder.count().as( "C" ) )
                        .filter(
                                builder.call( SqlStdOperatorTable.GREATER_THAN, builder.field( 1 ), builder.literal( 3 ) ) )
                        .aggregate(
                                builder.groupKey( builder.field( 0 ) ) )
                        .build();
        final String expected = ""
                + "LogicalProject(ename=[$0])\n"
                + "  LogicalFilter(condition=[>($1, 3)])\n"
                + "    LogicalAggregate(group=[{1}], C=[COUNT()])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAggregateFilter() {
        // Equivalent SQL:
        //   SELECT deptno, COUNT(*) FILTER (WHERE empid > 100) AS c
        //   FROM emp
        //   GROUP BY ROLLUP(deptno)
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .aggregate(
                                builder.groupKey( ImmutableBitSet.of( 7 ), ImmutableList.of( ImmutableBitSet.of( 7 ), ImmutableBitSet.of() ) ),
                                builder.count()
                                        .filter(
                                                builder.call( SqlStdOperatorTable.GREATER_THAN,
                                                        builder.field( "empid" ),
                                                        builder.literal( 100 ) ) )
                                        .as( "C" ) )
                        .build();
        final String expected = ""
                + "LogicalAggregate(group=[{7}], groups=[[{7}, {}]], C=[COUNT() FILTER $8])\n"
                + "  LogicalProject(empid=[$0], ename=[$1], job=[$2], mgr=[$3], hiredate=[$4], salary=[$5], commission=[$6], deptno=[$7], $f8=[>($0, 100)])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAggregateFilterFails() {
        // Equivalent SQL:
        //   SELECT deptno, SUM(salary) FILTER (WHERE commission) AS c
        //   FROM emp
        //   GROUP BY deptno
        try {
            final RelBuilder builder = createRelBuilder();
            RelNode root =
                    builder.scan( "employee" )
                            .aggregate(
                                    builder.groupKey( builder.field( "deptno" ) ),
                                    builder.sum( builder.field( "salary" ) )
                                            .filter( builder.field( "commission" ) )
                                            .as( "C" ) )
                            .build();
            fail( "expected error, got " + root );
        } catch ( PolyphenyDbException e ) {
            assertThat( e.getMessage(),
                    is( "FILTER expression must be of type BOOLEAN" ) );
        }
    }


    @Test
    public void testAggregateFilterNullable() {
        // Equivalent SQL:
        //   SELECT deptno, SUM(salary) FILTER (WHERE commission < 100) AS c
        //   FROM emp
        //   GROUP BY deptno
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .aggregate(
                                builder.groupKey( builder.field( "deptno" ) ),
                                builder.sum( builder.field( "salary" ) )
                                        .filter( builder.call( SqlStdOperatorTable.LESS_THAN, builder.field( "commission" ), builder.literal( 100 ) ) )
                                        .as( "C" ) )
                        .build();
        final String expected = ""
                + "LogicalAggregate(group=[{7}], C=[SUM($5) FILTER $8])\n"
                + "  LogicalProject(empid=[$0], ename=[$1], job=[$2], mgr=[$3], hiredate=[$4], salary=[$5], commission=[$6], deptno=[$7], $f8=[IS TRUE(<($6, 100))])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "RelBuilder gives NPE if groupKey contains alias".
     *
     * Now, the alias does not cause a new expression to be added to the input, but causes the referenced fields to be renamed.
     */
    @Test
    public void testAggregateProjectWithAliases() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .project( builder.field( "deptno" ) )
                        .aggregate( builder.groupKey( builder.alias( builder.field( "deptno" ), "departmentNo" ) ) )
                        .build();
        final String expected = ""
                + "LogicalAggregate(group=[{0}])\n"
                + "  LogicalProject(departmentNo=[$7])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAggregateProjectWithExpression() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .project( builder.field( "deptno" ) )
                        .aggregate(
                                builder.groupKey(
                                        builder.alias(
                                                builder.call( SqlStdOperatorTable.PLUS, builder.field( "deptno" ), builder.literal( 3 ) ),
                                                "d3" ) ) )
                        .build();
        final String expected = ""
                + "LogicalAggregate(group=[{1}])\n"
                + "  LogicalProject(deptno=[$7], d3=[+($7, 3)])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAggregateGroupingKeyOutOfRangeFails() {
        final RelBuilder builder = createRelBuilder();
        try {
            RelNode root =
                    builder.scan( "employee" )
                            .aggregate( builder.groupKey( ImmutableBitSet.of( 17 ) ) )
                            .build();
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "out of bounds: {17}" ) );
        }
    }


    @Test
    public void testAggregateGroupingSetNotSubsetFails() {
        final RelBuilder builder = createRelBuilder();
        try {
            RelNode root =
                    builder.scan( "employee" )
                            .aggregate( builder.groupKey( ImmutableBitSet.of( 7 ), ImmutableList.of( ImmutableBitSet.of( 4 ), ImmutableBitSet.of() ) ) )
                            .build();
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "group set element [$4] must be a subset of group key" ) );
        }
    }


    @Test
    public void testAggregateGroupingSetDuplicateIgnored() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .aggregate(
                                builder.groupKey(
                                        ImmutableBitSet.of( 7, 6 ),
                                        ImmutableList.of( ImmutableBitSet.of( 7 ), ImmutableBitSet.of( 6 ), ImmutableBitSet.of( 7 ) ) ) )
                        .build();
        final String expected = ""
                + "LogicalAggregate(group=[{6, 7}], groups=[[{6}, {7}]])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAggregateGrouping() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .aggregate(
                                builder.groupKey( 6, 7 ),
                                builder.aggregateCall( SqlStdOperatorTable.GROUPING, builder.field( "deptno" ) ).as( "g" ) )
                        .build();
        final String expected = ""
                + "LogicalAggregate(group=[{6, 7}], g=[GROUPING($7)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAggregateGroupingWithDistinctFails() {
        final RelBuilder builder = createRelBuilder();
        try {
            RelNode root =
                    builder.scan( "employee" )
                            .aggregate( builder.groupKey( 6, 7 ),
                                    builder.aggregateCall( SqlStdOperatorTable.GROUPING, builder.field( "deptno" ) )
                                            .distinct( true )
                                            .as( "g" ) )
                            .build();
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "DISTINCT not allowed" ) );
        }
    }


    @Test
    public void testAggregateGroupingWithFilterFails() {
        final RelBuilder builder = createRelBuilder();
        try {
            RelNode root =
                    builder.scan( "employee" )
                            .aggregate( builder.groupKey( 6, 7 ),
                                    builder.aggregateCall( SqlStdOperatorTable.GROUPING,
                                            builder.field( "deptno" ) )
                                            .filter( builder.literal( true ) )
                                            .as( "g" ) )
                            .build();
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "FILTER not allowed" ) );
        }
    }


    @Test
    public void testDistinct() {
        // Equivalent SQL:
        //   SELECT DISTINCT deptno
        //   FROM emp
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .project( builder.field( "deptno" ) )
                        .distinct()
                        .build();
        final String expected = "LogicalAggregate(group=[{0}])\n"
                + "  LogicalProject(deptno=[$7])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    @Ignore
    public void testDistinctAlready() {
        // department is already distinct
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .distinct()
                        .build();
        final String expected = "LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testDistinctEmpty() {
        // Is a relation with zero columns distinct? What about if we know there are zero rows? It is a matter of definition: there are no duplicate rows, but applying "select ... group by ()" to it would change the result.
        // In theory, we could omit the distinct if we know there is precisely one row, but we don't currently.
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .filter( builder.call( SqlStdOperatorTable.IS_NULL, builder.field( "commission" ) ) )
                        .project()
                        .distinct()
                        .build();
        final String expected = "LogicalAggregate(group=[{}])\n"
                + "  LogicalProject\n"
                + "    LogicalFilter(condition=[IS NULL($6)])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testUnion() {
        // Equivalent SQL:
        //   SELECT deptno FROM emp
        //   UNION ALL
        //   SELECT deptno FROM dept
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.field( "deptno" ) )
                        .scan( "employee" )
                        .filter( builder.call( SqlStdOperatorTable.EQUALS, builder.field( "deptno" ), builder.literal( 20 ) ) )
                        .project( builder.field( "empid" ) )
                        .union( true )
                        .build();
        final String expected = ""
                + "LogicalUnion(all=[true])\n"
                + "  LogicalProject(deptno=[$0])\n"
                + "    LogicalTableScan(table=[[public, department]])\n"
                + "  LogicalProject(empid=[$0])\n"
                + "    LogicalFilter(condition=[=($7, 20)])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for SetOps with incompatible args
     */
    @Test
    public void testBadUnionArgsErrorMessage() {
        // Equivalent SQL:
        //   SELECT empid, SALARY FROM emp
        //   UNION ALL
        //   SELECT deptno FROM dept
        final RelBuilder builder = createRelBuilder();
        try {
            final RelNode root =
                    builder.scan( "department" )
                            .project( builder.field( "deptno" ) )
                            .scan( "employee" )
                            .project( builder.field( "empid" ), builder.field( "salary" ) )
                            .union( true )
                            .build();
            fail( "Expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            final String expected = "Cannot compute compatible row type for arguments to set op: RecordType(INTEGER deptno), RecordType(BIGINT empid, DECIMAL(7, 2) salary)";
            assertThat( e.getMessage(), is( expected ) );
        }
    }


    @Test
    public void testUnion3() {
        // Equivalent SQL:
        //   SELECT deptno FROM dept
        //   UNION ALL
        //   SELECT empid FROM emp
        //   UNION ALL
        //   SELECT deptno FROM emp
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.field( "deptno" ) )
                        .scan( "employee" )
                        .project( builder.field( "empid" ) )
                        .scan( "employee" )
                        .project( builder.field( "deptno" ) )
                        .union( true, 3 )
                        .build();
        final String expected = ""
                + "LogicalUnion(all=[true])\n"
                + "  LogicalProject(deptno=[$0])\n"
                + "    LogicalTableScan(table=[[public, department]])\n"
                + "  LogicalProject(empid=[$0])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n"
                + "  LogicalProject(deptno=[$7])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testUnion1() {
        // Equivalent SQL:
        //   SELECT deptno FROM dept
        //   UNION ALL
        //   SELECT empid FROM emp
        //   UNION ALL
        //   SELECT deptno FROM emp
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.field( "deptno" ) )
                        .scan( "employee" )
                        .project( builder.field( "empid" ) )
                        .scan( "employee" )
                        .project( builder.field( "deptno" ) )
                        .union( true, 1 )
                        .build();
        final String expected = "LogicalProject(deptno=[$7])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testIntersect() {
        // Equivalent SQL:
        //   SELECT empid FROM emp
        //   WHERE deptno = 20
        //   INTERSECT
        //   SELECT deptno FROM dept
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.field( "deptno" ) )
                        .scan( "employee" )
                        .filter(
                                builder.call( SqlStdOperatorTable.EQUALS,
                                        builder.field( "deptno" ),
                                        builder.literal( 20 ) ) )
                        .project( builder.field( "empid" ) )
                        .intersect( false )
                        .build();
        final String expected = ""
                + "LogicalIntersect(all=[false])\n"
                + "  LogicalProject(deptno=[$0])\n"
                + "    LogicalTableScan(table=[[public, department]])\n"
                + "  LogicalProject(empid=[$0])\n"
                + "    LogicalFilter(condition=[=($7, 20)])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testIntersect3() {
        // Equivalent SQL:
        //   SELECT deptno FROM dept
        //   INTERSECT ALL
        //   SELECT empid FROM emp
        //   INTERSECT ALL
        //   SELECT deptno FROM emp
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.field( "deptno" ) )
                        .scan( "employee" )
                        .project( builder.field( "empid" ) )
                        .scan( "employee" )
                        .project( builder.field( "deptno" ) )
                        .intersect( true, 3 )
                        .build();
        final String expected = ""
                + "LogicalIntersect(all=[true])\n"
                + "  LogicalProject(deptno=[$0])\n"
                + "    LogicalTableScan(table=[[public, department]])\n"
                + "  LogicalProject(empid=[$0])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n"
                + "  LogicalProject(deptno=[$7])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testExcept() {
        // Equivalent SQL:
        //   SELECT empid FROM emp
        //   WHERE deptno = 20
        //   MINUS
        //   SELECT deptno FROM dept
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project( builder.field( "deptno" ) )
                        .scan( "employee" )
                        .filter(
                                builder.call( SqlStdOperatorTable.EQUALS,
                                        builder.field( "deptno" ),
                                        builder.literal( 20 ) ) )
                        .project( builder.field( "empid" ) )
                        .minus( false )
                        .build();
        final String expected = ""
                + "LogicalMinus(all=[false])\n"
                + "  LogicalProject(deptno=[$0])\n"
                + "    LogicalTableScan(table=[[public, department]])\n"
                + "  LogicalProject(empid=[$0])\n"
                + "    LogicalFilter(condition=[=($7, 20)])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testJoin() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM (SELECT * FROM employee WHERE commission IS NULL)
        //   JOIN dept ON emp.deptno = dept.deptno
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .filter(
                                builder.call( SqlStdOperatorTable.IS_NULL,
                                        builder.field( "commission" ) ) )
                        .scan( "department" )
                        .join( JoinRelType.INNER,
                                builder.call( SqlStdOperatorTable.EQUALS,
                                        builder.field( 2, 0, "deptno" ),
                                        builder.field( 2, 1, "deptno" ) ) )
                        .build();
        final String expected = ""
                + "LogicalJoin(condition=[=($7, $8)], joinType=[inner])\n"
                + "  LogicalFilter(condition=[IS NULL($6)])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Same as {@link #testJoin} using USING.
     */
    @Test
    public void testJoinUsing() {
        final RelBuilder builder = createRelBuilder();
        final RelNode root2 =
                builder.scan( "employee" )
                        .filter( builder.call( SqlStdOperatorTable.IS_NULL, builder.field( "commission" ) ) )
                        .scan( "department" )
                        .join( JoinRelType.INNER, "deptno" )
                        .build();
        final String expected = ""
                + "LogicalJoin(condition=[=($7, $8)], joinType=[inner])\n"
                + "  LogicalFilter(condition=[IS NULL($6)])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root2, Matchers.hasTree( expected ) );
    }


    @Test
    public void testJoin2() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   LEFT JOIN dept ON emp.deptno = dept.deptno
        //     AND emp.empid = 123
        //     AND dept.deptno IS NOT NULL
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .scan( "department" )
                        .join( JoinRelType.LEFT,
                                builder.call( SqlStdOperatorTable.EQUALS,
                                        builder.field( 2, 0, "deptno" ),
                                        builder.field( 2, 1, "deptno" ) ),
                                builder.call( SqlStdOperatorTable.EQUALS,
                                        builder.field( 2, 0, "empid" ),
                                        builder.literal( 123 ) ),
                                builder.call( SqlStdOperatorTable.IS_NOT_NULL,
                                        builder.field( 2, 1, "deptno" ) ) )
                        .build();
        // Note that "dept.deptno IS NOT NULL" has been simplified away.
        final String expected = ""
                + "LogicalJoin(condition=[AND(=($7, $8), =($0, 123))], joinType=[left])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testJoinCartesian() {
        // Equivalent SQL:
        //   SELECT * employee CROSS JOIN dept
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .scan( "department" )
                        .join( JoinRelType.INNER )
                        .build();
        final String expected =
                "LogicalJoin(condition=[true], joinType=[inner])\n"
                        + "  LogicalTableScan(table=[[public, employee]])\n"
                        + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testCorrelationFails() {
        final RelBuilder builder = createRelBuilder();
        final Holder<RexCorrelVariable> v = Holder.of( null );
        try {
            builder.scan( "employee" )
                    .variable( v )
                    .filter( builder.equals( builder.field( 0 ), v.get() ) )
                    .scan( "department" )
                    .join( JoinRelType.INNER, builder.literal( true ),
                            ImmutableSet.of( v.get().id ) );
            fail( "expected error" );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(),
                    containsString( "variable $cor0 must not be used by left input to correlation" ) );
        }
    }


    @Test
    public void testCorrelationWithCondition() {
        final RelBuilder builder = createRelBuilder();
        final Holder<RexCorrelVariable> v = Holder.of( null );
        RelNode root = builder.scan( "employee" )
                .variable( v )
                .scan( "department" )
                .filter( builder.equals( builder.field( 0 ), builder.field( v.get(), "deptno" ) ) )
                .join( JoinRelType.LEFT,
                        builder.equals( builder.field( 2, 0, "salary" ), builder.literal( 1000 ) ),
                        ImmutableSet.of( v.get().id ) )
                .build();
        // Note that the join filter gets pushed to the right-hand input of LogicalCorrelate
        final String expected = ""
                + "LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n"
                + "  LogicalFilter(condition=[=($cor0.salary, 1000)])\n"
                + "    LogicalFilter(condition=[=($0, $cor0.deptno)])\n"
                + "      LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAlias() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM employee AS e, dept
        //   WHERE e.deptno = department.deptno
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "e" )
                        .scan( "department" )
                        .join( JoinRelType.LEFT )
                        .filter( builder.equals( builder.field( "e", "deptno" ), builder.field( "department", "deptno" ) ) )
                        .project( builder.field( "e", "ename" ), builder.field( "department", "name" ) )
                        .build();
        final String expected = "LogicalProject(ename=[$1], name=[$9])\n"
                + "  LogicalFilter(condition=[=($7, $8)])\n"
                + "    LogicalJoin(condition=[true], joinType=[left])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n"
                + "      LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
        final RelDataTypeField field = root.getRowType().getFieldList().get( 1 );
        assertThat( field.getName(), is( "name" ) );
        assertThat( field.getType().isNullable(), is( true ) );
    }


    @Test
    public void testAlias2() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM employee AS e, employee as m, dept
        //   WHERE e.deptno = dept.deptno
        //   AND m.empid = e.mgr
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "e" )
                        .scan( "employee" )
                        .as( "m" )
                        .scan( "department" )
                        .join( JoinRelType.INNER )
                        .join( JoinRelType.INNER )
                        .filter(
                                builder.equals( builder.field( "e", "deptno" ), builder.field( "department", "deptno" ) ),
                                builder.equals( builder.field( "m", "empid" ), builder.field( "e", "mgr" ) ) )
                        .build();
        final String expected = ""
                + "LogicalFilter(condition=[AND(=($7, $16), =($8, $3))])\n"
                + "  LogicalJoin(condition=[true], joinType=[inner])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n"
                + "    LogicalJoin(condition=[true], joinType=[inner])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n"
                + "      LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAliasSort() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "e" )
                        .sort( 0 )
                        .project( builder.field( "e", "empid" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(empid=[$0])\n"
                + "  LogicalSort(sort0=[$0], dir0=[ASC])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAliasLimit() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "e" )
                        .sort( 1 )
                        .sortLimit( 10, 20 ) // aliases were lost here if preceded by sort()
                        .project( builder.field( "e", "empid" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(empid=[$0])\n"
                + "  LogicalSort(sort0=[$1], dir0=[ASC], offset=[10], fetch=[20])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "RelBuilder's project() doesn't preserve alias".
     */
    @Test
    public void testAliasProject() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "employee_alias" )
                        .project( builder.field( "deptno" ), builder.literal( 20 ) )
                        .project( builder.field( "employee_alias", "deptno" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(deptno=[$7])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Tests that table aliases are propagated even when there is a project on top of a project. (Aliases tend to get lost when projects are merged).
     */
    @Test
    public void testAliasProjectProject() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "employee_alias" )
                        .project(
                                builder.field( "deptno" ),
                                builder.literal( 20 ) )
                        .project(
                                builder.field( 1 ),
                                builder.literal( 10 ),
                                builder.field( 0 ) )
                        .project( builder.alias(
                                builder.field( 1 ), "sum" ),
                                builder.field( "employee_alias", "deptno" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(sum=[10], deptno=[$7])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Tests that table aliases are propagated and are available to a filter, even when there is a project on top of a project. (Aliases tend to get lost when projects are merged).
     */
    @Test
    public void testAliasFilter() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "employee_alias" )
                        .project(
                                builder.field( "deptno" ),
                                builder.literal( 20 ) )
                        .project(
                                builder.field( 1 ), // literal 20
                                builder.literal( 10 ),
                                builder.field( 0 ) ) // deptno
                        .filter(
                                builder.call(
                                        SqlStdOperatorTable.GREATER_THAN,
                                        builder.field( 1 ),
                                        builder.field( "employee_alias", "deptno" ) ) )
                        .build();
        final String expected = ""
                + "LogicalFilter(condition=[>($1, $2)])\n"
                + "  LogicalProject($f1=[20], $f12=[10], deptno=[$7])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testAliasAggregate() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "employee_alias" )
                        .project(
                                builder.field( "deptno" ),
                                builder.literal( 20 ) )
                        .aggregate(
                                builder.groupKey( builder.field( "employee_alias", "deptno" ) ),
                                builder.sum( builder.field( 1 ) ) )
                        .project(
                                builder.alias( builder.field( 1 ), "sum" ),
                                builder.field( "employee_alias", "deptno" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(sum=[$1], deptno=[$0])\n"
                + "  LogicalAggregate(group=[{0}], agg#0=[SUM($1)])\n"
                + "    LogicalProject(deptno=[$7], $f1=[20])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Tests that a projection retains field names after a join.
     */
    @Test
    public void testProjectJoin() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "e" )
                        .scan( "department" )
                        .join( JoinRelType.INNER )
                        .project(
                                builder.field( "department", "deptno" ),
                                builder.field( 0 ),
                                builder.field( "e", "mgr" ) )
                        // essentially a no-op, was previously throwing exception due to project() using join-renamed fields
                        .project(
                                builder.field( "department", "deptno" ),
                                builder.field( 1 ),
                                builder.field( "e", "mgr" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(deptno=[$8], empid=[$0], mgr=[$3])\n"
                + "  LogicalJoin(condition=[true], joinType=[inner])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n"
                + "    LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Tests that a projection after a projection.
     */
    @Test
    public void testProjectProject() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "e" )
                        .projectPlus(
                                builder.alias(
                                        builder.call(
                                                SqlStdOperatorTable.PLUS,
                                                builder.field( 0 ),
                                                builder.field( 3 ) ),
                                        "x" ) )
                        .project(
                                builder.field( "e", "deptno" ),
                                builder.field( 0 ),
                                builder.field( "e", "mgr" ),
                                Util.last( builder.fields() ) )
                        .build();
        final String expected = ""
                + "LogicalProject(deptno=[$7], empid=[$0], mgr=[$3], x=[+($0, $3)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testMultiLevelAlias() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "e" )
                        .scan( "employee" )
                        .as( "m" )
                        .scan( "department" )
                        .join( JoinRelType.INNER )
                        .join( JoinRelType.INNER )
                        .project(
                                builder.field( "department", "deptno" ),
                                builder.field( 16 ),
                                builder.field( "m", "empid" ),
                                builder.field( "e", "mgr" ) )
                        .as( "all" )
                        .filter(
                                builder.call(
                                        SqlStdOperatorTable.GREATER_THAN,
                                        builder.field( "department", "deptno" ),
                                        builder.literal( 100 ) ) )
                        .project(
                                builder.field( "department", "deptno" ),
                                builder.field( "all", "empid" ) )
                        .build();
        final String expected = ""
                + "LogicalProject(deptno=[$0], empid=[$2])\n"
                + "  LogicalFilter(condition=[>($0, 100)])\n"
                + "    LogicalProject(deptno=[$16], deptno0=[$16], empid=[$8], mgr=[$3])\n"
                + "      LogicalJoin(condition=[true], joinType=[inner])\n"
                + "        LogicalTableScan(table=[[public, employee]])\n"
                + "        LogicalJoin(condition=[true], joinType=[inner])\n"
                + "          LogicalTableScan(table=[[public, employee]])\n"
                + "          LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testUnionAlias() {
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .as( "e1" )
                        .project(
                                builder.field( "empid" ),
                                builder.call(
                                        SqlStdOperatorTable.CONCAT,
                                        builder.field( "ename" ),
                                        builder.literal( "-1" ) ) )
                        .scan( "employee" )
                        .as( "e2" )
                        .project(
                                builder.field( "empid" ),
                                builder.call(
                                        SqlStdOperatorTable.CONCAT,
                                        builder.field( "ename" ),
                                        builder.literal( "-2" ) ) )
                        .union( false ) // aliases lost here
                        .project( builder.fields( Lists.newArrayList( 1, 0 ) ) )
                        .build();
        final String expected = ""
                + "LogicalProject($f1=[$1], empid=[$0])\n"
                + "  LogicalUnion(all=[false])\n"
                + "    LogicalProject(empid=[$0], $f1=[||($1, '-1')])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n"
                + "    LogicalProject(empid=[$0], $f1=[||($1, '-2')])\n"
                + "      LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "Add RelBuilder field() method to reference aliased relations not on top of stack", accessing tables aliased that are not accessible in the top RelNode.
     */
    @Test
    public void testAliasPastTop() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   LEFT JOIN dept ON emp.deptno = dept.deptno
        //     AND emp.empid = 123
        //     AND dept.deptno IS NOT NULL
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" )
                        .scan( "department" )
                        .join( JoinRelType.LEFT,
                                builder.call(
                                        SqlStdOperatorTable.EQUALS,
                                        builder.field( 2, "employee", "deptno" ),
                                        builder.field( 2, "department", "deptno" ) ),
                                builder.call(
                                        SqlStdOperatorTable.EQUALS,
                                        builder.field( 2, "employee", "empid" ),
                                        builder.literal( 123 ) ) )
                        .build();
        final String expected = ""
                + "LogicalJoin(condition=[AND(=($7, $8), =($0, 123))], joinType=[left])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * As {@link #testAliasPastTop()}.
     */
    @Test
    public void testAliasPastTop2() {
        // Equivalent SQL:
        //   SELECT t1.empid, t2.empid, t3.deptno
        //   FROM employee t1
        //   INNER JOIN employee t2 ON t1.empid = t2.empid
        //   INNER JOIN dept t3 ON t1.deptno = t3.deptno
        //     AND t2.job != t3.loc
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "employee" ).as( "t1" )
                        .scan( "employee" ).as( "t2" )
                        .join(
                                JoinRelType.INNER,
                                builder.equals(
                                        builder.field( 2, "t1", "empid" ),
                                        builder.field( 2, "t2", "empid" ) ) )
                        .scan( "department" ).as( "t3" )
                        .join(
                                JoinRelType.INNER,
                                builder.equals(
                                        builder.field( 2, "t1", "deptno" ),
                                        builder.field( 2, "t3", "deptno" ) ),
                                builder.not(
                                        builder.equals(
                                                builder.field( 2, "t2", "job" ),
                                                builder.field( 2, "t3", "loc" ) ) ) )
                        .build();
        // Cols:
        // 0-7   employee as t1
        // 8-15  employee as t2
        // 16-18 department as t3
        final String expected = ""
                + "LogicalJoin(condition=[AND(=($7, $16), <>($10, $18))], joinType=[inner])\n"
                + "  LogicalJoin(condition=[=($0, $8)], joinType=[inner])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n"
                + "  LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testEmpty() {
        // Equivalent SQL:
        //   SELECT deptno, true FROM dept LIMIT 0
        // optimized to
        //   VALUES
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.scan( "department" )
                        .project(
                                builder.field( 0 ),
                                builder.literal( false ) )
                        .empty()
                        .build();
        final String expected = "LogicalValues(tuples=[[]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
        final String expectedType = "RecordType(INTEGER NOT NULL deptno, BOOLEAN NOT NULL $f1) NOT NULL";
        assertThat( root.getRowType().getFullTypeString(), is( expectedType ) );
    }


    @Test
    public void testValues() {
        // Equivalent SQL:
        //   VALUES (true, 1), (false, -50) AS t(a, b)
        final RelBuilder builder = createRelBuilder();
        RelNode root = builder.values( new String[]{ "a", "b" }, true, 1, false, -50 ).build();
        final String expected = "LogicalValues(tuples=[[{ true, 1 }, { false, -50 }]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
        final String expectedType = "RecordType(BOOLEAN NOT NULL a, INTEGER NOT NULL b) NOT NULL";
        assertThat( root.getRowType().getFullTypeString(), is( expectedType ) );
    }


    /**
     * Tests creating Values with some field names and some values null.
     */
    @Test
    public void testValuesNullable() {
        // Equivalent SQL:
        //   VALUES (null, 1, 'abc'), (false, null, 'longer string')
        final RelBuilder builder = createRelBuilder();
        RelNode root =
                builder.values( new String[]{ "a", null, "c" }, null, 1, "abc", false, null, "longer string" ).build();
        final String expected = "LogicalValues(tuples=[[{ null, 1, 'abc' }, { false, null, 'longer string' }]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
        final String expectedType = "RecordType(BOOLEAN a, INTEGER expr$1, CHAR(13) NOT NULL c) NOT NULL";
        assertThat( root.getRowType().getFullTypeString(), is( expectedType ) );
    }


    @Test
    public void testValuesBadNullFieldNames() {
        try {
            final RelBuilder builder = createRelBuilder();
            RelBuilder root = builder.values( (String[]) null, "a", "b" );
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "Value count must be a positive multiple of field count" ) );
        }
    }


    @Test
    public void testValuesBadNoFields() {
        try {
            final RelBuilder builder = createRelBuilder();
            RelBuilder root = builder.values( new String[0], 1, 2, 3 );
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "Value count must be a positive multiple of field count" ) );
        }
    }


    @Test
    public void testValuesBadNoValues() {
        try {
            final RelBuilder builder = createRelBuilder();
            RelBuilder root = builder.values( new String[]{ "a", "b" } );
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "Value count must be a positive multiple of field count" ) );
        }
    }


    @Test
    public void testValuesBadOddMultiple() {
        try {
            final RelBuilder builder = createRelBuilder();
            RelBuilder root = builder.values( new String[]{ "a", "b" }, 1, 2, 3, 4, 5 );
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "Value count must be a positive multiple of field count" ) );
        }
    }


    @Test
    public void testValuesBadAllNull() {
        try {
            final RelBuilder builder = createRelBuilder();
            RelBuilder root = builder.values( new String[]{ "a", "b" }, null, null, 1, null );
            fail( "expected error, got " + root );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), is( "All values of field 'b' are null; cannot deduce type" ) );
        }
    }


    @Test
    public void testValuesAllNull() {
        final RelBuilder builder = createRelBuilder();
        RelDataType rowType =
                builder.getTypeFactory().builder()
                        .add( "a", SqlTypeName.BIGINT )
                        .add( "a", SqlTypeName.VARCHAR, 10 )
                        .build();
        RelNode root = builder.values( rowType, null, null, 1, null ).build();
        final String expected = "LogicalValues(tuples=[[{ null, null }, { 1, null }]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
        final String expectedType = "RecordType(BIGINT NOT NULL a, VARCHAR(10) NOT NULL a) NOT NULL";
        assertThat( root.getRowType().getFullTypeString(), is( expectedType ) );
    }


    @Test
    public void testSort() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   ORDER BY 3. 1 DESC
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "employee" )
                        .sort( builder.field( 2 ), builder.desc( builder.field( 0 ) ) )
                        .build();
        final String expected = "LogicalSort(sort0=[$2], sort1=[$0], dir0=[ASC], dir1=[DESC])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );

        // same result using ordinals
        final RelNode root2 = builder.scan( "employee" ).sort( 2, -1 ).build();
        assertThat( root2, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "OFFSET 0 causes AssertionError".
     */
    @Test
    public void testTrivialSort() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   OFFSET 0
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "employee" )
                        .sortLimit( 0, -1, ImmutableList.of() )
                        .build();
        final String expected = "LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testSortDuplicate() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   ORDER BY empid DESC, deptno, empid ASC, hiredate
        //
        // The sort key "empid ASC" is unnecessary and is ignored.
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "employee" )
                        .sort(
                                builder.desc( builder.field( "empid" ) ),
                                builder.field( "deptno" ),
                                builder.field( "empid" ),
                                builder.field( "hiredate" ) )
                        .build();
        final String expected = "LogicalSort(sort0=[$0], sort1=[$7], sort2=[$4], dir0=[DESC], dir1=[ASC], dir2=[ASC])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testSortByExpression() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   ORDER BY ename ASC NULLS LAST, hiredate + mgr DESC NULLS FIRST
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "employee" )
                        .sort( builder.nullsLast( builder.desc( builder.field( 1 ) ) ),
                                builder.nullsFirst(
                                        builder.call(
                                                SqlStdOperatorTable.PLUS,
                                                builder.field( 4 ),
                                                builder.field( 3 ) ) ) )
                        .build();
        final String expected =
                "LogicalProject(empid=[$0], ename=[$1], job=[$2], mgr=[$3], hiredate=[$4], salary=[$5], commission=[$6], deptno=[$7])\n"
                        + "  LogicalSort(sort0=[$1], sort1=[$8], dir0=[DESC-nulls-last], dir1=[ASC-nulls-first])\n"
                        + "    LogicalProject(empid=[$0], ename=[$1], job=[$2], mgr=[$3], hiredate=[$4], salary=[$5], commission=[$6], deptno=[$7], $f8=[+($4, $3)])\n"
                        + "      LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testLimit() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   OFFSET 2 FETCH 10
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "employee" )
                        .limit( 2, 10 )
                        .build();
        final String expected =
                "LogicalSort(offset=[2], fetch=[10])\n"
                        + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testSortLimit() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   ORDER BY deptno DESC FETCH 10
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "employee" )
                        .sortLimit( -1, 10, builder.desc( builder.field( "deptno" ) ) )
                        .build();
        final String expected =
                "LogicalSort(sort0=[$7], dir0=[DESC], fetch=[10])\n"
                        + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testSortLimit0() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   ORDER BY deptno DESC FETCH 0
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "employee" )
                        .sortLimit( -1, 0, builder.desc( builder.field( "deptno" ) ) )
                        .build();
        final String expected = "LogicalValues(tuples=[[]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "RelBuilder sort-combining optimization treats aliases incorrectly".
     */
    @Test
    public void testSortOverProjectSort() {
        final RelBuilder builder = createRelBuilder();
        builder.scan( "employee" )
                .sort( 0 )
                .project( builder.field( 1 ) )
                // was throwing exception here when attempting to apply to inner sort node
                .limit( 0, 1 )
                .build();
        RelNode root = builder.scan( "employee" )
                .sort( 0 )
                .project( Lists.newArrayList( builder.field( 1 ) ), Lists.newArrayList( "F1" ) )
                .limit( 0, 1 )
                // make sure we can still access the field by alias
                .project( builder.field( "F1" ) )
                .build();
        String expected = "LogicalProject(F1=[$1])\n"
                + "  LogicalSort(sort0=[$0], dir0=[ASC], fetch=[1])\n"
                + "    LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    /**
     * Tests that a sort on a field followed by a limit gives the same effect as calling sortLimit.
     *
     * In general a relational operator cannot rely on the order of its input, but it is reasonable to merge sort and limit if they were created by consecutive builder operations. And clients such as Piglet rely on it.
     */
    @Test
    public void testSortThenLimit() {
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "employee" )
                        .sort( builder.desc( builder.field( "deptno" ) ) )
                        .limit( -1, 10 )
                        .build();
        final String expected = ""
                + "LogicalSort(sort0=[$7], dir0=[DESC], fetch=[10])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );

        final RelNode root2 =
                builder.scan( "employee" )
                        .sortLimit( -1, 10, builder.desc( builder.field( "deptno" ) ) )
                        .build();
        assertThat( root2, Matchers.hasTree( expected ) );
    }


    /**
     * Tests that a sort on an expression followed by a limit gives the same effect as calling sortLimit.
     */
    @Test
    public void testSortExpThenLimit() {
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "department" )
                        .sort(
                                builder.desc(
                                        builder.call(
                                                SqlStdOperatorTable.PLUS,
                                                builder.field( "deptno" ),
                                                builder.literal( 1 ) ) ) )
                        .limit( 3, 10 )
                        .build();
        final String expected = ""
                + "LogicalProject(deptno=[$0], name=[$1], loc=[$2])\n"
                + "  LogicalSort(sort0=[$3], dir0=[DESC], offset=[3], fetch=[10])\n"
                + "    LogicalProject(deptno=[$0], name=[$1], loc=[$2], $f3=[+($0, 1)])\n"
                + "      LogicalTableScan(table=[[public, department]])\n";
        assertThat( root, Matchers.hasTree( expected ) );

        final RelNode root2 =
                builder.scan( "department" )
                        .sortLimit( 3, 10,
                                builder.desc(
                                        builder.call(
                                                SqlStdOperatorTable.PLUS,
                                                builder.field( "deptno" ),
                                                builder.literal( 1 ) ) ) )
                        .build();
        assertThat( root2, Matchers.hasTree( expected ) );
    }


    /**
     * Test case for "RelBuilder.call throws NullPointerException if argument types are invalid".
     */
    @Test
    public void testTypeInferenceValidation() {
        final RelBuilder builder = createRelBuilder();
        // test for a) call(operator, Iterable<RexNode>)
        final RexNode arg0 = builder.literal( 0 );
        final RexNode arg1 = builder.literal( "xyz" );
        try {
            builder.call( SqlStdOperatorTable.PLUS, Lists.newArrayList( arg0, arg1 ) );
            fail( "Invalid combination of parameter types" );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), containsString( "Cannot infer return type" ) );
        }

        // test for b) call(operator, RexNode...)
        try {
            builder.call( SqlStdOperatorTable.PLUS, arg0, arg1 );
            fail( "Invalid combination of parameter types" );
        } catch ( IllegalArgumentException e ) {
            assertThat( e.getMessage(), containsString( "Cannot infer return type" ) );
        }
    }


    @Test
    public void testMatchRecognize() {
        // Equivalent SQL:
        //   SELECT *
        //   FROM emp
        //   MATCH_RECOGNIZE (
        //     PARTITION BY deptno
        //     ORDER BY empid asc
        //     MEASURES
        //       STRT.mgr as start_nw,
        //       LAST(DOWN.mgr) as bottom_nw,
        //     PATTERN (STRT DOWN+ UP+) WITHIN INTERVAL '5' SECOND
        //     DEFINE
        //       DOWN as DOWN.mgr < PREV(DOWN.mgr),
        //       UP as UP.mgr > PREV(UP.mgr)
        //   )
        final RelBuilder builder = createRelBuilder().scan( "employee" );
        final RelDataTypeFactory typeFactory = builder.getTypeFactory();
        final RelDataType intType = typeFactory.createSqlType( SqlTypeName.INTEGER );

        RexNode pattern = builder.patternConcat(
                builder.literal( "STRT" ),
                builder.patternQuantify(
                        builder.literal( "DOWN" ),
                        builder.literal( 1 ),
                        builder.literal( -1 ),
                        builder.literal( false ) ),
                builder.patternQuantify(
                        builder.literal( "UP" ),
                        builder.literal( 1 ),
                        builder.literal( -1 ),
                        builder.literal( false ) ) );

        ImmutableMap.Builder<String, RexNode> pdBuilder = new ImmutableMap.Builder<>();
        RexNode downDefinition = builder.call( SqlStdOperatorTable.LESS_THAN,
                builder.call(
                        SqlStdOperatorTable.PREV,
                        builder.patternField( "DOWN", intType, 3 ),
                        builder.literal( 0 ) ),
                builder.call(
                        SqlStdOperatorTable.PREV,
                        builder.patternField( "DOWN", intType, 3 ),
                        builder.literal( 1 ) ) );
        pdBuilder.put( "DOWN", downDefinition );
        RexNode upDefinition = builder.call(
                SqlStdOperatorTable.GREATER_THAN,
                builder.call(
                        SqlStdOperatorTable.PREV,
                        builder.patternField( "UP", intType, 3 ),
                        builder.literal( 0 ) ),
                builder.call(
                        SqlStdOperatorTable.PREV,
                        builder.patternField( "UP", intType, 3 ),
                        builder.literal( 1 ) ) );
        pdBuilder.put( "UP", upDefinition );

        ImmutableList.Builder<RexNode> measuresBuilder = new ImmutableList.Builder<>();
        measuresBuilder.add(
                builder.alias( builder.patternField( "STRT", intType, 3 ), "start_nw" ) );
        measuresBuilder.add(
                builder.alias(
                        builder.call(
                                SqlStdOperatorTable.LAST,
                                builder.patternField( "DOWN", intType, 3 ),
                                builder.literal( 0 ) ),
                        "bottom_nw" ) );

        RexNode after = builder.getRexBuilder().makeFlag(
                SqlMatchRecognize.AfterOption.SKIP_TO_NEXT_ROW );

        ImmutableList.Builder<RexNode> partitionKeysBuilder = new ImmutableList.Builder<>();
        partitionKeysBuilder.add( builder.field( "deptno" ) );

        ImmutableList.Builder<RexNode> orderKeysBuilder = new ImmutableList.Builder<>();
        orderKeysBuilder.add( builder.field( "empid" ) );

        RexNode interval = builder.literal( "INTERVAL '5' SECOND" );

        final ImmutableMap<String, TreeSet<String>> subsets = ImmutableMap.of();
        final RelNode root = builder
                .match( pattern, false, false, pdBuilder.build(), measuresBuilder.build(), after, subsets, false, partitionKeysBuilder.build(), orderKeysBuilder.build(), interval )
                .build();
        final String expected = "LogicalMatch(partition=[[$7]], order=[[0]], "
                + "outputFields=[[$7, 'start_nw', 'bottom_nw']], allRows=[false], "
                + "after=[FLAG(SKIP TO NEXT ROW)], pattern=[(('STRT', "
                + "PATTERN_QUANTIFIER('DOWN', 1, -1, false)), "
                + "PATTERN_QUANTIFIER('UP', 1, -1, false))], "
                + "isStrictStarts=[false], isStrictEnds=[false], "
                + "interval=['INTERVAL ''5'' SECOND'], subsets=[[]], "
                + "patternDefinitions=[[<(PREV(DOWN.$3, 0), PREV(DOWN.$3, 1)), "
                + ">(PREV(UP.$3, 0), PREV(UP.$3, 1))]], "
                + "inputFields=[[empid, ename, job, mgr, hiredate, salary, commission, deptno]])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testFilterCastAny() {
        final RelBuilder builder = createRelBuilder();
        final RelDataType anyType = builder.getTypeFactory().createSqlType( SqlTypeName.ANY );
        final RelNode root =
                builder.scan( "employee" )
                        .filter(
                                builder.cast(
                                        builder.getRexBuilder().makeInputRef( anyType, 0 ),
                                        SqlTypeName.BOOLEAN ) )
                        .build();
        final String expected = ""
                + "LogicalFilter(condition=[CAST($0):BOOLEAN NOT NULL])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testFilterCastNull() {
        final RelBuilder builder = createRelBuilder();
        final RelDataTypeFactory typeFactory = builder.getTypeFactory();
        final RelNode root =
                builder.scan( "employee" )
                        .filter(
                                builder.getRexBuilder().makeCast(
                                        typeFactory.createTypeWithNullability(
                                                typeFactory.createSqlType( SqlTypeName.BOOLEAN ),
                                                true ),
                                        builder.equals(
                                                builder.field( "deptno" ),
                                                builder.literal( 10 ) ) ) )
                        .build();
        final String expected = ""
                + "LogicalFilter(condition=[=($7, 10)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testRelBuilderToString() {
        final RelBuilder builder = createRelBuilder();
        builder.scan( "employee" );

        // One entry on the stack, a single-node tree
        final String expected1 = "LogicalTableScan(table=[[public, employee]])\n";
        assertThat( Util.toLinux( builder.toString() ), is( expected1 ) );

        // One entry on the stack, a two-node tree
        builder.filter( builder.equals( builder.field( 2 ), builder.literal( 3 ) ) );
        final String expected2 = "LogicalFilter(condition=[=($2, 3)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( Util.toLinux( builder.toString() ), is( expected2 ) );

        // Two entries on the stack
        builder.scan( "department" );
        final String expected3 = "LogicalTableScan(table=[[public, department]])\n"
                + "LogicalFilter(condition=[=($2, 3)])\n"
                + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( Util.toLinux( builder.toString() ), is( expected3 ) );
    }


    private void checkExpandTable( RelBuilder builder, Matcher<RelNode> matcher ) {
        final RelNode root =
                builder.scan( "JDBC_public", "employee" )
                        .filter( builder.call( SqlStdOperatorTable.GREATER_THAN, builder.field( 2 ), builder.literal( 10 ) ) )
                        .build();
        assertThat( root, matcher );
    }


    @Test
    public void testExchange() {
        final RelBuilder builder = createRelBuilder();
        final RelNode root = builder.scan( "employee" )
                .exchange( RelDistributions.hash( Lists.newArrayList( 0 ) ) )
                .build();
        final String expected =
                "LogicalExchange(distribution=[hash[0]])\n"
                        + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }


    @Test
    public void testSortExchange() {
        final RelBuilder builder = createRelBuilder();
        final RelNode root =
                builder.scan( "public", "employee" )
                        .sortExchange(
                                RelDistributions.hash( Lists.newArrayList( 0 ) ),
                                RelCollations.of( 0 ) )
                        .build();
        final String expected =
                "LogicalSortExchange(distribution=[hash[0]], collation=[[0]])\n"
                        + "  LogicalTableScan(table=[[public, employee]])\n";
        assertThat( root, Matchers.hasTree( expected ) );
    }
}
