/*
 * Copyright 2019-2023 The Polypheny Project
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

package org.polypheny.db.adapter.file;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.polypheny.db.adapter.DataContext;
import org.polypheny.db.algebra.constant.Kind;
import org.polypheny.db.algebra.type.AlgDataTypeField;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.functions.Functions;
import org.polypheny.db.rex.RexCall;
import org.polypheny.db.rex.RexDynamicParam;
import org.polypheny.db.rex.RexIndexRef;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.type.entity.PolyValue;


public class Condition {

    private final Kind operator;
    private Integer columnReference;
    private Long literalIndex;
    private PolyValue literal;
    private final ArrayList<Condition> operands = new ArrayList<>();


    public Condition( final RexCall call ) {
        this.operator = call.getOperator().getKind();
        for ( RexNode rex : call.getOperands() ) {
            if ( rex instanceof RexCall ) {
                this.operands.add( new Condition( (RexCall) rex ) );
            } else {
                RexNode n0 = call.getOperands().get( 0 );
                assignRexNode( n0 );
                if ( call.getOperands().size() > 1 ) { // IS NULL and IS NOT NULL have no literal/literalIndex
                    RexNode n1 = call.getOperands().get( 1 );
                    assignRexNode( n1 );
                }
            }
        }
    }


    /**
     * Called by generated code, see {@link Condition#getExpression}
     */
    public Condition( final Kind operator, final Integer columnReference, final Long literalIndex, final PolyValue literal, final Condition[] operands ) {
        this.operator = operator;
        this.columnReference = columnReference;
        this.literalIndex = literalIndex;
        this.literal = literal;
        this.operands.addAll( Arrays.asList( operands.clone() ) );
    }


    /**
     * For linq4j Expressions
     */
    public Expression getExpression() {
        List<Expression> operandsExpressions = new ArrayList<>();
        for ( Condition operand : operands ) {
            operandsExpressions.add( operand.getExpression() );
        }

        return Expressions.new_(
                Condition.class,
                Expressions.constant( operator, Kind.class ),
                Expressions.constant( columnReference, Integer.class ),
                Expressions.constant( literalIndex, Long.class ),
                this.literal == null ? Expressions.constant( null ) : this.literal.asExpression(),
                Expressions.newArrayInit( Condition.class, operandsExpressions )
        );
    }


    private void assignRexNode( final RexNode rexNode ) {
        if ( rexNode instanceof RexIndexRef rexIndexRef ) {
            this.columnReference = rexIndexRef.getIndex();
        } else if ( rexNode instanceof RexDynamicParam dynamicParam ) {
            this.literalIndex = dynamicParam.getIndex();
        } else if ( rexNode instanceof RexLiteral lit ) {
            this.literal = lit.value;
        }
    }


    /**
     * Determines if a condition is a primary key condition, i.e. an AND-condition over all primary key columns
     *
     * @param pkColumnReferences One-based references of the PK columns, e.g. [1,3] for [a,b,c] if a and c are the primary key columns
     * @param colSize Number of columns in the current query, needed to generate the object that will be hashed
     * @return {@code Null} if it is not a PK lookup, or an Object array with the lookups to hash, if it is a PK lookup
     */
    @Nullable
    public List<PolyValue> getPKLookup( final Set<Integer> pkColumnReferences, final List<AlgDataTypeField> columnTypes, final int colSize, final DataContext dataContext ) {
        List<PolyValue> lookups = new ArrayList<>( Collections.nCopies( colSize, null ) );
        if ( operator == Kind.EQUALS && pkColumnReferences.size() == 1 ) {
            if ( pkColumnReferences.contains( columnReference ) ) {
                lookups.set( columnReference, getParamValue( dataContext ) );
                return lookups;
            } else {
                return null;
            }
        } else if ( operator == Kind.AND ) {
            for ( Condition operand : operands ) {
                if ( operand.operator == Kind.EQUALS ) {
                    if ( !pkColumnReferences.contains( operand.columnReference ) ) {
                        return null;
                    } else {
                        pkColumnReferences.remove( operand.columnReference );
                    }
                } else {
                    return null;
                }
            }
            if ( pkColumnReferences.isEmpty() ) {
                return lookups;
            } else {
                return null;
            }
        }
        return null;
    }


    /**
     * Get the value of the condition parameter, either from the literal or literalIndex
     */
    PolyValue getParamValue( final DataContext dataContext ) {
        PolyValue out;
        if ( this.literalIndex != null ) {
            out = dataContext.getParameterValue( literalIndex );
        } else {
            out = this.literal;
        }
        return out;
    }


    /**
     * Implement the like keyword
     *
     * @param str Data in database
     * @param expr String in SQL statement
     * @return boolean
     */
    private static boolean like( final PolyValue str, PolyValue expr ) {
        if ( str == null || expr == null ) {
            return false;
        }
        if ( !str.isString() || !expr.isString() ) {
            return false;
        }

        return Functions.like( str.asString(), expr.asString() ).value;
    }


    public boolean matches( final List<PolyValue> columnValues, final List<AlgDataTypeField> columnTypes, final DataContext dataContext ) {
        if ( columnReference == null ) { // || literalIndex == null ) {
            return switch ( operator ) {
                case AND -> {
                    for ( Condition c : operands ) {
                        if ( !c.matches( columnValues, columnTypes, dataContext ) ) {
                            yield false;
                        }
                    }
                    yield true;
                }
                case OR -> {
                    for ( Condition c : operands ) {
                        if ( c.matches( columnValues, columnTypes, dataContext ) ) {
                            yield true;
                        }
                    }
                    yield false;
                }
                default -> throw new GenericRuntimeException( operator + " not supported in condition without columnReference" );
            };
        }
        // don't allow comparison of files and return false if Objects are not comparable
        /*if ( columnValues[columnReference] == null ) {
            return false;
        }*/
        PolyValue columnValue = columnValues.get( columnReference );//don't do the projectionMapping here
        AlgDataTypeField polyType = columnTypes.get( columnReference );
        switch ( operator ) {
            case IS_NULL:
                return columnValue == null || columnValue.isNull();
            case IS_NOT_NULL:
                return columnValue != null && !columnValue.isNull();
        }
        if ( columnValue == null || columnValue.isNull() ) {
            //if there is no null check and the column value is null, any check on the column value would return false
            return false;
        }
        PolyValue parameterValue = getParamValue( dataContext );
        if ( parameterValue == null || parameterValue.isNull() ) {
            //WHERE x = null is always false, see https://stackoverflow.com/questions/9581745/sql-is-null-and-null
            return false;
        }
        /*if ( columnValue.isNumber() && parameterValue.isNumber() ) {
            columnValue = columnValue;//.doubleValue();
            parameterValue = parameterValue;//).doubleValue();
        }*/

        int comparison;

        /*if ( parameterValue instanceof Calendar ) {
            //could be improved with precision..
            switch ( polyType ) {
                case DATE:
                    LocalDate ld = LocalDate.ofEpochDay( (Integer) columnValue );
                    comparison = ld.compareTo( ((GregorianCalendar) parameterValue).toZonedDateTime().toLocalDate() );
                    break;
                case TIME:
                    //see https://howtoprogram.xyz/2017/02/11/convert-milliseconds-localdatetime-java/
                    LocalTime dt = Instant.ofEpochMilli( (Integer) columnValue ).atZone( DateTimeUtils.UTC_ZONE.toZoneId() ).toLocalTime();
                    comparison = dt.compareTo( ((GregorianCalendar) parameterValue).toZonedDateTime().toLocalTime() );
                    break;
                case TIMESTAMP:
                    LocalDateTime ldt = Instant.ofEpochMilli( (Long) columnValue ).atZone( DateTimeUtils.UTC_ZONE.toZoneId() ).toLocalDateTime();
                    comparison = ldt.compareTo( ((GregorianCalendar) parameterValue).toZonedDateTime().toLocalDateTime() );
                    break;
                default:
                    comparison = ((Comparable) columnValue).compareTo( parameterValue );
            }
        } else if ( FileHelper.isSqlDateOrTimeOrTS( parameterValue ) ) {
            switch ( polyType ) {
                case TIME:
                case DATE:
                    comparison = Long.valueOf( (Integer) columnValue ).compareTo( FileHelper.sqlToLong( parameterValue ) );
                    break;
                case TIMESTAMP:
                default:
                    comparison = ((Comparable) columnValue).compareTo( FileHelper.sqlToLong( parameterValue ) );
            }
        } else {
            comparison = ((Comparable) columnValue).compareTo( parameterValue );
        }*/
        comparison = columnValue.compareTo( parameterValue );

        return switch ( operator ) {
            case AND -> {
                for ( Condition c : operands ) {
                    if ( !c.matches( columnValues, columnTypes, dataContext ) ) {
                        yield false;
                    }
                }
                yield true;
            }
            case OR -> {
                for ( Condition c : operands ) {
                    if ( c.matches( columnValues, columnTypes, dataContext ) ) {
                        yield true;
                    }
                }
                yield false;
            }
            case EQUALS -> comparison == 0;
            case NOT_EQUALS -> comparison != 0;
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUAL -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUAL -> comparison <= 0;
            case LIKE -> like( columnValue, parameterValue );
            default -> throw new GenericRuntimeException( operator + " comparison not supported by file adapter." );
        };
    }


    public void adjust( Integer[] projectionMapping ) {
        this.columnReference = projectionMapping[columnReference];
    }

}
