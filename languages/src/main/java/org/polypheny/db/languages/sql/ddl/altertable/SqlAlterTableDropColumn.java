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

package org.polypheny.db.languages.sql.ddl.altertable;


import static org.polypheny.db.util.Static.RESOURCE;

import java.util.List;
import java.util.Objects;
import org.polypheny.db.catalog.Catalog.TableType;
import org.polypheny.db.catalog.entity.CatalogTable;
import org.polypheny.db.core.CoreUtil;
import org.polypheny.db.core.ParserPos;
import org.polypheny.db.core.QueryParameters;
import org.polypheny.db.ddl.DdlManager;
import org.polypheny.db.ddl.exception.ColumnNotExistsException;
import org.polypheny.db.jdbc.Context;
import org.polypheny.db.languages.sql.SqlIdentifier;
import org.polypheny.db.languages.sql.SqlWriter;
import org.polypheny.db.languages.sql.ddl.SqlAlterTable;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.util.ImmutableNullableList;


/**
 * Parse tree for {@code ALTER TABLE name DROP COLUMN name} statement.
 */
public class SqlAlterTableDropColumn extends SqlAlterTable {

    private final SqlIdentifier table;
    private final SqlIdentifier column;


    public SqlAlterTableDropColumn( ParserPos pos, SqlIdentifier table, SqlIdentifier column ) {
        super( pos );
        this.table = Objects.requireNonNull( table );
        this.column = Objects.requireNonNull( column );
    }


    @Override
    public List<Node> getOperandList() {
        return ImmutableNullableList.of( table, column );
    }


    @Override
    public void unparse( SqlWriter writer, int leftPrec, int rightPrec ) {
        writer.keyword( "ALTER" );
        writer.keyword( "TABLE" );
        table.unparse( writer, leftPrec, rightPrec );
        writer.keyword( "DROP" );
        writer.keyword( "COLUMN" );
        column.unparse( writer, leftPrec, rightPrec );
    }


    @Override
    public void execute( Context context, Statement statement, QueryParameters parameters ) {
        CatalogTable catalogTable = getCatalogTable( context, table );

        if ( catalogTable.tableType != TableType.TABLE ) {
            throw new RuntimeException( "Not possible to use ALTER TABLE because" + catalogTable.name + " is not a table." );
        }

        if ( column.names.size() != 1 ) {
            throw new RuntimeException( "No FQDN allowed here: " + column.toString() );
        }

        try {
            DdlManager.getInstance().dropColumn( catalogTable, column.getSimple(), statement );
        } catch ( ColumnNotExistsException e ) {
            throw CoreUtil.newContextException( column.getPos(), RESOURCE.columnNotFoundInTable( e.columnName, e.tableName ) );
        }
    }

}

