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

package org.polypheny.db.languages.sql.parser;


import static org.polypheny.db.util.Static.RESOURCE;

import com.google.common.base.Preconditions;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.avatica.util.DateTimeUtils;
import org.polypheny.db.core.util.CoreUtil;
import org.polypheny.db.core.enums.Kind;
import org.polypheny.db.core.ParserPos;
import org.polypheny.db.core.StdOperatorRegistry;
import org.polypheny.db.core.operators.OperatorName;
import org.polypheny.db.languages.sql.SqlBinaryOperator;
import org.polypheny.db.languages.sql.SqlDateLiteral;
import org.polypheny.db.languages.sql.SqlIntervalLiteral;
import org.polypheny.db.languages.sql.SqlIntervalQualifier;
import org.polypheny.db.languages.sql.SqlLiteral;
import org.polypheny.db.languages.sql.SqlNode;
import org.polypheny.db.languages.sql.SqlNodeList;
import org.polypheny.db.languages.sql.SqlNumericLiteral;
import org.polypheny.db.languages.sql.SqlOperator;
import org.polypheny.db.languages.sql.SqlPostfixOperator;
import org.polypheny.db.languages.sql.SqlPrefixOperator;
import org.polypheny.db.languages.sql.SqlSpecialOperator;
import org.polypheny.db.languages.sql.SqlSpecialOperator.TokenSequence;
import org.polypheny.db.languages.sql.SqlTimeLiteral;
import org.polypheny.db.languages.sql.SqlTimestampLiteral;
import org.polypheny.db.rel.type.RelDataTypeSystem;
import org.polypheny.db.runtime.PolyphenyDbContextException;
import org.polypheny.db.util.DateString;
import org.polypheny.db.util.PrecedenceClimbingParser;
import org.polypheny.db.util.SaffronProperties;
import org.polypheny.db.util.TimeString;
import org.polypheny.db.util.TimestampString;
import org.polypheny.db.util.Util;
import org.polypheny.db.util.trace.PolyphenyDbTrace;
import org.slf4j.Logger;


/**
 * Utility methods relating to parsing SQL.
 */
public final class SqlParserUtil {

    static final Logger LOGGER = PolyphenyDbTrace.getParserTracer();


    private SqlParserUtil() {
    }


    /**
     * @return the character-set prefix of an sql string literal; returns null if there is none
     */
    public static String getCharacterSet( String s ) {
        if ( s.charAt( 0 ) == '\'' ) {
            return null;
        }
        if ( Character.toUpperCase( s.charAt( 0 ) ) == 'N' ) {
            return SaffronProperties.INSTANCE.defaultNationalCharset().get();
        }
        int i = s.indexOf( "'" );
        return s.substring( 1, i ); // skip prefixed '_'
    }


    /**
     * Converts the contents of an sql quoted string literal into the corresponding Java string representation (removing leading and trailing quotes and unescaping internal doubled quotes).
     */
    public static String parseString( String s ) {
        int i = s.indexOf( "'" ); // start of body
        if ( i > 0 ) {
            s = s.substring( i );
        }
        return strip( s, "'", "'", "''", Casing.UNCHANGED );
    }


    public static BigDecimal parseDecimal( String s ) {
        return new BigDecimal( s );
    }


    public static BigDecimal parseInteger( String s ) {
        return new BigDecimal( s );
    }


    public static SqlDateLiteral parseDateLiteral( String s, ParserPos pos ) {
        final String dateStr = parseString( s );
        final Calendar cal = DateTimeUtils.parseDateFormat( dateStr, Format.PER_THREAD.get().date, DateTimeUtils.UTC_ZONE );
        if ( cal == null ) {
            throw CoreUtil.newContextException( pos, RESOURCE.illegalLiteral( "DATE", s, RESOURCE.badFormat( DateTimeUtils.DATE_FORMAT_STRING ).str() ) );
        }
        final DateString d = DateString.fromCalendarFields( cal );
        return SqlLiteral.createDate( d, pos );
    }


    public static SqlTimeLiteral parseTimeLiteral( String s, ParserPos pos ) {
        final String dateStr = parseString( s );
        final DateTimeUtils.PrecisionTime pt = DateTimeUtils.parsePrecisionDateTimeLiteral( dateStr, Format.PER_THREAD.get().time, DateTimeUtils.UTC_ZONE, -1 );
        if ( pt == null ) {
            throw CoreUtil.newContextException( pos, RESOURCE.illegalLiteral( "TIME", s, RESOURCE.badFormat( DateTimeUtils.TIME_FORMAT_STRING ).str() ) );
        }
        final TimeString t = TimeString.fromCalendarFields( pt.getCalendar() ).withFraction( pt.getFraction() );
        return SqlLiteral.createTime( t, pt.getPrecision(), pos );
    }


    public static SqlTimestampLiteral parseTimestampLiteral( String s, ParserPos pos ) {
        final String dateStr = parseString( s );
        final DateTimeUtils.PrecisionTime pt = DateTimeUtils.parsePrecisionDateTimeLiteral( dateStr, Format.PER_THREAD.get().timestamp, DateTimeUtils.UTC_ZONE, -1 );
        if ( pt == null ) {
            throw CoreUtil.newContextException( pos, RESOURCE.illegalLiteral( "TIMESTAMP", s, RESOURCE.badFormat( DateTimeUtils.TIMESTAMP_FORMAT_STRING ).str() ) );
        }
        final TimestampString ts = TimestampString.fromCalendarFields( pt.getCalendar() ).withFraction( pt.getFraction() );
        return SqlLiteral.createTimestamp( ts, pt.getPrecision(), pos );
    }


    public static SqlIntervalLiteral parseIntervalLiteral( ParserPos pos, int sign, String s, SqlIntervalQualifier intervalQualifier ) {
        final String intervalStr = parseString( s );
        if ( intervalStr.equals( "" ) ) {
            throw CoreUtil.newContextException( pos, RESOURCE.illegalIntervalLiteral( s + " " + intervalQualifier.toString(), pos.toString() ) );
        }
        return SqlLiteral.createInterval( sign, intervalStr, intervalQualifier, pos );
    }


    /**
     * Checks if the date/time format is valid
     *
     * @param pattern {@link SimpleDateFormat}  pattern
     */
    public static void checkDateFormat( String pattern ) {
        SimpleDateFormat df = new SimpleDateFormat( pattern, Locale.ROOT );
        Util.discard( df );
    }


    /**
     * Converts the interval value into a millisecond representation.
     *
     * @param interval Interval
     * @return a long value that represents millisecond equivalent of the interval value.
     */
    public static long intervalToMillis( SqlIntervalLiteral.IntervalValue interval ) {
        return intervalToMillis( interval.getIntervalLiteral(), interval.getIntervalQualifier() );
    }


    public static long intervalToMillis( String literal, SqlIntervalQualifier intervalQualifier ) {
        Preconditions.checkArgument( !intervalQualifier.isYearMonth(), "interval must be day time" );
        int[] ret;
        try {
            ret = intervalQualifier.evaluateIntervalLiteral( literal, intervalQualifier.getPos(), RelDataTypeSystem.DEFAULT );
            assert ret != null;
        } catch ( PolyphenyDbContextException e ) {
            throw new RuntimeException( "while parsing day-to-second interval " + literal, e );
        }
        long l = 0;
        long[] conv = new long[5];
        conv[4] = 1; // millisecond
        conv[3] = conv[4] * 1000; // second
        conv[2] = conv[3] * 60; // minute
        conv[1] = conv[2] * 60; // hour
        conv[0] = conv[1] * 24; // day
        for ( int i = 1; i < ret.length; i++ ) {
            l += conv[i - 1] * ret[i];
        }
        return ret[0] * l;
    }


    /**
     * Converts the interval value into a months representation.
     *
     * @param interval Interval
     * @return a long value that represents months equivalent of the interval value.
     */
    public static long intervalToMonths( SqlIntervalLiteral.IntervalValue interval ) {
        return intervalToMonths(
                interval.getIntervalLiteral(),
                interval.getIntervalQualifier() );
    }


    public static long intervalToMonths( String literal, SqlIntervalQualifier intervalQualifier ) {
        Preconditions.checkArgument( intervalQualifier.isYearMonth(), "interval must be year month" );
        int[] ret;
        try {
            ret = intervalQualifier.evaluateIntervalLiteral( literal, intervalQualifier.getPos(), RelDataTypeSystem.DEFAULT );
            assert ret != null;
        } catch ( PolyphenyDbContextException e ) {
            throw new RuntimeException( "Error while parsing year-to-month interval " + literal, e );
        }

        long l = 0;
        long[] conv = new long[2];
        conv[1] = 1; // months
        conv[0] = conv[1] * 12; // years
        for ( int i = 1; i < ret.length; i++ ) {
            l += conv[i - 1] * ret[i];
        }
        return ret[0] * l;
    }


    /**
     * Parses a positive int. All characters have to be digits.
     *
     * @throws java.lang.NumberFormatException if invalid number or leading '-'
     * @see Integer#parseInt(String)
     */
    public static int parsePositiveInt( String value ) {
        value = value.trim();
        if ( value.charAt( 0 ) == '-' ) {
            throw new NumberFormatException( value );
        }
        return Integer.parseInt( value );
    }


    /**
     * Unquotes a quoted string, using different quotes for beginning and end.
     */
    public static String strip( String s, String startQuote, String endQuote, String escape, Casing casing ) {
        if ( startQuote != null ) {
            assert endQuote != null;
            assert startQuote.length() == 1;
            assert endQuote.length() == 1;
            assert escape != null;
            assert s.startsWith( startQuote ) && s.endsWith( endQuote ) : s;
            s = s.substring( 1, s.length() - 1 ).replace( escape, endQuote );
        }
        switch ( casing ) {
            case TO_UPPER:
                return s.toUpperCase( Locale.ROOT );
            case TO_LOWER:
                return s.toLowerCase( Locale.ROOT );
            default:
                return s;
        }
    }


    /**
     * Trims a string for given characters from left and right. E.g. {@code trim("aBaac123AabC","abBcC")} returns {@code "123A"}.
     */
    public static String trim( String s, String chars ) {
        if ( s.length() == 0 ) {
            return "";
        }

        int start;
        for ( start = 0; start < s.length(); start++ ) {
            char c = s.charAt( start );
            if ( chars.indexOf( c ) < 0 ) {
                break;
            }
        }

        int stop;
        for ( stop = s.length(); stop > start; stop-- ) {
            char c = s.charAt( stop - 1 );
            if ( chars.indexOf( c ) < 0 ) {
                break;
            }
        }

        if ( start >= stop ) {
            return "";
        }

        return s.substring( start, stop );
    }


    /**
     * Looks for one or two carets in a SQL string, and if present, converts them into a parser position.
     *
     * Examples:
     *
     * <ul>
     * <li>findPos("xxx^yyy") yields {"xxxyyy", position 3, line 1 column 4}</li>
     * <li>findPos("xxxyyy") yields {"xxxyyy", null}</li>
     * <li>findPos("xxx^yy^y") yields {"xxxyyy", position 3, line 4 column 4 through line 1 column 6}</li>
     * </ul>
     */
    public static StringAndPos findPos( String sql ) {
        int firstCaret = sql.indexOf( '^' );
        if ( firstCaret < 0 ) {
            return new StringAndPos( sql, -1, null );
        }
        int secondCaret = sql.indexOf( '^', firstCaret + 1 );
        if ( secondCaret < 0 ) {
            String sqlSansCaret = sql.substring( 0, firstCaret ) + sql.substring( firstCaret + 1 );
            int[] start = indexToLineCol( sql, firstCaret );
            ParserPos pos = new ParserPos( start[0], start[1] );
            return new StringAndPos( sqlSansCaret, firstCaret, pos );
        } else {
            String sqlSansCaret =
                    sql.substring( 0, firstCaret )
                            + sql.substring( firstCaret + 1, secondCaret )
                            + sql.substring( secondCaret + 1 );
            int[] start = indexToLineCol( sql, firstCaret );

            // subtract 1 because the col position needs to be inclusive
            --secondCaret;
            int[] end = indexToLineCol( sql, secondCaret );

            // if second caret is on same line as first, decrement its column, because first caret pushed the string out
            if ( start[0] == end[0] ) {
                --end[1];
            }

            ParserPos pos = new ParserPos( start[0], start[1], end[0], end[1] );
            return new StringAndPos( sqlSansCaret, firstCaret, pos );
        }
    }


    /**
     * Returns the (1-based) line and column corresponding to a particular (0-based) offset in a string.
     *
     * Converse of {@link #lineColToIndex(String, int, int)}.
     */
    public static int[] indexToLineCol( String sql, int i ) {
        int line = 0;
        int j = 0;
        while ( true ) {
            int prevj = j;
            j = nextLine( sql, j );
            if ( (j < 0) || (j > i) ) {
                return new int[]{ line + 1, i - prevj + 1 };
            }
            ++line;
        }
    }


    public static int nextLine( String sql, int j ) {
        int rn = sql.indexOf( "\r\n", j );
        int r = sql.indexOf( "\r", j );
        int n = sql.indexOf( "\n", j );
        if ( (r < 0) && (n < 0) ) {
            assert rn < 0;
            return -1;
        } else if ( (rn >= 0) && (rn < n) && (rn <= r) ) {
            return rn + 2; // looking at "\r\n"
        } else if ( (r >= 0) && (r < n) ) {
            return r + 1; // looking at "\r"
        } else {
            return n + 1; // looking at "\n"
        }
    }


    /**
     * Finds the position (0-based) in a string which corresponds to a given line and column (1-based).
     *
     * Converse of {@link #indexToLineCol(String, int)}.
     */
    public static int lineColToIndex( String sql, int line, int column ) {
        --line;
        --column;
        int i = 0;
        while ( line-- > 0 ) {
            i = nextLine( sql, i );
        }
        return i + column;
    }


    /**
     * Converts a string to a string with one or two carets in it. For example, <code>addCarets("values (foo)", 1, 9, 1, 12)</code> yields "values (^foo^)".
     */
    public static String addCarets( String sql, int line, int col, int endLine, int endCol ) {
        String sqlWithCarets;
        int cut = lineColToIndex( sql, line, col );
        sqlWithCarets = sql.substring( 0, cut ) + "^" + sql.substring( cut );
        if ( (col != endCol) || (line != endLine) ) {
            cut = lineColToIndex( sqlWithCarets, endLine, endCol );
            ++cut; // for caret
            if ( cut < sqlWithCarets.length() ) {
                sqlWithCarets = sqlWithCarets.substring( 0, cut ) + "^" + sqlWithCarets.substring( cut );
            } else {
                sqlWithCarets += "^";
            }
        }
        return sqlWithCarets;
    }


    public static String getTokenVal( String token ) {
        // We don't care about the token which are not string
        if ( !token.startsWith( "\"" ) ) {
            return null;
        }

        // Remove the quote from the token
        int startIndex = token.indexOf( "\"" );
        int endIndex = token.lastIndexOf( "\"" );
        String tokenVal = token.substring( startIndex + 1, endIndex );
        char c = tokenVal.charAt( 0 );
        if ( Character.isLetter( c ) ) {
            return tokenVal;
        }
        return null;
    }


    public static SqlNode[] toNodeArray( List<SqlNode> list ) {
        return list.toArray( SqlNode.EMPTY_ARRAY );
    }


    public static SqlNode[] toNodeArray( SqlNodeList list ) {
        return (SqlNode[]) list.toArray();
    }


    /**
     * Replaces a range of elements in a list with a single element. For example, if list contains <code>{A, B, C, D, E}</code> then <code>replaceSublist(list, X, 1, 4)</code> returns <code>{A, X, E}</code>.
     */
    public static <T> void replaceSublist( List<T> list, int start, int end, T o ) {
        Objects.requireNonNull( list );
        Preconditions.checkArgument( start < end );
        for ( int i = end - 1; i > start; --i ) {
            list.remove( i );
        }
        list.set( start, o );
    }


    /**
     * Converts a list of {expression, operator, expression, ...} into a tree, taking operator precedence and associativity into account.
     */
    public static SqlNode toTree( List<Object> list ) {
        if ( list.size() == 1 && list.get( 0 ) instanceof SqlNode ) {
            // Short-cut for the simple common case
            return (SqlNode) list.get( 0 );
        }
        LOGGER.trace( "Attempting to reduce {}", list );
        final OldTokenSequenceImpl tokenSequence = new OldTokenSequenceImpl( list );
        final SqlNode node = toTreeEx( tokenSequence, 0, 0, Kind.OTHER );
        LOGGER.debug( "Reduced {}", node );
        return node;
    }


    /**
     * Converts a list of {expression, operator, expression, ...} into a tree, taking operator precedence and associativity into account.
     *
     * @param list List of operands and operators. This list is modified as expressions are reduced.
     * @param start Position of first operand in the list. Anything to the left of this (besides the immediately preceding operand) is ignored. Generally use value 1.
     * @param minPrec Minimum precedence to consider. If the method encounters an operator of lower precedence, it doesn't reduce any further.
     * @param stopperKind If not {@link Kind#OTHER}, stop reading the list if we encounter a token of this kind.
     * @return the root node of the tree which the list condenses into
     */
    public static SqlNode toTreeEx( TokenSequence list, int start, final int minPrec, final Kind stopperKind ) {
        PrecedenceClimbingParser parser = list.parser( start,
                token -> {
                    if ( token instanceof PrecedenceClimbingParser.Op ) {
                        final SqlOperator op = ((ToTreeListItem) token.o).op;
                        return stopperKind != Kind.OTHER
                                && op.kind == stopperKind
                                || minPrec > 0
                                && op.getLeftPrec() < minPrec;
                    } else {
                        return false;
                    }
                } );
        final int beforeSize = parser.all().size();
        parser.partialParse();
        final int afterSize = parser.all().size();
        final SqlNode node = convert( parser.all().get( 0 ) );
        list.replaceSublist( start, start + beforeSize - afterSize + 1, node );
        return node;
    }


    private static SqlNode convert( PrecedenceClimbingParser.Token token ) {
        switch ( token.type ) {
            case ATOM:
                return (SqlNode) token.o;
            case CALL:
                final PrecedenceClimbingParser.Call call = (PrecedenceClimbingParser.Call) token;
                final List<SqlNode> list = new ArrayList<>();
                for ( PrecedenceClimbingParser.Token arg : call.args ) {
                    list.add( convert( arg ) );
                }
                final ToTreeListItem item = (ToTreeListItem) call.op.o;
                if ( item.op.equals( StdOperatorRegistry.get( OperatorName.UNARY_MINUS ) )
                        && list.size() == 1
                        && list.get( 0 ) instanceof SqlNumericLiteral ) {
                    return SqlLiteral.createNegative( (SqlNumericLiteral) list.get( 0 ), item.pos.plusAll( list ) );
                }
                if ( item.op.equals( StdOperatorRegistry.get( OperatorName.UNARY_PLUS ) )
                        && list.size() == 1
                        && list.get( 0 ) instanceof SqlNumericLiteral ) {
                    return list.get( 0 );
                }
                return (SqlNode) item.op.createCall( item.pos.plusAll( list ), list );
            default:
                throw new AssertionError( token );
        }
    }


    /**
     * Checks a UESCAPE string for validity, and returns the escape character if no exception is thrown.
     *
     * @param s UESCAPE string to check
     * @return validated escape character
     */
    public static char checkUnicodeEscapeChar( String s ) {
        if ( s.length() != 1 ) {
            throw RESOURCE.unicodeEscapeCharLength( s ).ex();
        }
        char c = s.charAt( 0 );
        if ( Character.isDigit( c )
                || Character.isWhitespace( c )
                || (c == '+')
                || (c == '"')
                || ((c >= 'a') && (c <= 'f'))
                || ((c >= 'A') && (c <= 'F')) ) {
            throw RESOURCE.unicodeEscapeCharIllegal( s ).ex();
        }
        return c;
    }


    /**
     * Class that holds a {@link SqlOperator} and a {@link ParserPos}. Used by {@link SqlSpecialOperator#reduceExpr} and the parser to associate a parsed operator with a parser position.
     */
    public static class ToTreeListItem {

        private final SqlOperator op;
        private final ParserPos pos;


        public ToTreeListItem( SqlOperator op, ParserPos pos ) {
            this.op = op;
            this.pos = pos;
        }


        public String toString() {
            return op.toString();
        }


        public SqlOperator getOperator() {
            return op;
        }


        public ParserPos getPos() {
            return pos;
        }

    }


    /**
     * Contains a string, the offset of a token within the string, and a parser position containing the beginning and end line number.
     */
    public static class StringAndPos {

        public final String sql;
        public final int cursor;
        public final ParserPos pos;


        StringAndPos( String sql, int cursor, ParserPos pos ) {
            this.sql = sql;
            this.cursor = cursor;
            this.pos = pos;
        }

    }


    /**
     * Implementation of {@link TokenSequence} based on an existing parser.
     */
    private static class TokenSequenceImpl implements SqlSpecialOperator.TokenSequence {

        final List<PrecedenceClimbingParser.Token> list;
        final PrecedenceClimbingParser parser;


        private TokenSequenceImpl( PrecedenceClimbingParser parser ) {
            this.parser = parser;
            this.list = parser.all();
        }


        @Override
        public PrecedenceClimbingParser parser( int start, Predicate<PrecedenceClimbingParser.Token> predicate ) {
            return parser.copy( start, predicate );
        }


        @Override
        public int size() {
            return list.size();
        }


        @Override
        public SqlOperator op( int i ) {
            return ((ToTreeListItem) list.get( i ).o).getOperator();
        }


        private static ParserPos pos( PrecedenceClimbingParser.Token token ) {
            switch ( token.type ) {
                case ATOM:
                    return ((SqlNode) token.o).getPos();
                case CALL:
                    final PrecedenceClimbingParser.Call call = (PrecedenceClimbingParser.Call) token;
                    ParserPos pos = ((ToTreeListItem) call.op.o).pos;
                    for ( PrecedenceClimbingParser.Token arg : call.args ) {
                        pos = pos.plus( pos( arg ) );
                    }
                    return pos;
                default:
                    return ((ToTreeListItem) token.o).getPos();
            }
        }


        @Override
        public ParserPos pos( int i ) {
            return pos( list.get( i ) );
        }


        @Override
        public boolean isOp( int i ) {
            return list.get( i ).o instanceof ToTreeListItem;
        }


        @Override
        public SqlNode node( int i ) {
            return convert( list.get( i ) );
        }


        @Override
        public void replaceSublist( int start, int end, SqlNode e ) {
            SqlParserUtil.replaceSublist( list, start, end, parser.atom( e ) );
        }

    }


    /**
     * Implementation of {@link TokenSequence}.
     */
    private static class OldTokenSequenceImpl implements SqlSpecialOperator.TokenSequence {

        final List<Object> list;


        private OldTokenSequenceImpl( List<Object> list ) {
            this.list = list;
        }


        @Override
        public PrecedenceClimbingParser parser( int start, Predicate<PrecedenceClimbingParser.Token> predicate ) {
            final PrecedenceClimbingParser.Builder builder = new PrecedenceClimbingParser.Builder();
            for ( Object o : Util.skip( list, start ) ) {
                if ( o instanceof ToTreeListItem ) {
                    final ToTreeListItem item = (ToTreeListItem) o;
                    final SqlOperator op = item.getOperator();
                    if ( op instanceof SqlPrefixOperator ) {
                        builder.prefix( item, op.getLeftPrec() );
                    } else if ( op instanceof SqlPostfixOperator ) {
                        builder.postfix( item, op.getRightPrec() );
                    } else if ( op instanceof SqlBinaryOperator ) {
                        builder.infix( item, op.getLeftPrec(), op.getLeftPrec() < op.getRightPrec() );
                    } else if ( op instanceof SqlSpecialOperator ) {
                        builder.special( item, op.getLeftPrec(), op.getRightPrec(),
                                ( parser, op2 ) -> {
                                    final List<PrecedenceClimbingParser.Token> tokens = parser.all();
                                    final SqlSpecialOperator op1 = (SqlSpecialOperator) ((ToTreeListItem) op2.o).op;
                                    SqlSpecialOperator.ReduceResult r = op1.reduceExpr( tokens.indexOf( op2 ), new TokenSequenceImpl( parser ) );
                                    return new PrecedenceClimbingParser.Result(
                                            tokens.get( r.startOrdinal ),
                                            tokens.get( r.endOrdinal - 1 ),
                                            parser.atom( r.node ) );
                                } );
                    } else {
                        throw new AssertionError();
                    }
                } else {
                    builder.atom( o );
                }
            }
            return builder.build();
        }


        @Override
        public int size() {
            return list.size();
        }


        @Override
        public SqlOperator op( int i ) {
            return ((ToTreeListItem) list.get( i )).op;
        }


        @Override
        public ParserPos pos( int i ) {
            final Object o = list.get( i );
            return o instanceof ToTreeListItem
                    ? ((ToTreeListItem) o).pos
                    : ((SqlNode) o).getPos();
        }


        @Override
        public boolean isOp( int i ) {
            return list.get( i ) instanceof ToTreeListItem;
        }


        @Override
        public SqlNode node( int i ) {
            return (SqlNode) list.get( i );
        }


        @Override
        public void replaceSublist( int start, int end, SqlNode e ) {
            SqlParserUtil.replaceSublist( list, start, end, e );
        }

    }


    /**
     * Pre-initialized {@link DateFormat} objects, to be used within the current thread, because {@code DateFormat} is not thread-safe.
     */
    private static class Format {

        private static final ThreadLocal<Format> PER_THREAD = ThreadLocal.withInitial( Format::new );
        final DateFormat timestamp = new SimpleDateFormat( DateTimeUtils.TIMESTAMP_FORMAT_STRING, Locale.ROOT );
        final DateFormat time = new SimpleDateFormat( DateTimeUtils.TIME_FORMAT_STRING, Locale.ROOT );
        final DateFormat date = new SimpleDateFormat( DateTimeUtils.DATE_FORMAT_STRING, Locale.ROOT );

    }

}

