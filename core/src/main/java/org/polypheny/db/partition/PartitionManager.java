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

package org.polypheny.db.partition;

import java.util.List;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.partition.PartitionFunctionInfo;
import org.polypheny.db.type.PolyType;

public interface PartitionManager {

    /**
     * Returns the Index of the partition where to place the object
     */
    long getTargetPartitionGroupId( CatalogTable catalogTable, String columnValue );

    boolean validatePartitionGroupDistribution( CatalogTable table );

    boolean probePartitionGroupDistributionChange( CatalogTable catalogTable, int storeId, long columnId );

    List<CatalogColumnPlacement> getRelevantPlacements( CatalogTable catalogTable, List<Long> partitionGroupIds );

    boolean validatePartitionGroupSetup( List<List<String>> partitionGroupQualifiers, long numPartitionGroups, List<String> partitionGroupNames, CatalogColumn partitionColumn );

    int getNumberOfPartitionsPerGroup( int numberOfPartitions);

    boolean requiresUnboundPartitionGroup();

    boolean supportsColumnOfType( PolyType type );

    /**
     * Returns an instance of PartitionFunctionInfo specifying the available parameters of the partition function.
     */
    PartitionFunctionInfo getPartitionFunctionInfo();

}
