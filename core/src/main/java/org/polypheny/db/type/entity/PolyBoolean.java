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

package org.polypheny.db.type.entity;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.activej.serializer.annotations.Deserialize;
import io.activej.serializer.annotations.Serialize;
import io.activej.serializer.annotations.SerializeNullable;
import java.io.IOException;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.polypheny.db.type.PolySerializable;
import org.polypheny.db.type.PolyType;

@EqualsAndHashCode(callSuper = true)
@Value
public class PolyBoolean extends PolyValue {

    public static final PolyBoolean TRUE = PolyBoolean.of( true );
    public static final PolyBoolean FALSE = PolyBoolean.of( false );

    @Serialize
    @SerializeNullable
    public Boolean value;


    public PolyBoolean( @Deserialize("value") Boolean value ) {
        super( PolyType.BOOLEAN );
        this.value = value;
    }


    public static PolyBoolean of( Boolean value ) {
        return new PolyBoolean( value );
    }


    public static PolyBoolean of( boolean value ) {
        return new PolyBoolean( value );
    }


    @Override
    public Expression asExpression() {
        return Expressions.new_( PolyBoolean.class, Expressions.constant( value ) );
    }


    @Override
    public int compareTo( @NotNull PolyValue o ) {
        if ( isSameType( o ) ) {
            return ObjectUtils.compare( value, o.asBoolean().value );
        }
        return -1;
    }


    public static class PolyBooleanTypeAdapter extends TypeAdapter<PolyBoolean> {

        @Override
        public void write( JsonWriter out, PolyBoolean value ) throws IOException {
            out.name( "value" );
            out.value( value.value );
        }


        @Override
        public PolyBoolean read( JsonReader in ) throws IOException {
            in.nextName();
            return PolyBoolean.of( in.nextBoolean() );
        }

    }


    @Override
    public PolySerializable copy() {
        return null;
    }


    @Override
    public String toString() {
        return value.toString();
    }

}
