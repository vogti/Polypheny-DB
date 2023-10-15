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

package org.polypheny.db.catalog.catalogs;

import com.google.common.collect.ImmutableList;
import io.activej.serializer.BinarySerializer;
import io.activej.serializer.annotations.Deserialize;
import io.activej.serializer.annotations.Serialize;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.catalog.IdBuilder;
import org.polypheny.db.catalog.entity.allocation.AllocationColumn;
import org.polypheny.db.catalog.entity.allocation.AllocationEntity;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.entity.allocation.AllocationTableWrapper;
import org.polypheny.db.catalog.entity.logical.LogicalColumn;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.entity.physical.PhysicalColumn;
import org.polypheny.db.catalog.entity.physical.PhysicalEntity;
import org.polypheny.db.catalog.entity.physical.PhysicalTable;
import org.polypheny.db.type.PolySerializable;
import org.polypheny.db.util.Pair;

@EqualsAndHashCode(callSuper = true)
@Value
@Slf4j
@NonFinal
public class RelStoreCatalog extends StoreCatalog {

    @Getter
    public BinarySerializer<GraphStoreCatalog> serializer = PolySerializable.buildSerializer( GraphStoreCatalog.class );

    @Serialize
    public ConcurrentMap<Pair<Long, Long>, PhysicalColumn> columns; // allocId, columnId


    public RelStoreCatalog( long adapterId ) {
        this( adapterId, Map.of(), Map.of(), Map.of(), Map.of() );
    }


    public RelStoreCatalog(
            @Deserialize("adapterId") long adapterId,
            @Deserialize("physicals") Map<Long, PhysicalEntity> physicals,
            @Deserialize("allocations") Map<Long, AllocationEntity> allocations,
            @Deserialize("columns") Map<Pair<Long, Long>, PhysicalColumn> columns,
            @Deserialize("allocToPhysicals") Map<Long, Set<Long>> allocToPhysicals ) {
        super( adapterId, Map.of(), physicals, allocations, allocToPhysicals );
        this.columns = new ConcurrentHashMap<>( columns );
    }


    @Override
    public void renameLogicalColumn( long id, String newFieldName ) {
        List<PhysicalColumn> updates = new ArrayList<>();
        for ( PhysicalColumn field : columns.values() ) {
            if ( field.id == id ) {
                updates.add( field.toBuilder().logicalName( newFieldName ).build() );
            }
        }
        for ( PhysicalColumn u : updates ) {
            PhysicalTable table = fromAllocation( u.allocId );
            List<PhysicalColumn> newColumns = new ArrayList<>( table.columns );
            newColumns.remove( u );
            newColumns.add( u );
            physicals.put( table.id, table.toBuilder().columns( ImmutableList.copyOf( newColumns ) ).build() );
            columns.put( Pair.of( u.allocId, u.id ), u );
        }
    }


    public void addColumn( PhysicalColumn column ) {
        columns.put( Pair.of( column.allocId, column.id ), column );
    }


    public PhysicalTable getTable( long id ) {
        return getPhysical( id ).unwrap( PhysicalTable.class );
    }


    public PhysicalColumn getColumn( long id, long allocId ) {
        return columns.get( Pair.of( allocId, id ) );
    }


    public PhysicalTable createTable( String namespaceName, String tableName, Map<Long, String> columnNames, LogicalTable logical, Map<Long, LogicalColumn> lColumns, AllocationTableWrapper wrapper ) {
        AllocationTable allocation = wrapper.table;
        List<AllocationColumn> columns = wrapper.columns;
        List<PhysicalColumn> pColumns = columns.stream().map( c -> new PhysicalColumn( columnNames.get( c.columnId ), logical.id, allocation.id, allocation.adapterId, c.position, lColumns.get( c.columnId ) ) ).collect( Collectors.toList() );
        PhysicalTable table = new PhysicalTable( IdBuilder.getInstance().getNewPhysicalId(), allocation.id, allocation.logicalId, tableName, pColumns, logical.namespaceId, namespaceName, allocation.adapterId );
        pColumns.forEach( this::addColumn );
        addPhysical( allocation, table );
        return table;
    }


    public PhysicalColumn addColumn( String name, long allocId, int position, LogicalColumn lColumn ) {
        PhysicalColumn column = new PhysicalColumn( name, lColumn.tableId, allocId, adapterId, position, lColumn );
        PhysicalTable table = fromAllocation( allocId );
        List<PhysicalColumn> columns = new ArrayList<>( table.columns );
        columns.add( position - 1, column );
        addColumn( column );
        addPhysical( getAlloc( table.allocationId ), table.toBuilder().columns( ImmutableList.copyOf( columns ) ).build() );
        return column;
    }


    public PhysicalColumn updateColumnType( long allocId, LogicalColumn newCol ) {
        PhysicalColumn old = getColumn( newCol.id, allocId );
        PhysicalColumn column = new PhysicalColumn( old.name, newCol.tableId, allocId, old.adapterId, old.position, newCol );
        PhysicalTable table = fromAllocation( allocId );
        List<PhysicalColumn> pColumn = new ArrayList<>( table.columns );
        pColumn.remove( old );
        pColumn.add( column );
        addPhysical( getAlloc( table.allocationId ), table.toBuilder().columns( ImmutableList.copyOf( pColumn ) ).build() );

        return column;
    }


    public PhysicalTable fromAllocation( long id ) {
        return getPhysicalsFromAllocs( id ).get( 0 ).unwrap( PhysicalTable.class );
    }


    public void dropColumn( long allocId, long columnId ) {
        PhysicalColumn column = columns.get( Pair.of( allocId, columnId ) );
        PhysicalTable table = fromAllocation( allocId );
        List<PhysicalColumn> pColumns = new ArrayList<>( table.columns );
        pColumns.remove( column );
        addPhysical( getAlloc( allocId ), table.toBuilder().columns( ImmutableList.copyOf( pColumns ) ).build() );
        columns.remove( Pair.of( allocId, columnId ) );
    }


    public List<PhysicalColumn> getColumns( long allocId ) {
        return columns.values().stream().filter( c -> c.allocId == allocId ).collect( Collectors.toList() );
    }


    @Override
    public PolySerializable copy() {
        return PolySerializable.deserialize( serialize(), RelStoreCatalog.class );
    }

}