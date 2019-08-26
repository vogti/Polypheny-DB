/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package ch.unibas.dmi.dbis.polyphenydb.webui;


import ch.unibas.dmi.dbis.polyphenydb.Transaction;
import ch.unibas.dmi.dbis.polyphenydb.TransactionException;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbPrepare.PolyphenyDbSignature;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.util.LimitIterator;
import ch.unibas.dmi.dbis.polyphenydb.webui.JdbcTransactionHandler.TransactionHandlerException;
import ch.unibas.dmi.dbis.polyphenydb.webui.models.DbColumn;
import ch.unibas.dmi.dbis.polyphenydb.webui.models.Debug;
import ch.unibas.dmi.dbis.polyphenydb.webui.models.Index;
import ch.unibas.dmi.dbis.polyphenydb.webui.models.Result;
import ch.unibas.dmi.dbis.polyphenydb.webui.models.SidebarElement;
import ch.unibas.dmi.dbis.polyphenydb.webui.models.requests.ColumnRequest;
import ch.unibas.dmi.dbis.polyphenydb.webui.models.requests.ConstraintRequest;
import ch.unibas.dmi.dbis.polyphenydb.webui.models.requests.SchemaTreeRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.apache.calcite.avatica.ColumnMetaData;
import org.apache.calcite.avatica.MetaImpl;
import spark.Request;
import spark.Response;


public class CrudPolypheny extends Crud {

    /**
     * Constructor
     *
     * @param driver driver name
     * @param jdbc jdbc url
     * @param host host name
     * @param port port
     * @param dbName database name
     * @param user user name
     * @param pass password
     */
    CrudPolypheny( final String driver, final String jdbc, final String host, final int port, final String dbName, final String user, final String pass ) {
        super( driver, jdbc, host, port, dbName, user, pass );
    }


    @Override
    ArrayList<SidebarElement> getSchemaTree( final Request req, final Response res ) {
        SchemaTreeRequest request = this.gson.fromJson( req.body(), SchemaTreeRequest.class );
        ArrayList<SidebarElement> result = new ArrayList<>();
        JdbcTransactionHandler handler = getHandler();

        if ( request.depth < 1 ) {
            LOGGER.error( "Trying to fetch a schemaTree with depth < 1" );
            return new ArrayList<>();
        }

        try ( ResultSet schemas = handler.getMetaData().getSchemas() ) {
            while ( schemas.next() ) {
                String schema = schemas.getString( 1 );
                //if( schema.equals( "pg_catalog" ) || schema.equals( "information_schema" )) continue;
                SidebarElement schemaTree = new SidebarElement( schema, schema, "", "cui-layers" );

                if ( request.depth > 1 ) {
                    ResultSet tablesRs = handler.getMetaData().getTables( this.dbName, schema, null, null );
                    ArrayList<SidebarElement> tables = new ArrayList<>();
                    ArrayList<SidebarElement> views = new ArrayList<>();
                    while ( tablesRs.next() ) {
                        String tableName = tablesRs.getString( 3 );
                        SidebarElement table = new SidebarElement( schema + "." + tableName, tableName, request.routerLinkRoot, "fa fa-table" );

                        if ( request.depth > 2 ) {
                            ResultSet columnsRs = handler.getMetaData().getColumns( this.dbName, schema, tableName, null );
                            while ( columnsRs.next() ) {
                                String columnName = columnsRs.getString( 4 );
                                table.addChild( new SidebarElement( schema + "." + tableName + "." + columnName, columnName, request.routerLinkRoot ).setCssClass( "sidebarColumn" ) );
                            }
                        }
                        if ( tablesRs.getString( 4 ).equals( "TABLE" ) ) {
                            tables.add( table );
                        } else if ( request.views && tablesRs.getString( 4 ).equals( "VIEW" ) ) {
                            views.add( table );
                        }
                    }
                    schemaTree.addChild( new SidebarElement( schema + ".tables", "tables", request.routerLinkRoot, "fa fa-table" ).addChildren( tables ).setRouterLink( "" ) );
                    if ( request.views ) {
                        schemaTree.addChild( new SidebarElement( schema + ".views", "views", request.routerLinkRoot, "icon-eye" ).addChildren( views ).setRouterLink( "" ) );
                    }
                    tablesRs.close();
                }
                result.add( schemaTree );
            }
        } catch ( SQLException e ) {
            LOGGER.error( e.getMessage() );
        }

        return result;
    }


    @Override
    protected String filterTable( final Map<String, String> filter ) {
        StringJoiner joiner = new StringJoiner( " AND ", " WHERE ", "" );
        int counter = 0;
        for ( Map.Entry<String, String> entry : filter.entrySet() ) {
            if ( !entry.getValue().equals( "" ) ) {
                joiner.add( "CAST (" + entry.getKey() + " AS VARCHAR) LIKE '" + entry.getValue() + "%'" );
                counter++;
            }
        }
        String out = "";
        if ( counter > 0 ) {
            out = joiner.toString();
        }
        return out;
    }


    @Override
    Result updateColumn( final Request req, final Response res ) {
        ColumnRequest request = this.gson.fromJson( req.body(), ColumnRequest.class );
        DbColumn oldColumn = request.oldColumn;
        DbColumn newColumn = request.newColumn;
        Result result;
        ArrayList<String> queries = new ArrayList<>();
        StringBuilder sBuilder = new StringBuilder();
        JdbcTransactionHandler handler = getHandler();

        // rename column if needed
        if ( !oldColumn.name.equals( newColumn.name ) ) {
            String query = String.format( "ALTER TABLE %s RENAME COLUMN %s TO %s", request.tableId, oldColumn.name, newColumn.name );
            queries.add( query );
        }

        // change type + length
        // TODO: cast if needed
        if ( !oldColumn.dataType.equals( newColumn.dataType ) || !Objects.equals( oldColumn.maxLength, newColumn.maxLength ) ) {
            if ( newColumn.maxLength != null ) {
                String query = String.format( "ALTER TABLE %s MODIFY COLUMN %s SET TYPE %s(%s)", request.tableId, newColumn.name, newColumn.dataType, newColumn.maxLength );
                queries.add( query );
            } else {
                // TODO: drop maxlength if requested
                String query = String.format( "ALTER TABLE %s MODIFY COLUMN %s SET TYPE %s", request.tableId, newColumn.name, newColumn.dataType );
                queries.add( query );
            }
        }

        // set/drop nullable
        if ( oldColumn.nullable != newColumn.nullable ) {
            String nullable = "SET";
            if ( newColumn.nullable ) {
                nullable = "DROP";
            }
            String query = "ALTER TABLE " + request.tableId + " MODIFY COLUMN " + newColumn.name + " " + nullable + " NOT NULL";
            queries.add( query );
        }

        // change default value
        if ( oldColumn.defaultValue == null || newColumn.defaultValue == null || !oldColumn.defaultValue.equals( newColumn.defaultValue ) ) {
            String query;
            if ( newColumn.defaultValue == null ) {
                query = String.format( "ALTER TABLE %s MODIFY COLUMN %s DROP DEFAULT", request.tableId, newColumn.name );
            } else {
                query = String.format( "ALTER TABLE %s MODIFY COLUMN %s SET DEFAULT ", request.tableId, newColumn.name );
                switch ( newColumn.dataType ) {
                    case "BIGINT":
                    case "INTEGER":
                    case "DECIMAL":
                    case "DOUBLE":
                    case "FLOAT":
                    case "SMALLINT":
                    case "TINYINT":
                        int a = Integer.parseInt( request.newColumn.defaultValue );
                        query = query + a;
                        break;
                    case "VARCHAR":
                        query = query + String.format( "'%s'", request.newColumn.defaultValue );
                        break;
                    default:
                        //varchar, timestamptz, bool
                        query = query + request.newColumn.defaultValue;
                }
            }
            queries.add( query );
        }

        result = new Result( new Debug().setAffectedRows( 1 ).setGeneratedQuery( queries.toString() ) );
        try {
            for ( String query : queries ) {
                handler.executeUpdate( query );
                sBuilder.append( query );
            }
            handler.commit();
        } catch ( SQLException | TransactionHandlerException e ) {
            result = new Result( e.toString() ).setInfo( new Debug().setAffectedRows( 0 ).setGeneratedQuery( sBuilder.toString() ) );
            try {
                handler.rollback();
            } catch ( TransactionHandlerException e2 ) {
                result = new Result( e2.toString() ).setInfo( new Debug().setAffectedRows( 0 ).setGeneratedQuery( sBuilder.toString() ) );
            }
        }

        return result;
    }


    /**
     * Add a column to an existing table
     */
    @Override
    Result addColumn( final Request req, final Response res ) {
        ColumnRequest request = this.gson.fromJson( req.body(), ColumnRequest.class );
        JdbcTransactionHandler handler = getHandler();
        String query = String.format( "ALTER TABLE %s ADD COLUMN %s %s", request.tableId, request.newColumn.name, request.newColumn.dataType );
        if ( request.newColumn.maxLength != null ) {
            query = query + String.format( "(%d)", request.newColumn.maxLength );
        }
        if ( !request.newColumn.nullable ) {
            query = query + " NOT NULL";
        }
        if ( request.newColumn.defaultValue != null ) {
            switch ( request.newColumn.dataType ) {
                case "BIGINT":
                case "INTEGER":
                case "DECIMAL":
                case "DOUBLE":
                case "FLOAT":
                case "SMALLINT":
                case "TINYINT":
                    int a = Integer.parseInt( request.newColumn.defaultValue );
                    query = query + " DEFAULT " + a;
                    break;
                case "VARCHAR":
                    query = query + String.format( " DEFAULT '%s'", request.newColumn.defaultValue );
                    break;
                default:
                    // varchar, timestamptz, bool
                    query = query + " DEFAULT " + request.newColumn.defaultValue;
            }
        }
        Result result;
        try {
            int affectedRows = handler.executeUpdate( query );
            handler.commit();
            result = new Result( new Debug().setAffectedRows( affectedRows ).setGeneratedQuery( query ) );
        } catch ( SQLException | TransactionHandlerException e ) {
            result = new Result( e.getMessage() );
        }
        return result;
    }


    @Override
    Result dropConstraint( final Request req, final Response res ) {
        ConstraintRequest request = this.gson.fromJson( req.body(), ConstraintRequest.class );
        JdbcTransactionHandler handler = getHandler();
        String query;
        if ( request.constraint.type.equals( "PRIMARY KEY" ) ) {
            query = String.format( "ALTER TABLE %s DROP PRIMARY KEY", request.table );
        } else {
            query = String.format( "ALTER TABLE %s DROP CONSTRAINT %s;", request.table, request.constraint.name );
        }
        Result result;
        try {
            int rows = handler.executeUpdate( query );
            handler.commit();
            result = new Result( new Debug().setAffectedRows( rows ) );
        } catch ( SQLException | TransactionHandlerException e ) {
            result = new Result( e.getMessage() );
        }
        return result;
    }


    /**
     * Drop an index of a table
     */
    @Override
    Result dropIndex( final Request req, final Response res ) {
        Index index = gson.fromJson( req.body(), Index.class );
        JdbcTransactionHandler handler = getHandler();
        String query = String.format( "ALTER TABLE %s DROP INDEX %s", index.getTable(), index.getName() );
        Result result;
        try {
            int a = handler.executeUpdate( query );
            handler.commit();
            result = new Result( new Debug().setGeneratedQuery( query ).setAffectedRows( a ) );
        } catch ( SQLException | TransactionHandlerException e ) {
            result = new Result( e.getMessage() );
        }
        return result;
    }


    /**
     * Create an index for a table
     */
    @Override
    Result createIndex( final Request req, final Response res ) {
        Index index = this.gson.fromJson( req.body(), Index.class );
        JdbcTransactionHandler handler = getHandler();
        Result result;
        StringJoiner colJoiner = new StringJoiner( ",", "(", ")" );
        for ( String col : index.getColumns() ) {
            colJoiner.add( col );
        }
        String query = String.format( "ALTER TABLE %s ADD INDEX %s ON %s USING %s", index.getTable(), index.getName(), colJoiner.toString(), index.getMethod() );
        try {
            int a = handler.executeUpdate( query );
            handler.commit();
            result = new Result( new Debug().setAffectedRows( a ) );
        } catch ( SQLException | TransactionHandlerException e ) {
            result = new Result( e.getMessage() );
        }
        return result;
    }


    /**
     * Execute a logical plan coming from the Web-Ui plan builder
     */
    @Override
    Result executeRelAlg( final Request req, final Response res ) {
        UIRelNode topNode = gson.fromJson( req.body(), UIRelNode.class );

        Transaction transaction = this.transactionManager.startTransaction( null, null, null );

        RelNode result;
        try {
            result = QueryPlanBuilder.buildFromTree( topNode, transaction );
        } catch ( Exception e ) {
            return new Result( e.getMessage() );
        }

        PolyphenyDbSignature signature = transaction.getQueryProcessor().processQuery( result );

        List<List<Object>> rows;
        try {
            @SuppressWarnings("unchecked") final Iterable<Object> iterable = signature.enumerable( transaction.getDataContext() );
            Iterator<Object> iterator = iterable.iterator();
            rows = MetaImpl.collect( signature.cursorFactory, LimitIterator.of( iterator, getPageSize() ), new ArrayList<>() );
        } catch ( Exception e ) {
            return new Result( e.getMessage() );
        }

        ArrayList<String[]> data = new ArrayList<>();
        for ( List<Object> row : rows ) {
            String[] temp = new String[row.size()];
            int counter = 0;
            for ( Object o : row ) {
                temp[counter] = o.toString();
                counter++;
            }
            data.add( temp );
        }

        try {
            transaction.commit();
        } catch ( TransactionException e ) {
            throw new RuntimeException( e );
        }

        DbColumn[] header = new DbColumn[signature.columns.size()];
        int counter = 0;
        for ( ColumnMetaData col : signature.columns ) {
            header[counter++] = new DbColumn( col.columnName );
        }
        return new Result( header, data.toArray( new String[0][] ) );
    }


    /**
     * Get available actions for foreign key constraints
     */
    @Override
    String[] getForeignKeyActions( Request req, Response res ) {
        return new String[]{ "CASCADE", "RESTRICT", "SET NULL", "SET DEFAULT" };
    }

}
