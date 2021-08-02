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

package org.polypheny.db.processing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.val;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.prepare.RelOptTableImpl;
import org.polypheny.db.rel.RelNode;
import org.polypheny.db.rel.RelShuttleImpl;
import org.polypheny.db.rel.core.TableScan;
import org.polypheny.db.rel.logical.LogicalAggregate;
import org.polypheny.db.rel.logical.LogicalCorrelate;
import org.polypheny.db.rel.logical.LogicalExchange;
import org.polypheny.db.rel.logical.LogicalFilter;
import org.polypheny.db.rel.logical.LogicalIntersect;
import org.polypheny.db.rel.logical.LogicalJoin;
import org.polypheny.db.rel.logical.LogicalMatch;
import org.polypheny.db.rel.logical.LogicalMinus;
import org.polypheny.db.rel.logical.LogicalProject;
import org.polypheny.db.rel.logical.LogicalSort;
import org.polypheny.db.rel.logical.LogicalTableScan;
import org.polypheny.db.rel.logical.LogicalUnion;
import org.polypheny.db.schema.LogicalTable;
import org.polypheny.db.transaction.Statement;

/**
 * Unified routing rel shuttle class to extract used columns from RelNode.
 */
public class LogicalRelQueryAnalyzeShuttle extends RelShuttleImpl {

    protected final LogicalRelQueryAnalyzeRexShuttle rexShuttle;
    @Getter
    protected final Map<Integer, List<String>> filterMap = new HashMap<>(); // logical filterId -> List partitionsValue
    private final Statement statement;

    @Getter
    protected final HashSet<String> hashBasis = new HashSet<>();

    @Getter
    protected final LinkedHashMap<Long, String> availableColumns = new LinkedHashMap<>(); // column id -> schemaName.tableName.ColumnName
    protected final HashMap<Long, Long> availableColumnsWithTable = new HashMap<>(); // columnId -> tableId

    @Getter
    protected final List<String> tables = new ArrayList<>();

    public List<Long> getAllColumnsPerTable(Long tableId){
        val usedCols = this.availableColumns;
        return availableColumnsWithTable.entrySet().stream()
                .filter( x -> x.getValue().equals( tableId ) && usedCols.keySet().contains( x.getKey() ))
                .map( x -> x.getKey() )
                .collect( Collectors.toList() );
    }

    public List<Long> getUsedColumnsPerTable(Long tableId) {
        val usedCols = getUsedColumns();
        return availableColumnsWithTable.entrySet().stream()
                .filter( x -> x.getValue().equals( tableId ) && usedCols.keySet().contains( x.getKey() ))
                .map( x -> x.getKey() )
                .collect( Collectors.toList() );
    }

    public Map<Long, String> getUsedColumns() {
        val availableColumnNames = new ArrayList<String>(this.availableColumns.values());
        val availableColumnKeys = new ArrayList<Long>(this.availableColumns.keySet());

        if(this.rexShuttle.usedIds.isEmpty()){
            return this.availableColumns;
        }

        val usedColumns = this.rexShuttle.usedIds.stream()
                .collect( Collectors.toMap(
                        index -> availableColumnKeys.get( index ),
                        index -> availableColumnNames.get( index ) ) );
        return usedColumns;
    }

    public String getQueryName(){
        return this.hashBasis.toString();
    }

    public LogicalRelQueryAnalyzeShuttle( Statement s ) {
        this.statement = s;
        rexShuttle = new LogicalRelQueryAnalyzeRexShuttle( this.statement );
    }

    // region public overrides

    @Override
    public RelNode visit( LogicalAggregate aggregate ) {
        hashBasis.add( "LogicalAggregate#" + aggregate.getAggCallList() );
        return visitChild( aggregate, 0, aggregate.getInput() );
    }


    @Override
    public RelNode visit( LogicalMatch match ) {
        hashBasis.add( "LogicalMatch#" + match.getTable().getQualifiedName() );
        return visitChild( match, 0, match.getInput() );
    }


    @Override
    public RelNode visit( TableScan scan ) {
        hashBasis.add( "TableScan#" + scan.getTable().getQualifiedName() );
        // get available columns for every table scan
        this.getAvailableColumns(scan);

        return super.visit( scan );
    }

    @Override
    public RelNode visit( LogicalFilter filter ) {
        hashBasis.add( "LogicalFilter" );
        super.visit( filter );
        filter.accept( this.rexShuttle );

        getPartitioningInfo(filter);

        return filter;
    }


    @Override
    public RelNode visit( LogicalProject project ) {
        hashBasis.add( "LogicalProject#" + project.getProjects().size() );
        super.visit( project );
        project.accept( this.rexShuttle );
        return project;
    }

    @Override
    public RelNode visit( LogicalCorrelate correlate ) {
        hashBasis.add( "LogicalCorrelate" );
        return visitChildren( correlate );
    }

    @Override
    public RelNode visit( LogicalJoin join ) {
        if(join.getLeft() instanceof LogicalTableScan && join.getRight() instanceof  LogicalTableScan ){
            hashBasis.add( "LogicalJoin#" + join.getLeft().getTable().getQualifiedName() + "#" + join.getRight().getTable().getQualifiedName() );
        }

        super.visit( join );
        join.accept( this.rexShuttle );
        return join;
    }


    @Override
    public RelNode visit( LogicalUnion union ) {
        hashBasis.add( "LogicalUnion" );
        super.visit( union );
        union.accept( this.rexShuttle );
        return union;
    }


    @Override
    public RelNode visit( LogicalIntersect intersect ) {
        hashBasis.add( "LogicalIntersect" );
        super.visit( intersect );
        intersect.accept( this.rexShuttle );
        return intersect;
    }


    @Override
    public RelNode visit( LogicalMinus minus ) {
        hashBasis.add( "LogicalMinus" );
        return visitChildren( minus );
    }


    @Override
    public RelNode visit( LogicalSort sort ) {
        hashBasis.add( "LogicalSort" );
        return visitChildren( sort );
    }


    @Override
    public RelNode visit( LogicalExchange exchange ) {
        hashBasis.add( "LogicalExchange#" + exchange.distribution.getType().shortName );
        return visitChildren( exchange );
    }


    @Override
    public RelNode visit( RelNode other ) {
        hashBasis.add( "other#" + other.getClass().getSimpleName() );
        return visitChildren( other );
    }

    // endregion

    // region private helpers

    private void getAvailableColumns( TableScan scan ) {
        this.tables.addAll( scan.getTable().getQualifiedName() );
        val table = ((RelOptTableImpl) scan.getTable()).getTable();
        LogicalTable logicalTable = ( table instanceof LogicalTable ) ? (LogicalTable) table : null;
        if ( logicalTable != null ) {
            val ids = logicalTable.getColumnIds();
            val names = logicalTable.getLogicalColumnNames();
            val baseName = logicalTable.getLogicalSchemaName() + "." + logicalTable.getLogicalTableName() + ".";

            for ( int i = 0; i < ids.size(); i++ ) {
                this.availableColumns.putIfAbsent( ids.get( i ), baseName + names.get( i ) );
                this.availableColumnsWithTable.putIfAbsent( ids.get( i ), logicalTable.getTableId() );
            }
        }
    }


    private void getPartitioningInfo( LogicalFilter filter ) {
        //
        RelOptTableImpl table = (RelOptTableImpl) filter.getInput().getTable();
        if(table == null){
            return;
        }

        LogicalTable logicalTable = ((LogicalTable) table.getTable());
        if(logicalTable == null){
            return;
        }

        // if ( table.getTable() instanceof LogicalTable )
        CatalogTable catalogTable = Catalog.getInstance().getTable( logicalTable.getTableId() );

        // only if table is partitioned
        if ( catalogTable.isPartitioned ) {
            WhereClauseVisitor whereClauseVisitor = new WhereClauseVisitor( statement, catalogTable.columnIds.indexOf( catalogTable.partitionColumnId ) );
            filter.accept( whereClauseVisitor );

            if ( whereClauseVisitor.valueIdentified ) {
                if ( !whereClauseVisitor.getValues().isEmpty() ) {
                    filterMap.put( filter.getId(), whereClauseVisitor.getValues().stream()
                            .map( Object::toString )
                            .collect( Collectors.toList() ) );
                }
                //filterMap.remove( filter.getId() );
            }
        }
    }

    // endregion


}

