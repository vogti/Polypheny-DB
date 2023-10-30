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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.polypheny.db.catalog.entity.allocation.AllocationColumn;
import org.polypheny.db.catalog.entity.allocation.AllocationPartition;
import org.polypheny.db.catalog.entity.allocation.AllocationPartitionGroup;
import org.polypheny.db.catalog.entity.allocation.AllocationPlacement;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.logistic.DataPlacementRole;
import org.polypheny.db.catalog.logistic.PartitionType;
import org.polypheny.db.catalog.logistic.PlacementType;
import org.polypheny.db.partition.properties.PartitionProperty;
import org.polypheny.db.util.Pair;

public interface AllocationRelationalCatalog extends AllocationCatalog {


    /**
     * Adds a placement for a column.
     *
     * @param placementId
     * @param logicalTableId
     * @param columnId The id of the column to be placed
     * @param adapterId
     * @param placementType The type of placement
     * @param position
     * @return
     */
    AllocationColumn addColumn( long placementId, long logicalTableId, long columnId, long adapterId, PlacementType placementType, int position );

    /**
     * Deletes all dependent column placements
     *
     * @param placementId The id of the adapter
     * @param columnId The id of the column
     */
    void deleteColumn( long placementId, long columnId );


    /**
     * Update the type of a placement.
     *
     * @param placementId The id of the adapter
     * @param columnId The id of the column
     * @param placementType The new type of placement
     */
    void updateColumnPlacementType( long placementId, long columnId, PlacementType placementType );


    /**
     * Adds a partition to the catalog
     *
     * @param tableId The unique id of the table
     * @param namespaceId The unique id of the table
     * @param partitionType partition Type of the added partition
     * @return The id of the created partitionGroup
     */
    AllocationPartitionGroup addPartitionGroup( long tableId, String partitionGroupName, long namespaceId, PartitionType partitionType, long numberOfInternalPartitions, List<String> effectivePartitionGroupQualifier, boolean isUnbound );

    /**
     * Should only be called from mergePartitions(). Deletes a single partition and all references.
     *
     * @param groupId The partitionId to be deleted
     */
    void deletePartitionGroup( long groupId );


    /**
     * Adds a partition to the catalog
     *
     * @param tableId The unique id of the table
     * @param namespaceId The unique id of the table
     * @param name
     * @param placementType
     * @param role
     * @param partitionType
     * @return The id of the created partition
     */
    AllocationPartition addPartition( long tableId, long namespaceId, @Nullable String name, boolean isUnbound, PlacementType placementType, DataPlacementRole role, PartitionType partitionType );

    /**
     * Deletes a single partition and all references.
     *
     * @param partitionId The partitionId to be deleted
     */
    void deletePartition( long partitionId );


    void addPartitionProperty( long tableId, PartitionProperty partitionProperty );

    /**
     * Assign the partition to a new partitionGroup
     *
     * @param partitionId Partition to move
     * @param partitionGroupId New target group to move the partition to
     */
    void updatePartition( long partitionId, Long partitionGroupId );


    /**
     * Adds a placement for a partition.
     *
     * @param namespaceId
     * @param adapterId The adapter on which the table should be placed on
     * @param tableId The table for which a partition placement shall be created
     * @param placementType The type of placement
     * @return
     */
    AllocationPartition addPartition( long namespaceId, long adapterId, long tableId, PlacementType placementType, DataPlacementRole role );

    /**
     * Adds a new DataPlacement for a given table on a specific store
     *
     * @param adapterId adapter where placement should be located
     * @param placementId
     * @param logicalId table to retrieve the placement from
     * @return
     */
    AllocationTable addAllocation( long adapterId, long placementId, long partitionId, long logicalId );


    void deleteAllocation( long allocId );


    Map<Long, AllocationTable> getTables();

    Map<Pair<Long, Long>, AllocationColumn> getColumns();

    Map<Long, PartitionProperty> getProperties();


    ConcurrentHashMap<Long, AllocationPartitionGroup> getPartitionGroups();

    AllocationPlacement addPlacement( long logicalEntityId, long namespaceId, long adapterId );

    void deletePlacement( long id );

    void deleteProperty( long id );

}
