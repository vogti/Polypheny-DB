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

package org.polypheny.db.languages.sql;


import static org.polypheny.db.util.Static.RESOURCE;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.apache.calcite.avatica.util.TimeUnit;
import org.apache.calcite.avatica.util.TimeUnitRange;
import org.polypheny.db.core.IntervalQualifier;
import org.polypheny.db.core.Node;
import org.polypheny.db.core.NodeVisitor;
import org.polypheny.db.core.ParserPos;
import org.polypheny.db.languages.sql.validate.SqlValidator;
import org.polypheny.db.languages.sql.validate.SqlValidatorScope;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeSystem;
import org.polypheny.db.runtime.PolyphenyDbContextException;
import org.polypheny.db.type.PolyIntervalQualifier;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.Litmus;
import org.polypheny.db.util.Util;


/**
 * Represents an INTERVAL qualifier.
 *
 * <p>INTERVAL qualifier is defined as follows:
 *
 * <blockquote><code>
 * &lt;interval qualifier&gt; ::=<br>
 * &nbsp;&nbsp; &lt;start field&gt; TO &lt;end field&gt;<br>
 * &nbsp;&nbsp;| &lt;single datetime field&gt;<br>
 * &lt;start field&gt; ::=<br>
 * &nbsp;&nbsp; &lt;non-second primary datetime field&gt;<br>
 * &nbsp;&nbsp; [ &lt;left paren&gt; &lt;interval leading field precision&gt;
 * &lt;right paren&gt; ]<br>
 * &lt;end field&gt; ::=<br>
 * &nbsp;&nbsp; &lt;non-second primary datetime field&gt;<br>
 * &nbsp;&nbsp;| SECOND [ &lt;left paren&gt;
 * &lt;interval fractional seconds precision&gt; &lt;right paren&gt; ]<br>
 * &lt;single datetime field&gt; ::=<br>
 * &nbsp;&nbsp;&lt;non-second primary datetime field&gt;<br>
 * &nbsp;&nbsp;[ &lt;left paren&gt; &lt;interval leading field precision&gt;
 * &lt;right paren&gt; ]<br>
 * &nbsp;&nbsp;| SECOND [ &lt;left paren&gt;
 * &lt;interval leading field precision&gt;<br>
 * &nbsp;&nbsp;[ &lt;comma&gt; &lt;interval fractional seconds precision&gt; ]
 * &lt;right paren&gt; ]<br>
 * &lt;primary datetime field&gt; ::=<br>
 * &nbsp;&nbsp;&lt;non-second primary datetime field&gt;<br>
 * &nbsp;&nbsp;| SECOND<br>
 * &lt;non-second primary datetime field&gt; ::= YEAR | MONTH | DAY | HOUR
 * | MINUTE<br>
 * &lt;interval fractional seconds precision&gt; ::=
 * &lt;unsigned integer&gt;<br>
 * &lt;interval leading field precision&gt; ::= &lt;unsigned integer&gt;
 * </code></blockquote>
 *
 * <p>Examples include:
 *
 * <ul>
 * <li><code>INTERVAL '1:23:45.678' HOUR TO SECOND</code></li>
 * <li><code>INTERVAL '1 2:3:4' DAY TO SECOND</code></li>
 * <li><code>INTERVAL '1 2:3:4' DAY(4) TO SECOND(4)</code></li>
 * </ul>
 *
 * <p>An instance of this class is immutable.
 */
public class SqlIntervalQualifier extends SqlNode implements IntervalQualifier {
    //~ Static fields/initializers ---------------------------------------------

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf( 1000 );
    private static final BigDecimal INT_MAX_VALUE_PLUS_ONE =
            BigDecimal.valueOf( Integer.MAX_VALUE ).add( BigDecimal.ONE );

    //~ Instance fields --------------------------------------------------------

    private final int startPrecision;
    public final TimeUnitRange timeUnitRange;
    @Getter
    private final int fractionalSecondPrecision;

    //~ Constructors -----------------------------------------------------------


    public SqlIntervalQualifier(
            TimeUnit startUnit,
            int startPrecision,
            TimeUnit endUnit,
            int fractionalSecondPrecision,
            ParserPos pos ) {
        super( pos );
        if ( endUnit == startUnit ) {
            endUnit = null;
        }
        this.timeUnitRange =
                TimeUnitRange.of( Objects.requireNonNull( startUnit ), endUnit );
        this.startPrecision = startPrecision;
        this.fractionalSecondPrecision = fractionalSecondPrecision;
    }


    public SqlIntervalQualifier(
            TimeUnit startUnit,
            TimeUnit endUnit,
            ParserPos pos ) {
        this(
                startUnit,
                RelDataType.PRECISION_NOT_SPECIFIED,
                endUnit,
                RelDataType.PRECISION_NOT_SPECIFIED,
                pos );
    }


    public SqlIntervalQualifier(
            int startPrecision,
            int fractionalSecondPrecision,
            TimeUnitRange timeUnitRange,
            ParserPos pos ) {
        super( pos );
        this.timeUnitRange = timeUnitRange;
        this.startPrecision = startPrecision;
        this.fractionalSecondPrecision = fractionalSecondPrecision;
    }


    public SqlIntervalQualifier( PolyIntervalQualifier polyIntervalQualifier ) {
        this( polyIntervalQualifier.startPrecision, polyIntervalQualifier.fractionalSecondPrecision, polyIntervalQualifier.timeUnitRange, ParserPos.ZERO );
    }

    //~ Methods ----------------------------------------------------------------


    public PolyType typeName() {
        switch ( timeUnitRange ) {
            case YEAR:
            case ISOYEAR:
            case CENTURY:
            case DECADE:
            case MILLENNIUM:
                return PolyType.INTERVAL_YEAR;
            case YEAR_TO_MONTH:
                return PolyType.INTERVAL_YEAR_MONTH;
            case MONTH:
            case QUARTER:
                return PolyType.INTERVAL_MONTH;
            case DOW:
            case ISODOW:
            case DOY:
            case DAY:
            case WEEK:
                return PolyType.INTERVAL_DAY;
            case DAY_TO_HOUR:
                return PolyType.INTERVAL_DAY_HOUR;
            case DAY_TO_MINUTE:
                return PolyType.INTERVAL_DAY_MINUTE;
            case DAY_TO_SECOND:
                return PolyType.INTERVAL_DAY_SECOND;
            case HOUR:
                return PolyType.INTERVAL_HOUR;
            case HOUR_TO_MINUTE:
                return PolyType.INTERVAL_HOUR_MINUTE;
            case HOUR_TO_SECOND:
                return PolyType.INTERVAL_HOUR_SECOND;
            case MINUTE:
                return PolyType.INTERVAL_MINUTE;
            case MINUTE_TO_SECOND:
                return PolyType.INTERVAL_MINUTE_SECOND;
            case SECOND:
            case MILLISECOND:
            case EPOCH:
            case MICROSECOND:
            case NANOSECOND:
                return PolyType.INTERVAL_SECOND;
            default:
                throw new AssertionError( timeUnitRange );
        }
    }


    @Override
    public void validate(
            SqlValidator validator,
            SqlValidatorScope scope ) {
        validator.validateIntervalQualifier( this );
    }


    @Override
    public <R> R accept( NodeVisitor<R> visitor ) {
        return visitor.visit( this );
    }


    @Override
    public boolean equalsDeep( Node node, Litmus litmus ) {
        final String thisString = this.toString();
        final String thatString = node.toString();
        if ( !thisString.equals( thatString ) ) {
            return litmus.fail( "{} != {}", this, node );
        }
        return litmus.succeed();
    }


    public int getStartPrecision( RelDataTypeSystem typeSystem ) {
        if ( startPrecision == RelDataType.PRECISION_NOT_SPECIFIED ) {
            return typeSystem.getDefaultPrecision( typeName() );
        } else {
            return startPrecision;
        }
    }


    public int getStartPrecisionPreservingDefault() {
        return startPrecision;
    }


    /**
     * Returns {@code true} if start precision is not specified.
     */
    public boolean useDefaultStartPrecision() {
        return startPrecision == RelDataType.PRECISION_NOT_SPECIFIED;
    }


    public static int combineStartPrecisionPreservingDefault(
            RelDataTypeSystem typeSystem,
            SqlIntervalQualifier qual1,
            SqlIntervalQualifier qual2 ) {
        final int start1 = qual1.getStartPrecision( typeSystem );
        final int start2 = qual2.getStartPrecision( typeSystem );
        if ( start1 > start2 ) {
            // qual1 is more precise, but if it has the default indicator
            // set, we need to return that indicator so result will also
            // use default
            return qual1.getStartPrecisionPreservingDefault();
        } else if ( start1 < start2 ) {
            // qual2 is more precise, but if it has the default indicator
            // set, we need to return that indicator so result will also
            // use default
            return qual2.getStartPrecisionPreservingDefault();
        } else {
            // they are equal.  return default if both are default,
            // otherwise return exact precision
            if ( qual1.useDefaultStartPrecision()
                    && qual2.useDefaultStartPrecision() ) {
                return qual1.getStartPrecisionPreservingDefault();
            } else {
                return start1;
            }
        }
    }


    public int getFractionalSecondPrecision( RelDataTypeSystem typeSystem ) {
        if ( fractionalSecondPrecision == RelDataType.PRECISION_NOT_SPECIFIED ) {
            return typeName().getDefaultScale();
        } else {
            return fractionalSecondPrecision;
        }
    }


    public int getFractionalSecondPrecisionPreservingDefault() {
        if ( useDefaultFractionalSecondPrecision() ) {
            return RelDataType.PRECISION_NOT_SPECIFIED;
        } else {
            return fractionalSecondPrecision;
        }
    }


    /**
     * Returns {@code true} if fractional second precision is not specified.
     */
    public boolean useDefaultFractionalSecondPrecision() {
        return fractionalSecondPrecision == RelDataType.PRECISION_NOT_SPECIFIED;
    }


    public static int combineFractionalSecondPrecisionPreservingDefault(
            RelDataTypeSystem typeSystem,
            SqlIntervalQualifier qual1,
            SqlIntervalQualifier qual2 ) {
        final int p1 = qual1.getFractionalSecondPrecision( typeSystem );
        final int p2 = qual2.getFractionalSecondPrecision( typeSystem );
        if ( p1 > p2 ) {
            // qual1 is more precise, but if it has the default indicator
            // set, we need to return that indicator so result will also
            // use default
            return qual1.getFractionalSecondPrecisionPreservingDefault();
        } else if ( p1 < p2 ) {
            // qual2 is more precise, but if it has the default indicator
            // set, we need to return that indicator so result will also
            // use default
            return qual2.getFractionalSecondPrecisionPreservingDefault();
        } else {
            // they are equal.  return default if both are default,
            // otherwise return exact precision
            if ( qual1.useDefaultFractionalSecondPrecision()
                    && qual2.useDefaultFractionalSecondPrecision() ) {
                return qual1.getFractionalSecondPrecisionPreservingDefault();
            } else {
                return p1;
            }
        }
    }


    public TimeUnit getStartUnit() {
        return timeUnitRange.startUnit;
    }


    public TimeUnit getEndUnit() {
        return timeUnitRange.endUnit;
    }


    /**
     * Returns {@code SECOND} for both {@code HOUR TO SECOND} and
     * {@code SECOND}.
     */
    public TimeUnit getUnit() {
        return Util.first( timeUnitRange.endUnit, timeUnitRange.startUnit );
    }


    @Override
    public SqlNode clone( ParserPos pos ) {
        return new SqlIntervalQualifier( timeUnitRange.startUnit, startPrecision,
                timeUnitRange.endUnit, fractionalSecondPrecision, pos );
    }


    @Override
    public void unparse(
            SqlWriter writer,
            int leftPrec,
            int rightPrec ) {
        writer.getDialect()
                .unparseSqlIntervalQualifier( writer, this, RelDataTypeSystem.DEFAULT );
    }


    /**
     * Returns whether this interval has a single datetime field.
     *
     * <p>Returns {@code true} if it is of the form {@code unit},
     * {@code false} if it is of the form {@code unit TO unit}.
     */
    @Override
    public boolean isSingleDatetimeField() {
        return timeUnitRange.endUnit == null;
    }


    @Override
    public final boolean isYearMonth() {
        return timeUnitRange.startUnit.yearMonth;
    }


    /**
     * @return 1 or -1
     */
    public int getIntervalSign( String value ) {
        int sign = 1; // positive until proven otherwise

        if ( !Util.isNullOrEmpty( value ) ) {
            if ( '-' == value.charAt( 0 ) ) {
                sign = -1; // Negative
            }
        }

        return sign;
    }


    private String stripLeadingSign( String value ) {
        String unsignedValue = value;

        if ( !Util.isNullOrEmpty( value ) ) {
            if ( ('-' == value.charAt( 0 )) || ('+' == value.charAt( 0 )) ) {
                unsignedValue = value.substring( 1 );
            }
        }

        return unsignedValue;
    }


    private boolean isLeadFieldInRange( RelDataTypeSystem typeSystem,
            BigDecimal value, TimeUnit unit ) {
        // we should never get handed a negative field value
        assert value.compareTo( ZERO ) >= 0;

        // Leading fields are only restricted by startPrecision.
        final int startPrecision = getStartPrecision( typeSystem );
        return startPrecision < POWERS10.length
                ? value.compareTo( POWERS10[startPrecision] ) < 0
                : value.compareTo( INT_MAX_VALUE_PLUS_ONE ) < 0;
    }


    private void checkLeadFieldInRange( RelDataTypeSystem typeSystem, int sign,
            BigDecimal value, TimeUnit unit, ParserPos pos ) {
        if ( !isLeadFieldInRange( typeSystem, value, unit ) ) {
            throw fieldExceedsPrecisionException(
                    pos, sign, value, unit, getStartPrecision( typeSystem ) );
        }
    }


    private static final BigDecimal[] POWERS10 = {
            ZERO,
            BigDecimal.valueOf( 10 ),
            BigDecimal.valueOf( 100 ),
            BigDecimal.valueOf( 1000 ),
            BigDecimal.valueOf( 10000 ),
            BigDecimal.valueOf( 100000 ),
            BigDecimal.valueOf( 1000000 ),
            BigDecimal.valueOf( 10000000 ),
            BigDecimal.valueOf( 100000000 ),
            BigDecimal.valueOf( 1000000000 ),
    };


    private boolean isFractionalSecondFieldInRange( BigDecimal field ) {
        // we should never get handed a negative field value
        assert field.compareTo( ZERO ) >= 0;

        // Fractional second fields are only restricted by precision, which
        // has already been checked for using pattern matching.
        // Therefore, always return true
        return true;
    }


    private boolean isSecondaryFieldInRange( BigDecimal field, TimeUnit unit ) {
        // we should never get handed a negative field value
        assert field.compareTo( ZERO ) >= 0;

        // YEAR and DAY can never be secondary units,
        // nor can unit be null.
        assert unit != null;
        switch ( unit ) {
            case YEAR:
            case DAY:
            default:
                throw Util.unexpected( unit );

                // Secondary field limits, as per section 4.6.3 of SQL2003 spec
            case MONTH:
            case HOUR:
            case MINUTE:
            case SECOND:
                return unit.isValidValue( field );
        }
    }


    private BigDecimal normalizeSecondFraction( String secondFracStr ) {
        // Decimal value can be more than 3 digits. So just get
        // the millisecond part.
        return new BigDecimal( "0." + secondFracStr ).multiply( THOUSAND );
    }


    private int[] fillIntervalValueArray(
            int sign,
            BigDecimal year,
            BigDecimal month ) {
        int[] ret = new int[3];

        ret[0] = sign;
        ret[1] = year.intValue();
        ret[2] = month.intValue();

        return ret;
    }


    private int[] fillIntervalValueArray(
            int sign,
            BigDecimal day,
            BigDecimal hour,
            BigDecimal minute,
            BigDecimal second,
            BigDecimal secondFrac ) {
        int[] ret = new int[6];

        ret[0] = sign;
        ret[1] = day.intValue();
        ret[2] = hour.intValue();
        ret[3] = minute.intValue();
        ret[4] = second.intValue();
        ret[5] = secondFrac.intValue();

        return ret;
    }


    /**
     * Validates an INTERVAL literal against a YEAR interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsYear(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal year;

        // validate as YEAR(startPrecision), e.g. 'YY'
        String intervalPattern = "(\\d+)";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                year = parseField( m, 1 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, year, TimeUnit.YEAR, pos );

            // package values up for return
            return fillIntervalValueArray( sign, year, ZERO );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against a YEAR TO MONTH interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsYearToMonth(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal year;
        BigDecimal month;

        // validate as YEAR(startPrecision) TO MONTH, e.g. 'YY-DD'
        String intervalPattern = "(\\d+)-(\\d{1,2})";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                year = parseField( m, 1 );
                month = parseField( m, 2 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, year, TimeUnit.YEAR, pos );
            if ( !(isSecondaryFieldInRange( month, TimeUnit.MONTH )) ) {
                throw invalidValueException( pos, originalValue );
            }

            // package values up for return
            return fillIntervalValueArray( sign, year, month );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against a MONTH interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsMonth(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal month;

        // validate as MONTH(startPrecision), e.g. 'MM'
        String intervalPattern = "(\\d+)";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                month = parseField( m, 1 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, month, TimeUnit.MONTH, pos );

            // package values up for return
            return fillIntervalValueArray( sign, ZERO, month );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against a DAY interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsDay(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal day;

        // validate as DAY(startPrecision), e.g. 'DD'
        String intervalPattern = "(\\d+)";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                day = parseField( m, 1 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, day, TimeUnit.DAY, pos );

            // package values up for return
            return fillIntervalValueArray( sign, day, ZERO, ZERO, ZERO, ZERO );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against a DAY TO HOUR interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsDayToHour(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal day;
        BigDecimal hour;

        // validate as DAY(startPrecision) TO HOUR, e.g. 'DD HH'
        String intervalPattern = "(\\d+) (\\d{1,2})";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                day = parseField( m, 1 );
                hour = parseField( m, 2 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, day, TimeUnit.DAY, pos );
            if ( !(isSecondaryFieldInRange( hour, TimeUnit.HOUR )) ) {
                throw invalidValueException( pos, originalValue );
            }

            // package values up for return
            return fillIntervalValueArray( sign, day, hour, ZERO, ZERO, ZERO );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against a DAY TO MINUTE interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsDayToMinute(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal day;
        BigDecimal hour;
        BigDecimal minute;

        // validate as DAY(startPrecision) TO MINUTE, e.g. 'DD HH:MM'
        String intervalPattern = "(\\d+) (\\d{1,2}):(\\d{1,2})";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                day = parseField( m, 1 );
                hour = parseField( m, 2 );
                minute = parseField( m, 3 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, day, TimeUnit.DAY, pos );
            if ( !(isSecondaryFieldInRange( hour, TimeUnit.HOUR ))
                    || !(isSecondaryFieldInRange( minute, TimeUnit.MINUTE )) ) {
                throw invalidValueException( pos, originalValue );
            }

            // package values up for return
            return fillIntervalValueArray( sign, day, hour, minute, ZERO, ZERO );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against a DAY TO SECOND interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsDayToSecond(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal day;
        BigDecimal hour;
        BigDecimal minute;
        BigDecimal second;
        BigDecimal secondFrac;
        boolean hasFractionalSecond;

        // validate as DAY(startPrecision) TO MINUTE,
        // e.g. 'DD HH:MM:SS' or 'DD HH:MM:SS.SSS'
        // Note: must check two patterns, since fractional second is optional
        final int fractionalSecondPrecision =
                getFractionalSecondPrecision( typeSystem );
        String intervalPatternWithFracSec =
                "(\\d+) (\\d{1,2}):(\\d{1,2}):(\\d{1,2})\\.(\\d{1,"
                        + fractionalSecondPrecision + "})";
        String intervalPatternWithoutFracSec =
                "(\\d+) (\\d{1,2}):(\\d{1,2}):(\\d{1,2})";

        Matcher m = Pattern.compile( intervalPatternWithFracSec ).matcher( value );
        if ( m.matches() ) {
            hasFractionalSecond = true;
        } else {
            m = Pattern.compile( intervalPatternWithoutFracSec ).matcher( value );
            hasFractionalSecond = false;
        }

        if ( m.matches() ) {
            // Break out  field values
            try {
                day = parseField( m, 1 );
                hour = parseField( m, 2 );
                minute = parseField( m, 3 );
                second = parseField( m, 4 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            if ( hasFractionalSecond ) {
                secondFrac = normalizeSecondFraction( m.group( 5 ) );
            } else {
                secondFrac = ZERO;
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, day, TimeUnit.DAY, pos );
            if ( !(isSecondaryFieldInRange( hour, TimeUnit.HOUR ))
                    || !(isSecondaryFieldInRange( minute, TimeUnit.MINUTE ))
                    || !(isSecondaryFieldInRange( second, TimeUnit.SECOND ))
                    || !(isFractionalSecondFieldInRange( secondFrac )) ) {
                throw invalidValueException( pos, originalValue );
            }

            // package values up for return
            return fillIntervalValueArray(
                    sign,
                    day,
                    hour,
                    minute,
                    second,
                    secondFrac );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against an HOUR interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsHour(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal hour;

        // validate as HOUR(startPrecision), e.g. 'HH'
        String intervalPattern = "(\\d+)";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                hour = parseField( m, 1 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, hour, TimeUnit.HOUR, pos );

            // package values up for return
            return fillIntervalValueArray( sign, ZERO, hour, ZERO, ZERO, ZERO );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against an HOUR TO MINUTE interval
     * qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsHourToMinute(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal hour;
        BigDecimal minute;

        // validate as HOUR(startPrecision) TO MINUTE, e.g. 'HH:MM'
        String intervalPattern = "(\\d+):(\\d{1,2})";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                hour = parseField( m, 1 );
                minute = parseField( m, 2 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, hour, TimeUnit.HOUR, pos );
            if ( !(isSecondaryFieldInRange( minute, TimeUnit.MINUTE )) ) {
                throw invalidValueException( pos, originalValue );
            }

            // package values up for return
            return fillIntervalValueArray( sign, ZERO, hour, minute, ZERO, ZERO );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against an HOUR TO SECOND interval
     * qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsHourToSecond(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal hour;
        BigDecimal minute;
        BigDecimal second;
        BigDecimal secondFrac;
        boolean hasFractionalSecond;

        // validate as HOUR(startPrecision) TO SECOND,
        // e.g. 'HH:MM:SS' or 'HH:MM:SS.SSS'
        // Note: must check two patterns, since fractional second is optional
        final int fractionalSecondPrecision =
                getFractionalSecondPrecision( typeSystem );
        String intervalPatternWithFracSec =
                "(\\d+):(\\d{1,2}):(\\d{1,2})\\.(\\d{1,"
                        + fractionalSecondPrecision + "})";
        String intervalPatternWithoutFracSec =
                "(\\d+):(\\d{1,2}):(\\d{1,2})";

        Matcher m = Pattern.compile( intervalPatternWithFracSec ).matcher( value );
        if ( m.matches() ) {
            hasFractionalSecond = true;
        } else {
            m = Pattern.compile( intervalPatternWithoutFracSec ).matcher( value );
            hasFractionalSecond = false;
        }

        if ( m.matches() ) {
            // Break out  field values
            try {
                hour = parseField( m, 1 );
                minute = parseField( m, 2 );
                second = parseField( m, 3 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            if ( hasFractionalSecond ) {
                secondFrac = normalizeSecondFraction( m.group( 4 ) );
            } else {
                secondFrac = ZERO;
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, hour, TimeUnit.HOUR, pos );
            if ( !(isSecondaryFieldInRange( minute, TimeUnit.MINUTE ))
                    || !(isSecondaryFieldInRange( second, TimeUnit.SECOND ))
                    || !(isFractionalSecondFieldInRange( secondFrac )) ) {
                throw invalidValueException( pos, originalValue );
            }

            // package values up for return
            return fillIntervalValueArray(
                    sign,
                    ZERO,
                    hour,
                    minute,
                    second,
                    secondFrac );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against an MINUTE interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsMinute(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal minute;

        // validate as MINUTE(startPrecision), e.g. 'MM'
        String intervalPattern = "(\\d+)";

        Matcher m = Pattern.compile( intervalPattern ).matcher( value );
        if ( m.matches() ) {
            // Break out  field values
            try {
                minute = parseField( m, 1 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, minute, TimeUnit.MINUTE, pos );

            // package values up for return
            return fillIntervalValueArray( sign, ZERO, ZERO, minute, ZERO, ZERO );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against an MINUTE TO SECOND interval
     * qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsMinuteToSecond(
            RelDataTypeSystem typeSystem, int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal minute;
        BigDecimal second;
        BigDecimal secondFrac;
        boolean hasFractionalSecond;

        // validate as MINUTE(startPrecision) TO SECOND,
        // e.g. 'MM:SS' or 'MM:SS.SSS'
        // Note: must check two patterns, since fractional second is optional
        final int fractionalSecondPrecision =
                getFractionalSecondPrecision( typeSystem );
        String intervalPatternWithFracSec =
                "(\\d+):(\\d{1,2})\\.(\\d{1," + fractionalSecondPrecision + "})";
        String intervalPatternWithoutFracSec =
                "(\\d+):(\\d{1,2})";

        Matcher m = Pattern.compile( intervalPatternWithFracSec ).matcher( value );
        if ( m.matches() ) {
            hasFractionalSecond = true;
        } else {
            m = Pattern.compile( intervalPatternWithoutFracSec ).matcher( value );
            hasFractionalSecond = false;
        }

        if ( m.matches() ) {
            // Break out  field values
            try {
                minute = parseField( m, 1 );
                second = parseField( m, 2 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            if ( hasFractionalSecond ) {
                secondFrac = normalizeSecondFraction( m.group( 3 ) );
            } else {
                secondFrac = ZERO;
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, minute, TimeUnit.MINUTE, pos );
            if ( !(isSecondaryFieldInRange( second, TimeUnit.SECOND ))
                    || !(isFractionalSecondFieldInRange( secondFrac )) ) {
                throw invalidValueException( pos, originalValue );
            }

            // package values up for return
            return fillIntervalValueArray(
                    sign,
                    ZERO,
                    ZERO,
                    minute,
                    second,
                    secondFrac );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal against an SECOND interval qualifier.
     *
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    private int[] evaluateIntervalLiteralAsSecond(
            RelDataTypeSystem typeSystem,
            int sign,
            String value,
            String originalValue,
            ParserPos pos ) {
        BigDecimal second;
        BigDecimal secondFrac;
        boolean hasFractionalSecond;

        // validate as SECOND(startPrecision, fractionalSecondPrecision)
        // e.g. 'SS' or 'SS.SSS'
        // Note: must check two patterns, since fractional second is optional
        final int fractionalSecondPrecision =
                getFractionalSecondPrecision( typeSystem );
        String intervalPatternWithFracSec =
                "(\\d+)\\.(\\d{1," + fractionalSecondPrecision + "})";
        String intervalPatternWithoutFracSec =
                "(\\d+)";

        Matcher m = Pattern.compile( intervalPatternWithFracSec ).matcher( value );
        if ( m.matches() ) {
            hasFractionalSecond = true;
        } else {
            m = Pattern.compile( intervalPatternWithoutFracSec ).matcher( value );
            hasFractionalSecond = false;
        }

        if ( m.matches() ) {
            // Break out  field values
            try {
                second = parseField( m, 1 );
            } catch ( NumberFormatException e ) {
                throw invalidValueException( pos, originalValue );
            }

            if ( hasFractionalSecond ) {
                secondFrac = normalizeSecondFraction( m.group( 2 ) );
            } else {
                secondFrac = ZERO;
            }

            // Validate individual fields
            checkLeadFieldInRange( typeSystem, sign, second, TimeUnit.SECOND, pos );
            if ( !(isFractionalSecondFieldInRange( secondFrac )) ) {
                throw invalidValueException( pos, originalValue );
            }

            // package values up for return
            return fillIntervalValueArray(
                    sign, ZERO, ZERO, ZERO, second, secondFrac );
        } else {
            throw invalidValueException( pos, originalValue );
        }
    }


    /**
     * Validates an INTERVAL literal according to the rules specified by the
     * interval qualifier. The assumption is made that the interval qualifier has
     * been validated prior to calling this method. Evaluating against an
     * invalid qualifier could lead to strange results.
     *
     * @return field values, never null
     * @throws PolyphenyDbContextException if the interval
     * value is illegal
     */
    public int[] evaluateIntervalLiteral( String value, ParserPos pos,
            RelDataTypeSystem typeSystem ) {
        // save original value for if we have to throw
        final String value0 = value;

        // First strip off any leading whitespace
        value = value.trim();

        // check if the sign was explicitly specified.  Record
        // the explicit or implicit sign, and strip it off to
        // simplify pattern matching later.
        final int sign = getIntervalSign( value );
        value = stripLeadingSign( value );

        // If we have an empty or null literal at this point,
        // it's illegal.  Complain and bail out.
        if ( Util.isNullOrEmpty( value ) ) {
            throw invalidValueException( pos, value0 );
        }

        // Validate remaining string according to the pattern
        // that corresponds to the start and end units as
        // well as explicit or implicit precision and range.
        switch ( timeUnitRange ) {
            case YEAR:
                return evaluateIntervalLiteralAsYear( typeSystem, sign, value, value0,
                        pos );
            case YEAR_TO_MONTH:
                return evaluateIntervalLiteralAsYearToMonth( typeSystem, sign, value,
                        value0, pos );
            case MONTH:
                return evaluateIntervalLiteralAsMonth( typeSystem, sign, value, value0,
                        pos );
            case DAY:
                return evaluateIntervalLiteralAsDay( typeSystem, sign, value, value0, pos );
            case DAY_TO_HOUR:
                return evaluateIntervalLiteralAsDayToHour( typeSystem, sign, value, value0,
                        pos );
            case DAY_TO_MINUTE:
                return evaluateIntervalLiteralAsDayToMinute( typeSystem, sign, value,
                        value0, pos );
            case DAY_TO_SECOND:
                return evaluateIntervalLiteralAsDayToSecond( typeSystem, sign, value,
                        value0, pos );
            case HOUR:
                return evaluateIntervalLiteralAsHour( typeSystem, sign, value, value0,
                        pos );
            case HOUR_TO_MINUTE:
                return evaluateIntervalLiteralAsHourToMinute( typeSystem, sign, value,
                        value0, pos );
            case HOUR_TO_SECOND:
                return evaluateIntervalLiteralAsHourToSecond( typeSystem, sign, value,
                        value0, pos );
            case MINUTE:
                return evaluateIntervalLiteralAsMinute( typeSystem, sign, value, value0,
                        pos );
            case MINUTE_TO_SECOND:
                return evaluateIntervalLiteralAsMinuteToSecond( typeSystem, sign, value,
                        value0, pos );
            case SECOND:
                return evaluateIntervalLiteralAsSecond( typeSystem, sign, value, value0,
                        pos );
            default:
                throw invalidValueException( pos, value0 );
        }
    }


    private BigDecimal parseField( Matcher m, int i ) {
        return new BigDecimal( m.group( i ) );
    }


    private PolyphenyDbContextException invalidValueException( ParserPos pos, String value ) {
        return SqlUtil.newContextException( pos,
                RESOURCE.unsupportedIntervalLiteral(
                        "'" + value + "'", "INTERVAL " + toString() ) );
    }


    private PolyphenyDbContextException fieldExceedsPrecisionException(
            ParserPos pos, int sign, BigDecimal value, TimeUnit type,
            int precision ) {
        if ( sign == -1 ) {
            value = value.negate();
        }
        return SqlUtil.newContextException( pos,
                RESOURCE.intervalFieldExceedsPrecision(
                        value, type.name() + "(" + precision + ")" ) );
    }

}

