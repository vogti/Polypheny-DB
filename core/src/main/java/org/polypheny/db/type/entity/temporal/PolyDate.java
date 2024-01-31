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

package org.polypheny.db.type.entity.temporal;

import com.fasterxml.jackson.core.JsonToken;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import lombok.Getter;
import lombok.Value;
import org.apache.calcite.avatica.util.DateTimeUtils;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.polypheny.db.functions.TemporalFunctions;
import org.polypheny.db.type.PolySerializable;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.type.entity.PolyValue;
import org.polypheny.db.type.entity.category.PolyNumber;
import org.polypheny.db.type.entity.category.PolyTemporal;

@Getter
@Value
public class PolyDate extends PolyTemporal {

    public static final DateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );

    public Long millisSinceEpoch;


    public PolyDate( Long millisSinceEpoch ) {
        super( PolyType.DATE );
        this.millisSinceEpoch = millisSinceEpoch == null ? null : millisSinceEpoch - millisSinceEpoch % DateTimeUtils.MILLIS_PER_DAY; // move to 00:00:00
    }


    public static PolyDate of( Long millisSinceEpoch ) {
        return new PolyDate( millisSinceEpoch );
    }

    public static PolyDate of( PolyNumber number ) {
        return new PolyDate( number.longValue() );
    }


    public static PolyDate of( Number number ) {
        return new PolyDate( number.longValue() );
    }


    public static PolyDate ofNullable( Number number ) {
        return number == null ? new PolyDate( null ) : of( number );
    }


    public static PolyDate ofNullable( java.sql.Date date ) {
        return PolyDate.of( date );
    }


    public static PolyDate ofDays( int days ) {
        return new PolyDate( days * 24L * 60 * 60 * 1000 );
    }


    public Date asDefaultDate() {
        return new Date( millisSinceEpoch );
    }


    public java.sql.Date asSqlDate() {
        return new java.sql.Date( millisSinceEpoch );
    }


    public static PolyDate of( Date date ) {
        return new PolyDate( TemporalFunctions.dateToLong( date ) );
    }


    public static PolyDate convert( Object value ) {
        if ( value == null ) {
            return null;
        }
        if ( value instanceof PolyValue poly ) {
            if ( poly.isDate() ) {
                return poly.asDate();
            } else if ( poly.isNumber() ) {
                return ofDays( poly.asNumber().intValue() );
            }
        }
        throw new NotImplementedException( "convert value to Boolean" );
    }


    @Override
    public boolean equals( Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( !(o instanceof PolyValue value) ) {
            return false;
        }

        if ( value.isDate() ) {
            return compareTo( value.asDate() ) == 0;
        }

        return super.equals( o );
    }


    @Override
    public String toJson() {
        return millisSinceEpoch == null ? JsonToken.VALUE_NULL.asString() : dateFormat.format( new Date( millisSinceEpoch ) );
    }


    @Override
    public int compareTo( @NotNull PolyValue o ) {
        if ( millisSinceEpoch == null ) {
            return -1;
        }
        if ( !isDate() ) {
            return -1;
        }

        return Long.compare( millisSinceEpoch, o.asDate().millisSinceEpoch );
    }


    @Override
    public String toString() {
        return toJson();
    }


    @Override
    public Expression asExpression() {
        return Expressions.new_( PolyDate.class, Expressions.constant( millisSinceEpoch ) );
    }


    @Override
    public @Nullable Long deriveByteSize() {
        return null;
    }


    @Override
    public Object toJava() {
        return getDaysSinceEpoch();
    }


    @Override
    public PolySerializable copy() {
        return PolySerializable.deserialize( serialize(), PolyDate.class );
    }

}