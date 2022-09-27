/*
 * Copyright 2019-2022 The Polypheny Project
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

package org.polypheny.db.sql.language;


import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.languages.ParserPos;
import org.polypheny.db.nodes.BasicNodeVisitor.ArgHandler;
import org.polypheny.db.nodes.Call;
import org.polypheny.db.nodes.Literal;
import org.polypheny.db.nodes.Node;
import org.polypheny.db.nodes.NodeVisitor;
import org.polypheny.db.nodes.Operator;
import org.polypheny.db.sql.language.validate.SqlValidator;
import org.polypheny.db.sql.language.validate.SqlValidatorScope;
import org.polypheny.db.util.ImmutableNullableList;


/**
 * SqlNode for MATCH_RECOGNIZE clause.
 */
public class SqlMatchRecognize extends SqlCall {

    public static final int OPERAND_TABLE_REF = 0;
    public static final int OPERAND_PATTERN = 1;
    public static final int OPERAND_STRICT_START = 2;
    public static final int OPERAND_STRICT_END = 3;
    public static final int OPERAND_PATTERN_DEFINES = 4;
    public static final int OPERAND_MEASURES = 5;
    public static final int OPERAND_AFTER = 6;
    public static final int OPERAND_SUBSET = 7;
    public static final int OPERAND_ROWS_PER_MATCH = 8;
    public static final int OPERAND_PARTITION_BY = 9;
    public static final int OPERAND_ORDER_BY = 10;
    public static final int OPERAND_INTERVAL = 11;

    public static final SqlPrefixOperator SKIP_TO_FIRST =
            new SqlPrefixOperator(
                    "SKIP TO FIRST",
                    Kind.SKIP_TO_FIRST,
                    20,
                    null,
                    null,
                    null );

    public static final SqlPrefixOperator SKIP_TO_LAST =
            new SqlPrefixOperator(
                    "SKIP TO LAST",
                    Kind.SKIP_TO_LAST,
                    20,
                    null,
                    null,
                    null );


    private SqlNode tableRef;
    private SqlNode pattern;
    private SqlLiteral strictStart;
    private SqlLiteral strictEnd;
    private SqlNodeList patternDefList;
    private SqlNodeList measureList;
    private SqlNode after;
    private SqlNodeList subsetList;
    private SqlLiteral rowsPerMatch;
    private SqlNodeList partitionList;
    private SqlNodeList orderList;
    private SqlLiteral interval;


    /**
     * Creates a SqlMatchRecognize.
     */
    public SqlMatchRecognize(
            ParserPos pos,
            SqlNode tableRef,
            SqlNode pattern,
            SqlLiteral strictStart,
            SqlLiteral strictEnd,
            SqlNodeList patternDefList,
            SqlNodeList measureList,
            SqlNode after,
            SqlNodeList subsetList,
            SqlLiteral rowsPerMatch,
            SqlNodeList partitionList,
            SqlNodeList orderList,
            SqlLiteral interval ) {
        super( pos );
        this.tableRef = Objects.requireNonNull( tableRef );
        this.pattern = Objects.requireNonNull( pattern );
        this.strictStart = strictStart;
        this.strictEnd = strictEnd;
        this.patternDefList = Objects.requireNonNull( patternDefList );
        Preconditions.checkArgument( patternDefList.size() > 0 );
        this.measureList = Objects.requireNonNull( measureList );
        this.after = after;
        this.subsetList = subsetList;
        Preconditions.checkArgument( rowsPerMatch == null || rowsPerMatch.value instanceof RowsPerMatchOption );
        this.rowsPerMatch = rowsPerMatch;
        this.partitionList = Objects.requireNonNull( partitionList );
        this.orderList = Objects.requireNonNull( orderList );
        this.interval = interval;
    }


    @Override
    public Operator getOperator() {
        return SqlMatchRecognizeOperator.INSTANCE;
    }


    @Override
    public Kind getKind() {
        return Kind.MATCH_RECOGNIZE;
    }


    @Override
    public List<Node> getOperandList() {
        return ImmutableNullableList.of( tableRef, pattern, strictStart, strictEnd, patternDefList, measureList, after, subsetList, partitionList, orderList );
    }


    @Override
    public List<SqlNode> getSqlOperandList() {
        return ImmutableNullableList.of( tableRef, pattern, strictStart, strictEnd, patternDefList, measureList, after, subsetList, partitionList, orderList );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        ((SqlOperator) getOperator()).unparse( writer, this, 0, 0 );
    }


    @Override
    public void validate( SqlValidator validator, SqlValidatorScope scope ) {
        validator.validateMatchRecognize( this );
    }


    @Override
    public void setOperand( int i, Node operand ) {
        switch ( i ) {
            case OPERAND_TABLE_REF:
                tableRef = (SqlNode) Objects.requireNonNull( operand );
                break;
            case OPERAND_PATTERN:
                pattern = (SqlNode) operand;
                break;
            case OPERAND_STRICT_START:
                strictStart = (SqlLiteral) operand;
                break;
            case OPERAND_STRICT_END:
                strictEnd = (SqlLiteral) operand;
                break;
            case OPERAND_PATTERN_DEFINES:
                patternDefList = Objects.requireNonNull( (SqlNodeList) operand );
                Preconditions.checkArgument( patternDefList.size() > 0 );
                break;
            case OPERAND_MEASURES:
                measureList = Objects.requireNonNull( (SqlNodeList) operand );
                break;
            case OPERAND_AFTER:
                after = (SqlNode) operand;
                break;
            case OPERAND_SUBSET:
                subsetList = (SqlNodeList) operand;
                break;
            case OPERAND_ROWS_PER_MATCH:
                rowsPerMatch = (SqlLiteral) operand;
                Preconditions.checkArgument( rowsPerMatch == null || rowsPerMatch.value instanceof RowsPerMatchOption );
                break;
            case OPERAND_PARTITION_BY:
                partitionList = (SqlNodeList) operand;
                break;
            case OPERAND_ORDER_BY:
                orderList = (SqlNodeList) operand;
                break;
            case OPERAND_INTERVAL:
                interval = (SqlLiteral) operand;
                break;
            default:
                throw new AssertionError( i );
        }
    }


    @Nonnull
    public SqlNode getTableRef() {
        return tableRef;
    }


    public SqlNode getPattern() {
        return pattern;
    }


    public SqlLiteral getStrictStart() {
        return strictStart;
    }


    public SqlLiteral getStrictEnd() {
        return strictEnd;
    }


    @Nonnull
    public SqlNodeList getPatternDefList() {
        return patternDefList;
    }


    @Nonnull
    public SqlNodeList getMeasureList() {
        return measureList;
    }


    public SqlNode getAfter() {
        return after;
    }


    public SqlNodeList getSubsetList() {
        return subsetList;
    }


    public SqlLiteral getRowsPerMatch() {
        return rowsPerMatch;
    }


    public SqlNodeList getPartitionList() {
        return partitionList;
    }


    public SqlNodeList getOrderList() {
        return orderList;
    }


    public SqlLiteral getInterval() {
        return interval;
    }


    /**
     * Options for {@code ROWS PER MATCH}.
     */
    public enum RowsPerMatchOption {
        ONE_ROW( "ONE ROW PER MATCH" ),
        ALL_ROWS( "ALL ROWS PER MATCH" );

        private final String sql;


        RowsPerMatchOption( String sql ) {
            this.sql = sql;
        }


        @Override
        public String toString() {
            return sql;
        }


        public SqlLiteral symbol( ParserPos pos ) {
            return SqlLiteral.createSymbol( this, pos );
        }
    }


    /**
     * Options for {@code AFTER MATCH} clause.
     */
    public enum AfterOption {
        SKIP_TO_NEXT_ROW( "SKIP TO NEXT ROW" ),
        SKIP_PAST_LAST_ROW( "SKIP PAST LAST ROW" );

        private final String sql;


        AfterOption( String sql ) {
            this.sql = sql;
        }


        @Override
        public String toString() {
            return sql;
        }


        /**
         * Creates a parse-tree node representing an occurrence of this symbol at a particular position in the parsed text.
         */
        public SqlLiteral symbol( ParserPos pos ) {
            return SqlLiteral.createSymbol( this, pos );
        }
    }


    /**
     * An operator describing a MATCH_RECOGNIZE specification.
     */
    public static class SqlMatchRecognizeOperator extends SqlOperator {

        public static final SqlMatchRecognizeOperator INSTANCE = new SqlMatchRecognizeOperator();


        private SqlMatchRecognizeOperator() {
            super(
                    "MATCH_RECOGNIZE",
                    Kind.MATCH_RECOGNIZE,
                    2,
                    true,
                    null,
                    null,
                    null );
        }


        @Override
        public SqlSyntax getSqlSyntax() {
            return SqlSyntax.SPECIAL;
        }


        @Override
        public SqlCall createCall( Literal functionQualifier, ParserPos pos, Node... operands ) {
            assert functionQualifier == null;
            assert operands.length == 12;

            return new SqlMatchRecognize(
                    pos,
                    (SqlNode) operands[0],
                    (SqlNode) operands[1],
                    (SqlLiteral) operands[2],
                    (SqlLiteral) operands[3],
                    (SqlNodeList) operands[4],
                    (SqlNodeList) operands[5],
                    (SqlNode) operands[6],
                    (SqlNodeList) operands[7],
                    (SqlLiteral) operands[8],
                    (SqlNodeList) operands[9],
                    (SqlNodeList) operands[10],
                    (SqlLiteral) operands[11] );
        }


        @Override
        public <R> void acceptCall( NodeVisitor<R> visitor, Call call, boolean onlyExpressions, ArgHandler<R> argHandler ) {
            if ( onlyExpressions ) {
                List<SqlNode> operands = ((SqlCall) call).getSqlOperandList();
                for ( int i = 0; i < operands.size(); i++ ) {
                    SqlNode operand = operands.get( i );
                    if ( operand == null ) {
                        continue;
                    }
                    argHandler.visitChild( visitor, call, i, operand );
                }
            } else {
                super.acceptCall( visitor, call, onlyExpressions, argHandler );
            }
        }


        @Override
        public void validateCall( SqlCall call, SqlValidator validator, SqlValidatorScope scope, SqlValidatorScope operandScope ) {
            validator.validateMatchRecognize( call );
        }


        @Override
        public void unparse( SqlWriter writer, SqlCall call, int leftPrec, int rightPrec ) {
            final SqlMatchRecognize pattern = (SqlMatchRecognize) call;

            pattern.tableRef.unparse( writer, 0, 0 );
            final SqlWriter.Frame mrFrame = writer.startFunCall( "MATCH_RECOGNIZE" );

            if ( pattern.partitionList != null && pattern.partitionList.size() > 0 ) {
                writer.newlineAndIndent();
                writer.sep( "PARTITION BY" );
                final SqlWriter.Frame partitionFrame = writer.startList( "", "" );
                pattern.partitionList.unparse( writer, 0, 0 );
                writer.endList( partitionFrame );
            }

            if ( pattern.orderList != null && pattern.orderList.size() > 0 ) {
                writer.newlineAndIndent();
                writer.sep( "ORDER BY" );
                final SqlWriter.Frame orderFrame = writer.startList( SqlWriter.FrameTypeEnum.ORDER_BY_LIST );
                unparseListClause( writer, pattern.orderList );
                writer.endList( orderFrame );
            }

            if ( pattern.measureList != null && pattern.measureList.size() > 0 ) {
                writer.newlineAndIndent();
                writer.sep( "MEASURES" );
                final SqlWriter.Frame measureFrame = writer.startList( "", "" );
                pattern.measureList.unparse( writer, 0, 0 );
                writer.endList( measureFrame );
            }

            if ( pattern.rowsPerMatch != null ) {
                writer.newlineAndIndent();
                pattern.rowsPerMatch.unparse( writer, 0, 0 );
            }

            if ( pattern.after != null ) {
                writer.newlineAndIndent();
                writer.sep( "AFTER MATCH" );
                pattern.after.unparse( writer, 0, 0 );
            }

            writer.newlineAndIndent();
            writer.sep( "PATTERN" );

            SqlWriter.Frame patternFrame = writer.startList( "(", ")" );
            if ( pattern.strictStart.booleanValue() ) {
                writer.sep( "^" );
            }
            pattern.pattern.unparse( writer, 0, 0 );
            if ( pattern.strictEnd.booleanValue() ) {
                writer.sep( "$" );
            }
            writer.endList( patternFrame );
            if ( pattern.interval != null ) {
                writer.sep( "WITHIN" );
                pattern.interval.unparse( writer, 0, 0 );
            }

            if ( pattern.subsetList != null && pattern.subsetList.size() > 0 ) {
                writer.newlineAndIndent();
                writer.sep( "SUBSET" );
                SqlWriter.Frame subsetFrame = writer.startList( "", "" );
                pattern.subsetList.unparse( writer, 0, 0 );
                writer.endList( subsetFrame );
            }

            writer.newlineAndIndent();
            writer.sep( "DEFINE" );

            final SqlWriter.Frame patternDefFrame = writer.startList( "", "" );
            final SqlNodeList newDefineList = new SqlNodeList( ParserPos.ZERO );
            for ( SqlNode node : pattern.getPatternDefList().getSqlList() ) {
                final SqlCall call2 = (SqlCall) node;
                // swap the position of alias position in AS operator
                newDefineList.add( call2.getOperator().createCall( ParserPos.ZERO, call2.operand( 1 ), call2.operand( 0 ) ) );
            }
            newDefineList.unparse( writer, 0, 0 );
            writer.endList( patternDefFrame );
            writer.endList( mrFrame );
        }

    }

}

