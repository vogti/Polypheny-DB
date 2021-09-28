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

package org.polypheny.db.adapter.cottontail.enumberable;

import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.Types;
import org.polypheny.db.adapter.DataContext;
import org.polypheny.db.adapter.cottontail.CottontailWrapper;
import org.polypheny.db.adapter.cottontail.util.CottontailTypeUtil;
import org.vitrivr.cottontail.client.iterators.TupleIterator;
import org.vitrivr.cottontail.client.language.basics.Constants;
import org.vitrivr.cottontail.grpc.CottontailGrpc;
import org.vitrivr.cottontail.grpc.CottontailGrpc.*;
import org.vitrivr.cottontail.grpc.CottontailGrpc.BatchInsertMessage.Insert;
import org.vitrivr.cottontail.grpc.CottontailGrpc.InsertMessage.InsertElement;
import org.vitrivr.cottontail.grpc.CottontailGrpc.Projection.ProjectionElement;

import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;

public class CottontailEnumerableFactory {
    /**
     * Method signature for Query statements.
     */
    public static final Method CREATE_QUERY_METHOD = Types.lookupMethod(
            CottontailEnumerableFactory.class,
            "query",
            String.class, String.class, Map.class, Map.class, Function1.class, Function1.class, Function1.class, DataContext.class, CottontailWrapper.class );

    /**
     * Method signature for INSERT of values.
     */
    public static final Method CREATE_INSERT_VALUES = Types.lookupMethod(
            CottontailEnumerableFactory.class,
            "insertFromValues",
            String.class, String.class, List.class, DataContext.class, CottontailWrapper.class );

    /**
     * Method signature for INSERT for prepared statements.
     */
    public static final Method CREATE_INSERT_PREPARED = Types.lookupMethod(
            CottontailEnumerableFactory.class,
            "insertFromPreparedStatements",
            String.class, String.class, Function1.class, DataContext.class, CottontailWrapper.class );

    /**
     *
     * @param from
     * @param schema
     * @param projection
     * @param whereBuilder
     * @param limitBuilder
     * @param offsetBuilder
     * @param dataContext
     * @param wrapper
     * @return
     */
    public static CottontailQueryEnumerable<Object> query(
            String from,
            String schema,
            Map<Object, String> projection,
            Map<String, String> orderBy,
            Function1<Map<Long, Object>, Integer> limitBuilder,
            Function1<Map<Long, Object>, Integer> offsetBuilder,
            Function1<Map<Long, Object>, Where> whereBuilder,
            DataContext dataContext,
            CottontailWrapper wrapper
    ) {

        /* Begin or continue Cottontail DB transaction. */
        final TransactionId txId = wrapper.beginOrContinue( dataContext.getStatement().getTransaction() );

        /* Build SELECT messages and create enumerable. */
        TupleIterator queryResponseIterator;
        if ( dataContext.getParameterValues().size() < 2 ) {
            final Map<Long, Object> parameterValues;
            if ( dataContext.getParameterValues().size() == 0 ) {
                parameterValues = new HashMap<>();
            } else {
                parameterValues = dataContext.getParameterValues().get( 0 );
            }

            Integer limit = null;
            if ( limitBuilder != null ) {
                limit = limitBuilder.apply( parameterValues );
            }

            Integer offset = null;
            if ( offsetBuilder != null ) {
                offset = offsetBuilder.apply( parameterValues );
            }

            final Query query = buildSingleQuery( from, schema, projection, orderBy, limit, offset, whereBuilder, parameterValues );
            queryResponseIterator = wrapper.query( QueryMessage.newBuilder().setTxId( txId ).setQuery( query ).build() );
        } else {
            BatchedQueryMessage.Builder batchedQueryMessageBuilder = BatchedQueryMessage.newBuilder().setTxId( txId );
            for ( Map<Long, Object> parameterValues : dataContext.getParameterValues() ) {

                Integer limit = null;
                if ( limitBuilder != null ) {
                    limit = limitBuilder.apply( parameterValues );
                }

                Integer offset = null;
                if ( offsetBuilder != null ) {
                    offset = offsetBuilder.apply( parameterValues );
                }

                final Query query = buildSingleQuery( from, schema, projection, orderBy, limit, offset, whereBuilder, parameterValues );
                batchedQueryMessageBuilder.addQuery( query );
            }

            queryResponseIterator = wrapper.batchedQuery( batchedQueryMessageBuilder.build() );
        }

        return new CottontailQueryEnumerable<Object>( queryResponseIterator );
    }

    /**
     * Used via reflection
     *
     * @param from
     * @param schema
     * @param projection
     * @param whereBuilder
     * @param limit
     * @param offset
     * @param parameterValues
     * @return
     */
    private static Query buildSingleQuery(
            String from,
            String schema,
            Map<Object, String> projection,
            Map<String, String> order,
            Integer limit,
            Integer offset,
            Function1<Map<Long, Object>, Where> whereBuilder,
            Map<Long, Object> parameterValues
    ) {
        Query.Builder queryBuilder = Query.newBuilder();

        queryBuilder.setFrom( CottontailTypeUtil.fromFromTableAndSchema( from, schema ) );

        if ( limit != null ) {
            queryBuilder.setLimit( limit );
        }

        if ( offset != null ) {
            queryBuilder.setSkip( offset );
        }

        /* Parse and translate projection clause (if available). */
        if ( projection != null && !projection.isEmpty() ) {
            final Projection.Builder projBuilder = queryBuilder.getProjectionBuilder();
            for ( Entry<Object, String> p : projection.entrySet() ) {
                final Object key = p.getKey();
                final String value = p.getValue();
                if (key instanceof String) {
                    final ProjectionElement.Builder ele = projBuilder.addElementsBuilder().setColumn( ColumnName.newBuilder().setName( (String) key ) );
                    if (value != null) {
                        ele.setAlias( ColumnName.newBuilder().setName( value ) );
                    }
                } else if (key instanceof Function1) {
                    /* Not exactly beautiful i know ;-) */
                    projBuilder.addElements(((Function1<Map<Long, Object>, ProjectionElement>) key).apply(parameterValues));
                }
            }
        }

        /* Add WHERE clause to query. */
        if ( whereBuilder != null ) {
            queryBuilder.setWhere( whereBuilder.apply( parameterValues ) );
        }

        /* Add ORDER BY to query. */
        if (order != null && !order.isEmpty()) {
            final Order.Builder orderBuilder = queryBuilder.getOrderBuilder();
            for ( Entry<String, String> p : order.entrySet() ) {
                orderBuilder.addComponentsBuilder()
                        .setColumn( ColumnName.newBuilder().setName( p.getKey() ).build() )
                        .setDirection( Order.Direction.valueOf( p.getValue() ) );
            }
        }

        return queryBuilder.build();
    }

    @SuppressWarnings("unused") // Used via reflection
    public static AbstractEnumerable<Object> insertFromValues(
            String from,
            String schema,
            List<Map<String, Literal>> values,
            DataContext dataContext,
            CottontailWrapper wrapper
    ) {
        /* Begin or continue Cottontail DB transaction. */
        final TransactionId txId = wrapper.beginOrContinue( dataContext.getStatement().getTransaction() );

        /* Build INSERT messages and create enumerable. */
        final CottontailGrpc.From from_ = CottontailTypeUtil.fromFromTableAndSchema( from, schema );
        final List<InsertMessage> insertMessages = new ArrayList<>( values.size() );
        for ( Map<String, CottontailGrpc.Literal> value : values ) {
            final InsertMessage.Builder message = InsertMessage.newBuilder().setFrom( from_ ).setTxId( txId );
            for ( Entry<String, Literal> e : value.entrySet() ) {
                message.addElements( InsertElement.newBuilder().setColumn( ColumnName.newBuilder().setName( e.getKey() ) ).setValue( e.getValue() ).build() );
            }
            insertMessages.add( message.build() );
        }

        return new CottontailInsertEnumerable<>( insertMessages, wrapper );
    }

    @SuppressWarnings("unused") // Used via reflection
    public static AbstractEnumerable<Object> insertFromPreparedStatements(
            String from,
            String schema,
            Function1<Map<Long, Object>, Map<String, CottontailGrpc.Literal>> tupleBuilder,
            DataContext dataContext,
            CottontailWrapper wrapper
    ) {
        /* Begin or continue Cottontail DB transaction. */
        final TransactionId txId = wrapper.beginOrContinue( dataContext.getStatement().getTransaction() );

        /* Build INSERT messages and create enumerable. */
        final CottontailGrpc.From from_ = CottontailTypeUtil.fromFromTableAndSchema( from, schema );
        if ( dataContext.getParameterValues().size() == 0 ) {
            final List<InsertMessage> insertMessages = new LinkedList<>();
            final InsertMessage.Builder insert = InsertMessage.newBuilder().setFrom( from_ ).setTxId( txId );
            final Map<String, Literal> values = tupleBuilder.apply( new HashMap<>() );
            for ( Entry<String, Literal> e : values.entrySet() ) {
                insert.addElements( InsertElement.newBuilder().setColumn( ColumnName.newBuilder().setName( e.getKey() ) ).setValue( e.getValue() ) );
            }
            insertMessages.add( insert.build() );
            return new CottontailInsertEnumerable<>( insertMessages, wrapper );
        } else {
            final List<BatchInsertMessage> insertMessages = new LinkedList<>();
            BatchInsertMessage.Builder builder = BatchInsertMessage.newBuilder().setFrom( from_ ).setTxId( txId );

            /* Add columns to BatchInsertMessage */
            final List<Map<Long,Object>> parameterValues = dataContext.getParameterValues();
            for (Entry<String, Literal> e: tupleBuilder.apply( parameterValues.get(0) ).entrySet()) {
                final ColumnName name = ColumnName.newBuilder().setName( e.getKey() ).build();
                builder.addColumns( name );
            }

            /* Start to track message size. */
            final int basicSize = builder.clone().build().getSerializedSize();
            int messageSize = basicSize;

            /* Add values to BatchInsertMessage. */
            for ( Map<Long, Object> row : parameterValues ) {
                final Insert.Builder insertBuilder = Insert.newBuilder();
                for ( Entry<String, Literal> e : tupleBuilder.apply( row ).entrySet() ) {
                    insertBuilder.addValues( e.getValue()  );
                }
                final Insert insert = insertBuilder.build();

                /* Check if maximum message size is exceeded. If so, build and add BatchInsertMessage to list. */
                if (messageSize + insert.getSerializedSize() >= Constants.MAX_PAGE_SIZE_BYTES) {
                    insertMessages.add(builder.build());
                    builder = builder.clone().clearInserts();
                    messageSize = basicSize;
                }
                messageSize += insert.getSerializedSize();
                builder.addInserts( insert );
            }

            /* Add file message. */
            if (builder.getInsertsCount() > 0) {
                insertMessages.add(builder.build());
            }

            return new CottontailBatchInsertEnumerable<>( insertMessages, wrapper );
        }
    }
}