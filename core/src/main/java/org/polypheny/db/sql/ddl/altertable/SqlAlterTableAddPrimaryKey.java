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

package org.polypheny.db.sql.ddl.altertable;


import static org.polypheny.db.util.Static.RESOURCE;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.polypheny.db.adapter.AdapterManager;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.Catalog.PlacementType;
import org.polypheny.db.catalog.Catalog.TableType;
import org.polypheny.db.catalog.entity.CatalogColumn;
import org.polypheny.db.catalog.entity.CatalogColumnPlacement;
import org.polypheny.db.catalog.entity.CatalogPrimaryKey;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.catalog.exceptions.GenericCatalogException;
import org.polypheny.db.catalog.exceptions.UnknownColumnException;
import org.polypheny.db.jdbc.Context;
import org.polypheny.db.sql.SqlIdentifier;
import org.polypheny.db.sql.SqlNode;
import org.polypheny.db.sql.SqlNodeList;
import org.polypheny.db.sql.SqlUtil;
import org.polypheny.db.sql.SqlWriter;
import org.polypheny.db.sql.ddl.SqlAlterTable;
import org.polypheny.db.sql.parser.SqlParserPos;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.util.ImmutableNullableList;


/**
 * Parse tree for {@code ALTER TABLE name ADD PRIMARY KEY} statement.
 */
public class SqlAlterTableAddPrimaryKey extends SqlAlterTable {

    private final SqlIdentifier table;
    private final SqlNodeList columnList;


    public SqlAlterTableAddPrimaryKey( SqlParserPos pos, SqlIdentifier table, SqlNodeList columnList ) {
        super( pos );
        this.table = Objects.requireNonNull( table );
        this.columnList = Objects.requireNonNull( columnList );
    }


    @Override
    public List<SqlNode> getOperandList() {
        return ImmutableNullableList.of( table, columnList );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        writer.keyword( "ALTER" );
        writer.keyword( "TABLE" );
        table.unparse( writer, leftPrec, rightPrec );
        writer.keyword( "ADD" );
        writer.keyword( "PRIMARY" );
        writer.keyword( "KEY" );
        columnList.unparse( writer, leftPrec, rightPrec );
    }


    @Override
    public void execute( Context context, Statement statement ) {
        CatalogTable catalogTable = getCatalogTable( context, table );

        // Make sure that this is a table of type TABLE (and not SOURCE)
        if ( catalogTable.tableType != TableType.TABLE ) {
            throw SqlUtil.newContextException( table.getParserPosition(), RESOURCE.ddlOnSourceTable() );
        }

        try {
            CatalogPrimaryKey oldPk = Catalog.getInstance().getPrimaryKey( catalogTable.primaryKey );

            List<Long> columnIds = new LinkedList<>();
            for ( SqlNode node : columnList.getList() ) {
                String columnName = node.toString();
                CatalogColumn catalogColumn = Catalog.getInstance().getColumn( catalogTable.id, columnName );
                columnIds.add( catalogColumn.id );
            }
            Catalog.getInstance().addPrimaryKey( catalogTable.id, columnIds );

            // Add new column placements
            long pkColumnId = oldPk.columnIds.get( 0 ); // It is sufficient to check for one because all get replicated on all stores
            List<CatalogColumnPlacement> oldPkPlacements = Catalog.getInstance().getColumnPlacements( pkColumnId );
            for ( CatalogColumnPlacement ccp : oldPkPlacements ) {
                for ( long columnId : columnIds ) {
                    if ( !Catalog.getInstance().checkIfExistsColumnPlacement( ccp.adapterId, columnId ) ) {
                        Catalog.getInstance().addColumnPlacement(
                                ccp.adapterId,
                                columnId,
                                PlacementType.AUTOMATIC,
                                null, // Will be set later
                                null, // Will be set later
                                null ); // Will be set later
                        AdapterManager.getInstance().getStore( ccp.adapterId ).addColumn(
                                context,
                                Catalog.getInstance().getTable( ccp.tableId ),
                                Catalog.getInstance().getColumn( columnId ) );
                    }
                }
            }
        } catch ( GenericCatalogException | UnknownColumnException e ) {
            throw new RuntimeException( e );
        }
    }

}

