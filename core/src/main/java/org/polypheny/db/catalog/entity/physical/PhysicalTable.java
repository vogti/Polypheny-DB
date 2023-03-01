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

package org.polypheny.db.catalog.entity.physical;

import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.polypheny.db.algebra.type.AlgDataType;
import org.polypheny.db.algebra.type.AlgDataTypeFactory;
import org.polypheny.db.algebra.type.AlgDataTypeImpl;
import org.polypheny.db.algebra.type.AlgDataTypeSystem;
import org.polypheny.db.algebra.type.AlgProtoDataType;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.allocation.AllocationTable;
import org.polypheny.db.catalog.entity.logical.LogicalColumn;
import org.polypheny.db.catalog.entity.logical.LogicalTable;
import org.polypheny.db.catalog.logistic.EntityType;
import org.polypheny.db.catalog.logistic.NamespaceType;
import org.polypheny.db.type.PolyTypeFactoryImpl;

@EqualsAndHashCode(callSuper = true)
@Value
@NonFinal
public class PhysicalTable extends PhysicalEntity<LogicalTable> {

    public ImmutableList<CatalogColumnPlacement> placements;
    public ImmutableList<Long> columnIds;
    public ImmutableList<String> columnNames;
    public String namespaceName;

    public AllocationTable allocation;


    public PhysicalTable( AllocationTable allocation, long id, String name, String namespaceName, EntityType type, NamespaceType namespaceType, List<CatalogColumnPlacement> placements, List<String> columnNames ) {
        super( allocation.logical, id, name, namespaceName, type, namespaceType, allocation.adapterId );
        this.allocation = allocation;
        this.namespaceName = namespaceName;
        this.placements = ImmutableList.copyOf( placements );
        this.columnIds = ImmutableList.copyOf( placements.stream().map( p -> p.columnId ).collect( Collectors.toList() ) );
        this.columnNames = ImmutableList.copyOf( columnNames );
    }


    public PhysicalTable( AllocationTable table, String name, String namespaceName, List<String> columnNames ) {
        this( table, table.id, name, namespaceName, table.entityType, table.namespaceType, table.placements, columnNames );
    }


    @Override
    public AlgDataType getRowType() {
        return buildProto().apply( AlgDataTypeFactory.DEFAULT );
    }


    public AlgProtoDataType buildProto() {
        final AlgDataTypeFactory typeFactory = new PolyTypeFactoryImpl( AlgDataTypeSystem.DEFAULT );
        final AlgDataTypeFactory.Builder fieldInfo = typeFactory.builder();

        for ( CatalogColumnPlacement placement : placements ) {
            LogicalColumn logicalColumn = Catalog.getInstance().getSnapshot( 0 ).getLogicalColumn( placement.columnId );
            AlgDataType sqlType = logicalColumn.getAlgDataType( typeFactory );
            fieldInfo.add( logicalColumn.name, placement.physicalColumnName, sqlType ).nullable( logicalColumn.nullable );
        }

        return AlgDataTypeImpl.proto( fieldInfo.build() );
    }


    @Override
    public Serializable[] getParameterArray() {
        return new Serializable[0];
    }


    @Override
    public Expression asExpression() {
        return Expressions.call( Catalog.CATALOG_EXPRESSION, "getPhysicalTable", Expressions.constant( id ) );
    }

}
