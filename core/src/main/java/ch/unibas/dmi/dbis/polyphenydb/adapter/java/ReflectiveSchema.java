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

package ch.unibas.dmi.dbis.polyphenydb.adapter.java;


import ch.unibas.dmi.dbis.polyphenydb.DataContext;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelReferentialConstraint;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataType;
import ch.unibas.dmi.dbis.polyphenydb.rel.type.RelDataTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.schema.Function;
import ch.unibas.dmi.dbis.polyphenydb.schema.ScannableTable;
import ch.unibas.dmi.dbis.polyphenydb.schema.Schema;
import ch.unibas.dmi.dbis.polyphenydb.schema.SchemaPlus;
import ch.unibas.dmi.dbis.polyphenydb.schema.Schemas;
import ch.unibas.dmi.dbis.polyphenydb.schema.Statistic;
import ch.unibas.dmi.dbis.polyphenydb.schema.Statistics;
import ch.unibas.dmi.dbis.polyphenydb.schema.Table;
import ch.unibas.dmi.dbis.polyphenydb.schema.TableMacro;
import ch.unibas.dmi.dbis.polyphenydb.schema.TranslatableTable;
import ch.unibas.dmi.dbis.polyphenydb.schema.impl.AbstractSchema;
import ch.unibas.dmi.dbis.polyphenydb.schema.impl.AbstractTableQueryable;
import ch.unibas.dmi.dbis.polyphenydb.schema.impl.ReflectiveFunctionBase;
import ch.unibas.dmi.dbis.polyphenydb.util.BuiltInMethod;
import ch.unibas.dmi.dbis.polyphenydb.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.Primitive;
import org.apache.calcite.linq4j.tree.Types;


/**
 * Implementation of {@link Schema} that exposes the public fields and methods in a Java object.
 */
public class ReflectiveSchema extends AbstractSchema {

    private final Class clazz;
    private Object target;
    private Map<String, Table> tableMap;
    private Multimap<String, Function> functionMap;


    /**
     * Creates a ReflectiveSchema.
     *
     * @param target Object whose fields will be sub-objects of the schema
     */
    public ReflectiveSchema( Object target ) {
        super();
        this.clazz = target.getClass();
        this.target = target;
    }


    @Override
    public String toString() {
        return "ReflectiveSchema(target=" + target + ")";
    }


    /**
     * Returns the wrapped object.
     *
     * May not appear to be used, but is used in generated code via {@link ch.unibas.dmi.dbis.polyphenydb.util.BuiltInMethod#REFLECTIVE_SCHEMA_GET_TARGET}.
     */
    public Object getTarget() {
        return target;
    }


    @Override
    protected Map<String, Table> getTableMap() {
        if ( tableMap == null ) {
            tableMap = createTableMap();
        }
        return tableMap;
    }


    private Map<String, Table> createTableMap() {
        final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder();
        for ( Field field : clazz.getFields() ) {
            final String fieldName = field.getName();
            final Table table = fieldRelation( field );
            if ( table == null ) {
                continue;
            }
            builder.put( fieldName, table );
        }
        Map<String, Table> tableMap = builder.build();
        // Unique-Key - Foreign-Key
        for ( Field field : clazz.getFields() ) {
            if ( RelReferentialConstraint.class.isAssignableFrom( field.getType() ) ) {
                RelReferentialConstraint rc;
                try {
                    rc = (RelReferentialConstraint) field.get( target );
                } catch ( IllegalAccessException e ) {
                    throw new RuntimeException( "Error while accessing field " + field, e );
                }
                FieldTable table = (FieldTable) tableMap.get( Util.last( rc.getSourceQualifiedName() ) );
                assert table != null;
                table.statistic = Statistics.of( ImmutableList.copyOf( Iterables.concat( table.getStatistic().getReferentialConstraints(), Collections.singleton( rc ) ) ) );
            }
        }
        return tableMap;
    }


    @Override
    protected Multimap<String, Function> getFunctionMultimap() {
        if ( functionMap == null ) {
            functionMap = createFunctionMap();
        }
        return functionMap;
    }


    private Multimap<String, Function> createFunctionMap() {
        final ImmutableMultimap.Builder<String, Function> builder = ImmutableMultimap.builder();
        for ( Method method : clazz.getMethods() ) {
            final String methodName = method.getName();
            if ( method.getDeclaringClass() == Object.class || methodName.equals( "toString" ) ) {
                continue;
            }
            if ( TranslatableTable.class.isAssignableFrom( method.getReturnType() ) ) {
                final TableMacro tableMacro = new MethodTableMacro( this, method );
                builder.put( methodName, tableMacro );
            }
        }
        return builder.build();
    }


    /**
     * Returns an expression for the object wrapped by this schema (not the schema itself).
     */
    Expression getTargetExpression( SchemaPlus parentSchema, String name ) {
        return Types.castIfNecessary(
                target.getClass(),
                Expressions.call(
                        Schemas.unwrap( getExpression( parentSchema, name ), ReflectiveSchema.class ),
                        BuiltInMethod.REFLECTIVE_SCHEMA_GET_TARGET.method ) );
    }


    /**
     * Returns a table based on a particular field of this schema. If the field is not of the right type to be a relation, returns null.
     */
    private <T> Table fieldRelation( final Field field ) {
        final Type elementType = getElementType( field.getType() );
        if ( elementType == null ) {
            return null;
        }
        Object o;
        try {
            o = field.get( target );
        } catch ( IllegalAccessException e ) {
            throw new RuntimeException( "Error while accessing field " + field, e );
        }
        @SuppressWarnings("unchecked") final Enumerable<T> enumerable = toEnumerable( o );
        return new FieldTable<>( field, elementType, enumerable );
    }


    /**
     * Deduces the element type of a collection; same logic as {@link #toEnumerable}
     */
    private static Type getElementType( Class clazz ) {
        if ( clazz.isArray() ) {
            return clazz.getComponentType();
        }
        if ( Iterable.class.isAssignableFrom( clazz ) ) {
            return Object.class;
        }
        return null; // not a collection/array/iterable
    }


    private static Enumerable toEnumerable( final Object o ) {
        if ( o.getClass().isArray() ) {
            if ( o instanceof Object[] ) {
                return Linq4j.asEnumerable( (Object[]) o );
            } else {
                return Linq4j.asEnumerable( Primitive.asList( o ) );
            }
        }
        if ( o instanceof Iterable ) {
            return Linq4j.asEnumerable( (Iterable) o );
        }
        throw new RuntimeException( "Cannot convert " + o.getClass() + " into a Enumerable" );
    }


    /**
     * Table that is implemented by reading from a Java object.
     */
    private static class ReflectiveTable extends AbstractQueryableTable implements Table, ScannableTable {

        private final Type elementType;
        private final Enumerable enumerable;


        ReflectiveTable( Type elementType, Enumerable enumerable ) {
            super( elementType );
            this.elementType = elementType;
            this.enumerable = enumerable;
        }


        @Override
        public RelDataType getRowType( RelDataTypeFactory typeFactory ) {
            return ((JavaTypeFactory) typeFactory).createType( elementType );
        }


        @Override
        public Statistic getStatistic() {
            return Statistics.UNKNOWN;
        }


        @Override
        public Enumerable<Object[]> scan( DataContext root ) {
            if ( elementType == Object[].class ) {
                //noinspection unchecked
                return enumerable;
            } else {
                //noinspection unchecked
                return enumerable.select( new FieldSelector( (Class) elementType ) );
            }
        }


        @Override
        public <T> Queryable<T> asQueryable( DataContext dataContext, SchemaPlus schema, String tableName ) {
            return new AbstractTableQueryable<T>( dataContext, schema, this, tableName ) {
                @Override
                @SuppressWarnings("unchecked")
                public Enumerator<T> enumerator() {
                    return (Enumerator<T>) enumerable.enumerator();
                }
            };
        }
    }


    /**
     * Table macro based on a Java method.
     */
    private static class MethodTableMacro extends ReflectiveFunctionBase implements TableMacro {

        private final ReflectiveSchema schema;


        MethodTableMacro( ReflectiveSchema schema, Method method ) {
            super( method );
            this.schema = schema;
            assert TranslatableTable.class.isAssignableFrom( method.getReturnType() ) : "Method should return TranslatableTable so the macro can be expanded";
        }


        public String toString() {
            return "Member {method=" + method + "}";
        }


        @Override
        public TranslatableTable apply( final List<Object> arguments ) {
            try {
                final Object o = method.invoke( schema.getTarget(), arguments.toArray() );
                return (TranslatableTable) o;
            } catch ( IllegalAccessException | InvocationTargetException e ) {
                throw new RuntimeException( e );
            }
        }
    }


    /**
     * Table based on a Java field.
     *
     * @param <T> element type
     */
    private static class FieldTable<T> extends ReflectiveTable {

        private final Field field;
        private Statistic statistic;


        FieldTable( Field field, Type elementType, Enumerable<T> enumerable ) {
            this( field, elementType, enumerable, Statistics.UNKNOWN );
        }


        FieldTable( Field field, Type elementType, Enumerable<T> enumerable, Statistic statistic ) {
            super( elementType, enumerable );
            this.field = field;
            this.statistic = statistic;
        }


        public String toString() {
            return "Relation {field=" + field.getName() + "}";
        }


        @Override
        public Statistic getStatistic() {
            return statistic;
        }


        @Override
        public Expression getExpression( SchemaPlus schema, String tableName, Class clazz ) {
            return Expressions.field( schema.unwrap( ReflectiveSchema.class ).getTargetExpression( schema.getParentSchema(), schema.getName() ), field );
        }
    }


    /**
     * Function that returns an array of a given object's field values.
     */
    private static class FieldSelector implements Function1<Object, Object[]> {

        private final Field[] fields;


        FieldSelector( Class elementType ) {
            this.fields = elementType.getFields();
        }


        @Override
        public Object[] apply( Object o ) {
            try {
                final Object[] objects = new Object[fields.length];
                for ( int i = 0; i < fields.length; i++ ) {
                    objects[i] = fields[i].get( o );
                }
                return objects;
            } catch ( IllegalAccessException e ) {
                throw new RuntimeException( e );
            }
        }
    }
}

