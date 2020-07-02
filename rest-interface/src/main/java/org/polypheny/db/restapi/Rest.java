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

package org.polypheny.db.restapi;


import com.google.common.annotations.VisibleForTesting;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.avatica.MetaImpl;
import org.apache.commons.lang3.time.StopWatch;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.UnknownDatabaseException;
import org.polypheny.db.catalog.exceptions.UnknownSchemaException;
import org.polypheny.db.catalog.exceptions.UnknownUserException;
import org.polypheny.db.jdbc.PolyphenyDbSignature;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.plan.RelOptPlanner;
import org.polypheny.db.prepare.PolyphenyDbCatalogReader;
import org.polypheny.db.prepare.Prepare.PreparingTable;
import org.polypheny.db.rel.RelCollation;
import org.polypheny.db.rel.RelCollationImpl;
import org.polypheny.db.rel.RelCollations;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.RelRoot;
import org.polypheny.db.rel.core.AggregateCall;
import org.polypheny.db.rel.core.JoinRelType;
import org.polypheny.db.rel.core.Sort;
import org.polypheny.db.rel.core.TableModify;
import org.polypheny.db.rel.logical.LogicalTableModify;
import org.polypheny.db.rel.type.RelDataType;
import org.polypheny.db.rel.type.RelDataTypeField;
import org.polypheny.db.restapi.models.requests.RequestInfo;
import org.polypheny.db.rex.RexBuilder;
import org.polypheny.db.rex.RexLiteral;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.sql.SqlAggFunction;
import org.polypheny.db.sql.SqlKind;
import org.polypheny.db.sql.SqlOperator;
import org.polypheny.db.sql.fun.SqlStdOperatorTable;
import org.polypheny.db.tools.RelBuilder;
import org.polypheny.db.tools.RelBuilder.GroupKey;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.transaction.TransactionManager;
import org.polypheny.db.type.PolyType;
import org.polypheny.db.util.ImmutableBitSet;
import org.polypheny.db.util.ImmutableIntList;
import org.polypheny.db.util.Pair;
import spark.Request;
import spark.Response;


@Slf4j
public class Rest {
    private final TransactionManager transactionManager;
    private final String databaseName;
    private final String userName;
    private final Catalog catalog;

    Rest( final TransactionManager transactionManager, final String userName, final String databaseName ) {
        this( Catalog.getInstance(), transactionManager, userName, databaseName );
    }

    @VisibleForTesting
    Rest( final Catalog catalog, final TransactionManager transactionManager, final String userName, final String databaseName ) {
        this.catalog = catalog;
        this.transactionManager = transactionManager;
        this.userName = userName;
        this.databaseName = databaseName;
    }


    Map<String, Object> processGetResource( final RequestInfo requestInfo, final Request req, final Response res ) {
        log.debug( "Starting to process resource request. Session ID: {}.", req.session().id() );
        Transaction transaction = getTransaction( false );
//        transaction.resetQueryProcessor();
        RelBuilder relBuilder = RelBuilder.create( transaction );
//        JavaTypeFactory typeFactory = new JavaTypeFactoryImpl();
        JavaTypeFactory typeFactory = transaction.getTypeFactory();
        RexBuilder rexBuilder = new RexBuilder( typeFactory );

        relBuilder = this.tableScans( relBuilder, rexBuilder, requestInfo );

        List<RexNode> filters = this.filters( relBuilder, rexBuilder, requestInfo, req );
        if ( filters != null ) {
            relBuilder = relBuilder.filter( filters );
        }

        // Projections
        /*if ( requestInfo.getProjection() != null ) {
            List<RexNode> projectionInputRefs = new ArrayList<>();
            RelNode baseNodeForProjections = relBuilder.peek();
            for ( CatalogColumn catalogColumn : requestInfo.getProjection().left ) {
                int inputField = requestInfo.getColumnPosition( catalogColumn );
                RexNode inputRef = rexBuilder.makeInputRef( baseNodeForProjections, inputField );
                projectionInputRefs.add( inputRef );
            }

            relBuilder = relBuilder.project( projectionInputRefs, requestInfo.getProjection().right );
            log.debug( "Added projections to relation. Session ID: {}.", req.session().id() );
        } else {
            log.debug( "No projections to add. Session ID: {}.", req.session().id() );
        }*/

        if ( ! requestInfo.getAggregateFunctions().isEmpty() ) {
            RelNode baseNodeForAggregation = relBuilder.peek();
            int groupCount = requestInfo.getGroupings().size();
            List<AggregateCall> aggregateCalls = new ArrayList<>();
            // FIXME
            List<Pair<CatalogColumn, SqlAggFunction>> aggFunctions = requestInfo.getAggregateFunctions();
            for ( Pair<CatalogColumn, SqlAggFunction> aggFunction : aggFunctions ) {
                List<Integer> inputFields = new ArrayList<>();
                inputFields.add( requestInfo.getColumnPosition( aggFunction.left ) );
                int fieldNameIndex = requestInfo.getProjection().left.indexOf( aggFunction.left );
                String fieldName = requestInfo.getProjection().right.get( fieldNameIndex );
                AggregateCall aggregateCall = AggregateCall.create( aggFunction.right, false, false, inputFields, -1, RelCollations.EMPTY, groupCount, baseNodeForAggregation, null, fieldName );
                aggregateCalls.add( aggregateCall );
            }

            List<Integer> groupByOrdinals = new ArrayList<>();
            for ( CatalogColumn column : requestInfo.getGroupings() ) {
                groupByOrdinals.add( requestInfo.getColumnPosition( column ) );
            }

            GroupKey groupKey = relBuilder.groupKey( ImmutableBitSet.of( groupByOrdinals ) );

            relBuilder = relBuilder.aggregate( groupKey, aggregateCalls );
        }

        // Projections
        if ( requestInfo.getProjection() != null ) {
            List<RexNode> projectionInputRefs = new ArrayList<>();
            RelNode baseNodeForProjections = relBuilder.peek();
            for ( CatalogColumn catalogColumn : requestInfo.getProjection().left ) {
                int inputField = requestInfo.getColumnPosition( catalogColumn );
                RexNode inputRef = rexBuilder.makeInputRef( baseNodeForProjections, inputField );
                projectionInputRefs.add( inputRef );
            }

            relBuilder = relBuilder.project( projectionInputRefs, requestInfo.getProjection().right, true );
            log.debug( "Added projections to relation. Session ID: {}.", req.session().id() );
        } else {
            log.debug( "No projections to add. Session ID: {}.", req.session().id() );
        }

        // Sorting, Limit and Offset
        if ( ( requestInfo.getSort() == null || requestInfo.getSort().size() == 0 ) && ( requestInfo.getLimit() >= 0 || requestInfo.getOffset() >= 0 ) ) {
            relBuilder = relBuilder.limit( requestInfo.getOffset(), requestInfo.getLimit() );
            log.debug( "Added limit and offset to relation. Session ID: {}.", req.session().id() );
        } else if ( requestInfo.getSort() != null && requestInfo.getSort().size() != 0 ) {
            List<RexNode> sortingNodes = new ArrayList<>();
            RelNode baseNodeForSorts = relBuilder.peek();
            for ( Pair<CatalogColumn, Boolean> sort : requestInfo.getSort() ) {
                int inputField = requestInfo.getColumnPosition( sort.left );
                RexNode inputRef = rexBuilder.makeInputRef( baseNodeForSorts, inputField );
                RexNode sortingNode;
                if ( sort.right ) {
                    RexNode innerNode = rexBuilder.makeCall( SqlStdOperatorTable.DESC, inputRef );
                    sortingNode = rexBuilder.makeCall( SqlStdOperatorTable.NULLS_FIRST, innerNode );
                } else {
                    sortingNode = rexBuilder.makeCall( SqlStdOperatorTable.NULLS_FIRST, inputRef );
                }

                sortingNodes.add( sortingNode );
            }

            relBuilder = relBuilder.sortLimit( requestInfo.getOffset(), requestInfo.getLimit(), sortingNodes );
            log.debug( "Added sort, limit and offset to relation. Session ID: {}.", req.session().id() );
        } else {
            log.debug( "No sort, limit, or offset to add. Session ID: {}.", req.session().id() );
        }


        log.debug( "RelNodeBuilder: {}", relBuilder.toString() );
        RelNode relNode = relBuilder.build();
        log.debug( "RelNode was built." );

        // Wrap RelNode into a RelRoot
        final RelDataType rowType = relNode.getRowType();
        final List<Pair<Integer, String>> fields = Pair.zip( ImmutableIntList.identity( rowType.getFieldCount() ), rowType.getFieldNames() );
        final RelCollation collation =
                relNode instanceof Sort
                        ? ((Sort) relNode).collation
                        : RelCollations.EMPTY;
        RelRoot root = new RelRoot( relNode, relNode.getRowType(), SqlKind.SELECT, fields, collation );
        log.debug( "RelRoot was built." );

        Map<String, Object> finalResult = executeAndTransformRelAlg( root, transaction );

        finalResult.put( "uri", req.uri() );
        finalResult.put( "query", req.queryString() );
        return finalResult;

//        return null;
    }

    Map<String, Object> processPatchResource( final RequestInfo requestInfo, final Request req, final Response res ) {
        Transaction transaction = getTransaction();
        RelBuilder relBuilder = RelBuilder.create( transaction );
        JavaTypeFactory typeFactory = transaction.getTypeFactory();
        RexBuilder rexBuilder = new RexBuilder( typeFactory );

        PolyphenyDbCatalogReader catalogReader = transaction.getCatalogReader();
        PreparingTable table = catalogReader.getTable( Arrays.asList( requestInfo.getTables().get( 0 ).schemaName, requestInfo.getTables().get( 0 ).name ) );

        relBuilder = this.tableScans( relBuilder, rexBuilder, requestInfo );
        List<RexNode> filters = this.filters( relBuilder, rexBuilder, requestInfo, req );
        if ( filters != null ) {
            relBuilder = relBuilder.filter( filters );
        }

        // Values
        RelDataType tableRowType = table.getRowType();
        List<RelDataTypeField> tableRows = tableRowType.getFieldList();

        List<String> valueColumnNames = new ArrayList<>();
        List<RexNode> rexValues = new ArrayList<>();
        for ( Pair<CatalogColumn, Object> insertValue : requestInfo.getValues().get( 0 ) ) {
            valueColumnNames.add( insertValue.left.name );
            int columnPosition = requestInfo.getColumnPosition( insertValue.left );
            RelDataTypeField typeField = tableRows.get( columnPosition );
            rexValues.add( rexBuilder.makeLiteral( insertValue.right, typeField.getType(), true ) );
        }
        // Table Modify

        RelOptPlanner planner = transaction.getQueryProcessor().getPlanner();
        RelOptCluster cluster = RelOptCluster.create( planner, rexBuilder );

        RelNode relNode = relBuilder.build();
        TableModify tableModify = new LogicalTableModify(
                cluster,
                relNode.getTraitSet(),
                table,
                catalogReader,
                relNode,
                LogicalTableModify.Operation.UPDATE,
                valueColumnNames,
                rexValues,
                false
        );

        // Wrap RelNode into a RelRoot
        final RelDataType rowType = tableModify.getRowType();
        final List<Pair<Integer, String>> fields = Pair.zip( ImmutableIntList.identity( rowType.getFieldCount() ), rowType.getFieldNames() );
        final RelCollation collation =
                relNode instanceof Sort
                        ? ((Sort) relNode).collation
                        : RelCollations.EMPTY;
        RelRoot root = new RelRoot( tableModify, rowType, SqlKind.UPDATE, fields, collation );
        log.debug( "RelRoot was built." );

        Map<String, Object> finalResult = executeAndTransformRelAlg( root, transaction );
        return finalResult;
    }


    Map<String, Object> processDeleteResource( final RequestInfo requestInfo, final Request req, final Response res ) {
        Transaction transaction = getTransaction();
        RelBuilder relBuilder = RelBuilder.create( transaction );
        JavaTypeFactory typeFactory = transaction.getTypeFactory();
        RexBuilder rexBuilder = new RexBuilder( typeFactory );

        PolyphenyDbCatalogReader catalogReader = transaction.getCatalogReader();
        PreparingTable table = catalogReader.getTable( Arrays.asList( requestInfo.getTables().get( 0 ).schemaName, requestInfo.getTables().get( 0 ).name ) );

        relBuilder = this.tableScans( relBuilder, rexBuilder, requestInfo );
        List<RexNode> filters = this.filters( relBuilder, rexBuilder, requestInfo, req );
        if ( filters != null ) {
            relBuilder = relBuilder.filter( filters );
        }

        // Table Modify

        RelOptPlanner planner = transaction.getQueryProcessor().getPlanner();
        RelOptCluster cluster = RelOptCluster.create( planner, rexBuilder );

        RelNode relNode = relBuilder.build();
        TableModify tableModify = new LogicalTableModify(
                cluster,
                relNode.getTraitSet(),
                table,
                catalogReader,
                relNode,
                LogicalTableModify.Operation.DELETE,
                null,
                null,
                false
        );

        // Wrap RelNode into a RelRoot
        final RelDataType rowType = tableModify.getRowType();
        final List<Pair<Integer, String>> fields = Pair.zip( ImmutableIntList.identity( rowType.getFieldCount() ), rowType.getFieldNames() );
        final RelCollation collation =
                relNode instanceof Sort
                        ? ((Sort) relNode).collation
                        : RelCollations.EMPTY;
        RelRoot root = new RelRoot( tableModify, rowType, SqlKind.DELETE, fields, collation );
        log.debug( "RelRoot was built." );

        Map<String, Object> finalResult = executeAndTransformRelAlg( root, transaction );
        return finalResult;
    }


    Map<String, Object> processPutResource( final RequestInfo requestInfo, final Request req, final Response res ) {
        Transaction transaction = getTransaction();
        RelBuilder relBuilder = RelBuilder.create( transaction );
        JavaTypeFactory typeFactory = transaction.getTypeFactory();
        RexBuilder rexBuilder = new RexBuilder( typeFactory );

        PolyphenyDbCatalogReader catalogReader = transaction.getCatalogReader();
        PreparingTable table = catalogReader.getTable( Arrays.asList( requestInfo.getTables().get( 0 ).schemaName, requestInfo.getTables().get( 0 ).name ) );

        // Values
        RelDataType tableRowType = table.getRowType();
        List<RelDataTypeField> tableRows = tableRowType.getFieldList();

        List<Object> actualRexValues = new ArrayList<>();
        List<List<RexLiteral>> wrapperList = new ArrayList<>();
        // FIXME
        for ( List<Pair<CatalogColumn, Object>> rowsToInsert : requestInfo.getValues() ) {
            List<RexLiteral> rexValues = new ArrayList<>();
            for ( Pair<CatalogColumn, Object> insertValue : rowsToInsert ) {
                int columnPosition = requestInfo.getColumnPosition( insertValue.left );
                RelDataTypeField typeField = tableRows.get( columnPosition );
                rexValues.add( (RexLiteral) rexBuilder.makeLiteral( insertValue.right, typeField.getType(), true ) );
                actualRexValues.add( insertValue.right );
            }
            wrapperList.add( rexValues );
        }

        relBuilder = relBuilder.values( wrapperList, tableRowType );

        // Table Modify

        RelOptPlanner planner = transaction.getQueryProcessor().getPlanner();
        RelOptCluster cluster = RelOptCluster.create( planner, rexBuilder );

        RelNode relNode = relBuilder.build();
        TableModify tableModify = new LogicalTableModify(
                cluster,
                relNode.getTraitSet(),
                table,
                catalogReader,
                relNode,
                LogicalTableModify.Operation.INSERT,
                null,
                null,
                false
        );

        // Wrap RelNode into a RelRoot
        final RelDataType rowType = tableModify.getRowType();
        final List<Pair<Integer, String>> fields = Pair.zip( ImmutableIntList.identity( rowType.getFieldCount() ), rowType.getFieldNames() );
        final RelCollation collation =
                relNode instanceof Sort
                        ? ((Sort) relNode).collation
                        : RelCollations.EMPTY;
        RelRoot root = new RelRoot( tableModify, rowType, SqlKind.INSERT, fields, collation );
        log.debug( "RelRoot was built." );

        Map<String, Object> finalResult = executeAndTransformRelAlg( root, transaction );

        return finalResult;
    }

    @VisibleForTesting
    RelBuilder tableScans( RelBuilder relBuilder, RexBuilder rexBuilder, RequestInfo requestInfo ) {
        boolean firstTable = true;
        for ( CatalogTable catalogTable : requestInfo.getTables() ) {
            if ( firstTable ) {
                relBuilder = relBuilder.scan( catalogTable.schemaName, catalogTable.name );
                firstTable = false;
            } else {
                relBuilder = relBuilder
                        .scan( catalogTable.schemaName, catalogTable.name )
                        .join( JoinRelType.INNER, rexBuilder.makeLiteral( true ) );
            }
        }
        return relBuilder;
    }

    @VisibleForTesting
    List<RexNode> filters( RelBuilder relBuilder, RexBuilder rexBuilder, RequestInfo requestInfo, Request req ) {
        if ( requestInfo.getLiteralFilters() != null ) {
            log.debug( "Starting to process filters. Session ID: {}.", req.session().id() );
            List<RexNode> filterNodes = new ArrayList<>();
            RelNode baseNodeForFilters = relBuilder.peek();
            RelDataType filtersRowType = baseNodeForFilters.getRowType();
            List<RelDataTypeField> filtersRows = filtersRowType.getFieldList();
            for ( CatalogColumn catalogColumn : requestInfo.getLiteralFilters().keySet() ) {
                for ( Pair<SqlOperator, Object> filterOperationPair : requestInfo.getLiteralFilters().get( catalogColumn ) ) {
                    int columnPosition = requestInfo.getColumnPosition( catalogColumn );
                    RelDataTypeField typeField = filtersRows.get( columnPosition );
                    RexNode inputRef = rexBuilder.makeInputRef( baseNodeForFilters, columnPosition );
                    RexNode rightHandSide = rexBuilder.makeLiteral( filterOperationPair.right, typeField.getType(), true );
                    RexNode call = rexBuilder.makeCall( filterOperationPair.left, inputRef, rightHandSide );
                    filterNodes.add( call );
                }
            }

            log.debug( "Finished processing filters. Session ID: {}.", req.session().id() );
//            relBuilder = relBuilder.filter( filterNodes );
            log.debug( "Added filters to relation. Session ID: {}.", req.session().id() );
            return filterNodes;
        } else {
            log.debug( "No filters to add. Session ID: {}.", req.session().id() );
            return null;
        }

    }

    @VisibleForTesting
    PreparingTable getPreparingTable( RequestInfo requestInfo, Transaction transaction) {
        // RelOptTable
        PolyphenyDbCatalogReader catalogReader = transaction.getCatalogReader();
        PreparingTable table = catalogReader.getTable( Arrays.asList( requestInfo.getTables().get( 0 ).schemaName, requestInfo.getTables().get( 0 ).name ) );
        return table;
    }

    List<List<RexNode>> values( PreparingTable table, RexBuilder rexBuilder, RequestInfo requestInfo, Request req, Transaction transaction ) {
        // Values
        RelDataType tableRowType = table.getRowType();
        List<RelDataTypeField> tableRows = tableRowType.getFieldList();

        List<Object> actualRexValues = new ArrayList<>();
        List<List<RexNode>> wrapperList = new ArrayList<>();
        // FIXME
        for ( List<Pair<CatalogColumn, Object>> rowsToInsert : requestInfo.getValues() ) {
            List<RexNode> rexValues = new ArrayList<>();
            for ( Pair<CatalogColumn, Object> insertValue : rowsToInsert ) {
                int columnPosition = requestInfo.getColumnPosition( insertValue.left );
                RelDataTypeField typeField = tableRows.get( columnPosition );
                rexValues.add( rexBuilder.makeLiteral( insertValue.right, typeField.getType(), true ) );
                actualRexValues.add( insertValue.right );
            }
            wrapperList.add( rexValues );
        }
//        relBuilder = relBuilder.values( wrapperList, tableRowType );

        return wrapperList;
    }

    private Transaction getTransaction() {
        return getTransaction( false );
    }

    private Transaction getTransaction( boolean analyze ) {
        try {
            return transactionManager.startTransaction( userName, databaseName, analyze );
        } catch ( GenericCatalogException | UnknownUserException | UnknownDatabaseException | UnknownSchemaException e ) {
            throw new RuntimeException( "Error while starting transaction", e );
        }
    }

    Map<String, Object> executeAndTransformRelAlg( RelRoot relRoot, final Transaction transaction ) {
        // Prepare
        PolyphenyDbSignature signature = transaction.getQueryProcessor().prepareQuery( relRoot );
        log.debug( "RelRoot was prepared." );

        List<List<Object>> rows;
        try {
            @SuppressWarnings("unchecked") final Iterable<Object> iterable = signature.enumerable( transaction.getDataContext() );
            Iterator<Object> iterator = iterable.iterator();
            if ( relRoot.kind.belongsTo( SqlKind.DML ) ) {
                Object object;
                int rowsChanged = -1;
                while ( iterator.hasNext() ) {
                    object = iterator.next();
                    int num;
                    if ( object != null && object.getClass().isArray() ) {
                        Object[] o = (Object[]) object;
                        num = ((Number) o[0]).intValue();
                    } else if ( object != null ) {
                        num = ((Number) object).intValue();
                    } else {
                        throw new RuntimeException( "Result is null" );
                    }
                    // Check if num is equal for all stores
                    if ( rowsChanged != -1 && rowsChanged != num ) {
                        throw new RuntimeException( "The number of changed rows is not equal for all stores!" );
                    }
                    rowsChanged = num;
                }
                rows = new LinkedList<>();
                LinkedList<Object> result = new LinkedList<>();
                result.add( rowsChanged );
                rows.add( result );
            } else {
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                rows = MetaImpl.collect( signature.cursorFactory, iterator, new ArrayList<>() );
                stopWatch.stop();
                signature.getExecutionTimeMonitor().setExecutionTime( stopWatch.getNanoTime() );
            }
            transaction.commit();
        } catch ( Exception | TransactionException e ) {
            log.error( "Caught exception while iterating the plan builder tree", e );
            try {
                transaction.rollback();
            } catch ( TransactionException transactionException ) {
                transactionException.printStackTrace();
            }
            return null;
        }

        return transformResultIterator( signature, rows );
    }


    Map<String, Object> transformResultIterator( PolyphenyDbSignature<?> signature, List<List<Object>> rows ) {
        List<Map<String, Object>> resultData = new ArrayList<>();

        try {
            /*CatalogTable catalogTable = null;
            if ( request.tableId != null ) {
                String[] t = request.tableId.split( "\\." );
                try {
                    catalogTable = catalog.getTable( this.databaseName, t[0], t[1] );
                } catch ( UnknownTableException | GenericCatalogException e ) {
                    log.error( "Caught exception", e );
                }
            }*/
            for ( List<Object> row : rows ) {
                Map<String, Object> temp = new HashMap<>();
                int counter = 0;
                for ( Object o: row ) {
                    if ( signature.rowType.getFieldList().get( counter ).getType().getPolyType().equals( PolyType.TIMESTAMP ) ) {
                        Long nanoSeconds = (Long) o;
                        LocalDateTime localDateTime = LocalDateTime.ofEpochSecond( nanoSeconds / 1000L, (int) (( nanoSeconds % 1000 ) * 1000), ZoneOffset.UTC );
//                        localDateTime.toString();
                        temp.put( signature.columns.get( counter ).columnName, localDateTime.toString() );
                    } else {
                        temp.put( signature.columns.get( counter ).columnName, o );
                    }
                    counter++;
                }
                resultData.add( temp );
            }

        } catch ( Exception e ) {

        }

        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put( "result", resultData );
        finalResult.put( "size", resultData.size() );
        return finalResult;
    }
}
