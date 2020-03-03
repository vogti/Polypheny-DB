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

package org.polypheny.db.adapter.cassandra;


import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.UnknownColumnException;
import org.polypheny.db.catalog.exceptions.UnknownTableException;


public class CassandraPhysicalNameProvider {

    private final static Pattern idRevPattern = Pattern.compile( "^(col|tab|sch)([0-9]+)(?>r([0-9]+))?$" );

    private final Catalog catalog;
    private final int storeId;

    private final String DEFAULT_SCHEMA = "public";

    public CassandraPhysicalNameProvider( Catalog catalog, int storeId ) {
        this.catalog = catalog;
        this.storeId = storeId;
    }


    public String generatePhysicalColumnName( long columnId ) {
        return "col" + columnId;
    }


    public String generatePhysicalTableName( long tableId ) {
        return "tab" + tableId;
    }


    public String generatePhysicalSchemaName( int schemaId ) {
        // TODO JS: implement cassandra schemas
        return "cassandra";
//        return "sch" + schemaId;
    }


    public String getPhysicalSchemaName( int schemaId ) {
        return generatePhysicalSchemaName( schemaId );
    }


    public String getPhysicalTableName( long tableId ) {
        return generatePhysicalTableName( tableId );
    }


    public String getPhysicalColumnName( long columnId ) throws RuntimeException {
        // TODO JS: This really should be a direct call to the catalog!
        List<CatalogColumnPlacement> placements;
        try {
            placements = catalog.getColumnPlacementsOnStore( this.storeId );
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
//            e.printStackTrace();
        }

        for ( CatalogColumnPlacement placement: placements ) {
            if ( placement.columnId == columnId ) {
                return placement.physicalColumnName;
            }
        }

        throw new RuntimeException("Column placement not found for store " + this.storeId + " and column " + columnId );
    }


    public String getLogicalColumnName( long columnId ) {
        CatalogColumn catalogColumn;
        try {
            catalogColumn = catalog.getColumn( columnId );
        } catch ( GenericCatalogException | UnknownColumnException e ) {
//            e.printStackTrace();
            throw new RuntimeException( e );
        }

        return catalogColumn.name;
    }


    private long tableId( String schemaName, String tableName ) {
        CatalogTable catalogTable;
        try {
            catalogTable = catalog.getTable( "APP", schemaName, tableName );
        } catch ( GenericCatalogException | UnknownTableException e ) {
            throw new RuntimeException( e );
        }
        return catalogTable.id;
    }


    private long columnId( String logicalSchemaName, String logicalTableName, String logicalColumnName ) {
        CatalogColumn catalogColumn;
        try {
            catalogColumn = catalog.getColumn( "APP", logicalSchemaName, logicalTableName, logicalColumnName );
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
        return catalogColumn.id;
    }


    private long columnId( long tableId, String logicalColumnName ) {
        CatalogColumn catalogColumn;
        try {
            catalogColumn = catalog.getColumn( tableId, logicalColumnName );
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
        return catalogColumn.id;
    }


    public String getPhysicalColumnName( long tableId, String logicalColumnName ) {
        long catalogColumnId = columnId( tableId, logicalColumnName );
        try {
            return this.catalog.getColumnPlacement( this.storeId, catalogColumnId ).physicalColumnName;
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
        }
    }


    public String getPhysicalColumnName( String tableName, String logicalColumnName ) {
        long tableId = tableId( this.DEFAULT_SCHEMA, tableName );
        long catalogColumnId = columnId( tableId, logicalColumnName );
        try {
            return this.catalog.getColumnPlacement( this.storeId, catalogColumnId ).physicalColumnName;
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
        }
    }


    public void updatePhysicalColumnName( long columnId, String updatedName ) {
        CatalogColumnPlacement placement;
        try {
            placement = this.catalog.getColumnPlacement( this.storeId, columnId );
            this.catalog.updateColumnPlacementPhysicalNames( this.storeId, columnId, placement.physicalTableName, placement.physicalTableName, updatedName );
        } catch ( GenericCatalogException e ) {
            throw new RuntimeException( e );
        }
    }


    public String getPhysicalTableName( String schemaName, String tableName ) {
        return "tab" + tableId( schemaName, tableName );
    }


    public String getPhysicalTableName( String tableName ) {
        return getPhysicalTableName( DEFAULT_SCHEMA, tableName );
    }


    public String getPhysicalTableName( List<String> qualifiedName ) {
        String schemaName;
        String tableName;
        if ( qualifiedName.size() == 1 ) {
            schemaName = DEFAULT_SCHEMA;
            tableName = qualifiedName.get( 0 );
        } else if ( qualifiedName.size() == 2 ) {
            schemaName = qualifiedName.get( 0 );
            tableName = qualifiedName.get( 1 );
        } else {
            throw new RuntimeException( "Unknown format for qualified name! Size: " + qualifiedName.size() );
        }

        return getPhysicalTableName( schemaName, tableName );
    }

    public static String incrementNameRevision( String name ) {
        Matcher m = idRevPattern.matcher( name );
        Long id;
        Long rev;
        String type;
        if ( m.find() ) {
            type = m.group( 1 );
            id = Long.valueOf( m.group( 2 ) );
            if ( m.group( 3 ) == null ) {
                rev = 0L;
            } else {
                rev = Long.valueOf( m.group( 3 ) );
            }
        } else {
            throw new IllegalArgumentException( "Not a physical name!" );
        }

        rev += 1L;

        return type + id + "r" + rev;
    }
}
