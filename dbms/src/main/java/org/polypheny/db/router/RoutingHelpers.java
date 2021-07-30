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

package org.polypheny.db.router;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.CatalogPartitionPlacement;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.plan.RelOptCluster;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.core.JoinRelType;
import org.polypheny.db.rel.logical.LogicalValues;
import org.polypheny.db.rex.RexNode;
import org.polypheny.db.schema.PolySchemaBuilder;
import org.polypheny.db.sql.fun.SqlStdOperatorTable;
import org.polypheny.db.tools.RelBuilder;
import org.polypheny.db.tools.RoutedRelBuilder;
import org.polypheny.db.transaction.Statement;

@Slf4j
public class RoutingHelpers {

    final static Catalog catalog = Catalog.getInstance();
    protected static final Cache<Integer, RelNode> joinedTableScanCache = CacheBuilder.newBuilder()
            .maximumSize( RuntimeConfig.JOINED_TABLE_SCAN_CACHE_SIZE.getInteger() )
            .build();

    protected static RelNode recursiveCopy( RelNode node ) {
        List<RelNode> inputs = new LinkedList<>();
        if ( node.getInputs() != null && node.getInputs().size() > 0 ) {
            for ( RelNode input : node.getInputs() ) {
                inputs.add( recursiveCopy( input ) );
            }
        }
        return node.copy( node.getTraitSet(), inputs );
    }

    protected static RoutedRelBuilder handleTableScan(
            RoutedRelBuilder builder,
            long tableId,
            String storeUniqueName,
            String logicalSchemaName,
            String logicalTableName,
            String physicalSchemaName,
            String physicalTableName,
            long partitionId ) {
        //if ( selectedAdapter != null ) {
        //    selectedAdapter.put( tableId, new SelectedAdapterInfo( storeUniqueName, physicalSchemaName, physicalTableName ) );
        //}
        return builder.scan( ImmutableList.of(
                PolySchemaBuilder.buildAdapterSchemaName( storeUniqueName, logicalSchemaName, physicalSchemaName, partitionId ),
                logicalTableName ) );
    }



    public static RoutedRelBuilder handleValues( LogicalValues node, RoutedRelBuilder builder ) {
        return builder.values( node.tuples, node.getRowType() );
    }


    protected static List<RoutedRelBuilder> handleValues( LogicalValues node, List<RoutedRelBuilder> builders ) {
        //if ( cancelQuery ) {
        //    return Collections.emptyList();
        //}
        return builders.stream().map( builder -> builder.values( node.tuples, node.getRowType() ) ).collect( Collectors.toList() );
    }


    public static  RelBuilder handleGeneric( RelNode node, RoutedRelBuilder builder ) {
        val result = handleGeneric( node, Lists.newArrayList( builder ) );
        if ( result.size() > 1 ) {
            log.error( "Single handle generic with multiple results " );
        }
        return result.get( 0 );
    }


    protected static List<RoutedRelBuilder> handleGeneric( RelNode node, List<RoutedRelBuilder> builders ) {
        /*if ( cancelQuery ) {
            return Collections.emptyList();
        }*/
        log.info( "start handle generic" );
        if ( node.getInputs().size() == 1 ) {
            log.info( "node input size = 1" );

            builders.forEach(
                    builder -> builder.replaceTop( node.copy( node.getTraitSet(), ImmutableList.of( builder.peek( 0 ) ) ) )
            );
        } else if ( node.getInputs().size() == 2 ) { // Joins, SetOperations
            log.info( "node input size = 2" );
            builders.forEach(
                    builder -> builder.replaceTop( node.copy( node.getTraitSet(), ImmutableList.of( builder.peek( 1 ), builder.peek( 0 ) ) ) )
            );
        } else {
            throw new RuntimeException( "Unexpected number of input elements: " + node.getInputs().size() );
        }
        return builders;
    }


    public static RelNode buildJoinedTableScan( Statement statement, RelOptCluster cluster, Map<Long, List<CatalogColumnPlacement>> placements ){
        RoutedRelBuilder builder = RoutedRelBuilder.create( statement, cluster );

        if ( RuntimeConfig.JOINED_TABLE_SCAN_CACHE.getBoolean() ) {
            RelNode cachedNode = joinedTableScanCache.getIfPresent( placements.hashCode() );
            if ( cachedNode != null ) {
                return cachedNode;
            }
        }

        for ( Entry partitionToPlacement : placements.entrySet() ) {

            Long partitionId = (long) partitionToPlacement.getKey();
            List<CatalogColumnPlacement> currentPlacements = (List<CatalogColumnPlacement>) partitionToPlacement.getValue();
            // Sort by adapter
            Map<Integer, List<CatalogColumnPlacement>> placementsByAdapter = new HashMap<>();
            for ( CatalogColumnPlacement placement : currentPlacements ) {
                if ( !placementsByAdapter.containsKey( placement.adapterId ) ) {
                    placementsByAdapter.put( placement.adapterId, new LinkedList<>() );
                }
                placementsByAdapter.get( placement.adapterId ).add( placement );
            }

            if ( placementsByAdapter.size() == 1 ) {

                List<CatalogColumnPlacement> ccps = placementsByAdapter.values().iterator().next();
                CatalogColumnPlacement ccp = ccps.get( 0 );
                CatalogPartitionPlacement cpp = catalog.getPartitionPlacement( ccp.adapterId, partitionId );

                builder = handleTableScan(
                        builder,
                        ccp.tableId,
                        ccp.adapterUniqueName,
                        ccp.getLogicalSchemaName(),
                        ccp.getLogicalTableName(),
                        ccp.physicalSchemaName,
                        cpp.physicalTableName,
                        cpp.partitionId );
                // final project
                ArrayList<RexNode> rexNodes = new ArrayList<>();
                List<CatalogColumnPlacement> placementList = currentPlacements.stream()
                        .sorted( Comparator.comparingInt( p -> Catalog.getInstance().getColumn( p.columnId ).position ) )
                        .collect( Collectors.toList() );
                for ( CatalogColumnPlacement catalogColumnPlacement : placementList ) {
                    rexNodes.add( builder.field( catalogColumnPlacement.getLogicalColumnName() ) );
                }
                builder.project( rexNodes );

            } else if ( placementsByAdapter.size() > 1 ) {
                // We need to join placements on different adapters

                // Get primary key
                long pkid = catalog.getTable( currentPlacements.get( 0 ).tableId ).primaryKey;
                List<Long> pkColumnIds = Catalog.getInstance().getPrimaryKey( pkid ).columnIds;
                List<CatalogColumn> pkColumns = new LinkedList<>();
                for ( long pkColumnId : pkColumnIds ) {
                    pkColumns.add( Catalog.getInstance().getColumn( pkColumnId ) );
                }

                // Add primary key
                for ( Entry<Integer, List<CatalogColumnPlacement>> entry : placementsByAdapter.entrySet() ) {
                    for ( CatalogColumn pkColumn : pkColumns ) {
                        CatalogColumnPlacement pkPlacement = Catalog.getInstance().getColumnPlacement( entry.getKey(), pkColumn.id );
                        if ( !entry.getValue().contains( pkPlacement ) ) {
                            entry.getValue().add( pkPlacement );
                        }
                    }
                }

                Deque<String> queue = new LinkedList<>();
                boolean first = true;
                for ( List<CatalogColumnPlacement> ccps : placementsByAdapter.values() ) {

                    CatalogColumnPlacement ccp = ccps.get( 0 );
                    CatalogPartitionPlacement cpp = catalog.getPartitionPlacement( ccp.adapterId, partitionId );

                    handleTableScan(
                            builder,
                            ccp.tableId,
                            ccp.adapterUniqueName,
                            ccp.getLogicalSchemaName(),
                            ccp.getLogicalTableName(),
                            ccp.physicalSchemaName,
                            cpp.physicalTableName,
                            cpp.partitionId );
                    if ( first ) {
                        first = false;
                    } else {
                        ArrayList<RexNode> rexNodes = new ArrayList<>();
                        for ( CatalogColumnPlacement p : ccps ) {
                            if ( pkColumnIds.contains( p.columnId ) ) {
                                String alias = ccps.get( 0 ).adapterUniqueName + "_" + p.getLogicalColumnName();
                                rexNodes.add( builder.alias( builder.field( p.getLogicalColumnName() ), alias ) );
                                queue.addFirst( alias );
                                queue.addFirst( p.getLogicalColumnName() );
                            } else {
                                rexNodes.add( builder.field( p.getLogicalColumnName() ) );
                            }
                        }
                        builder.project( rexNodes );
                        List<RexNode> joinConditions = new LinkedList<>();
                        for ( int i = 0; i < pkColumnIds.size(); i++ ) {
                            joinConditions.add( builder.call(
                                    SqlStdOperatorTable.EQUALS,
                                    builder.field( 2, ccp.getLogicalTableName(), queue.removeFirst() ),
                                    builder.field( 2, ccp.getLogicalTableName(), queue.removeFirst() ) ) );
                        }
                        builder.join( JoinRelType.INNER, joinConditions );

                    }
                }
                // final project
                ArrayList<RexNode> rexNodes = new ArrayList<>();
                List<CatalogColumnPlacement> placementList = currentPlacements.stream()
                        .sorted( Comparator.comparingInt( p -> Catalog.getInstance().getColumn( p.columnId ).position ) )
                        .collect( Collectors.toList() );
                for ( CatalogColumnPlacement ccp : placementList ) {
                    rexNodes.add( builder.field( ccp.getLogicalColumnName() ) );
                }
                builder.project( rexNodes );
            } else {
                throw new RuntimeException( "The table '" + currentPlacements.get( 0 ).getLogicalTableName() + "' seems to have no placement. This should not happen!" );
            }
        }

        builder.union( true, placements.size() );

        RelNode node = builder.build();
        if ( RuntimeConfig.JOINED_TABLE_SCAN_CACHE.getBoolean() ) {
            joinedTableScanCache.put( placements.hashCode(), node );
        }
        return node;
    }


}
