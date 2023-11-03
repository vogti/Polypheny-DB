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

package org.polypheny.db.catalog.entity.logical;


import io.activej.serializer.annotations.Deserialize;
import io.activej.serializer.annotations.Serialize;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogObject;


@Value
@EqualsAndHashCode(callSuper = true)
public class LogicalPrimaryKey extends LogicalKey {

    @Serialize
    public LogicalKey key;


    public LogicalPrimaryKey( @Deserialize("key") @NonNull final LogicalKey key ) {
        super(
                key.id,
                key.tableId,
                key.namespaceId,
                key.columnIds,
                EnforcementTime.ON_QUERY );

        this.key = key;
    }


    // Used for creating ResultSets
    public List<CatalogPrimaryKeyColumn> getCatalogPrimaryKeyColumns() {
        int i = 1;
        List<CatalogPrimaryKeyColumn> list = new LinkedList<>();
        for ( String columnName : getColumnNames() ) {
            list.add( new CatalogPrimaryKeyColumn( id, i++, columnName ) );
        }
        return list;
    }


    public Serializable[] getParameterArray( String columnName, int keySeq ) {
        return new Serializable[]{ Catalog.DATABASE_NAME, getSchemaName(), getTableName(), columnName, keySeq, null };
    }


    // Used for creating ResultSets
    @RequiredArgsConstructor
    public static class CatalogPrimaryKeyColumn implements CatalogObject {

        private static final long serialVersionUID = -2669773639977732201L;

        private final long pkId;

        private final int keySeq;
        private final String columnName;


        @Override
        public Serializable[] getParameterArray() {
            return Catalog.snapshot().rel().getPrimaryKey( pkId ).orElseThrow().getParameterArray( columnName, keySeq );
        }


        @RequiredArgsConstructor
        public static class PrimitiveCatalogPrimaryKeyColumn {

            public final String tableCat;
            public final String tableSchem;
            public final String tableName;
            public final String columnName;
            public final int keySeq;
            public final String pkName;

        }

    }

}
