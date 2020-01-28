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

package ch.unibas.dmi.dbis.polyphenydb.processing;


import ch.unibas.dmi.dbis.polyphenydb.DataContext;
import ch.unibas.dmi.dbis.polyphenydb.DataContext.SlimDataContext;
import ch.unibas.dmi.dbis.polyphenydb.PolyXid;
import ch.unibas.dmi.dbis.polyphenydb.QueryProcessor;
import ch.unibas.dmi.dbis.polyphenydb.SqlProcessor;
import ch.unibas.dmi.dbis.polyphenydb.Store;
import ch.unibas.dmi.dbis.polyphenydb.Transaction;
import ch.unibas.dmi.dbis.polyphenydb.TransactionException;
import ch.unibas.dmi.dbis.polyphenydb.adapter.java.JavaTypeFactory;
import ch.unibas.dmi.dbis.polyphenydb.catalog.Catalog;
import ch.unibas.dmi.dbis.polyphenydb.catalog.CatalogManagerImpl;
import ch.unibas.dmi.dbis.polyphenydb.catalog.entity.CatalogDatabase;
import ch.unibas.dmi.dbis.polyphenydb.catalog.entity.CatalogSchema;
import ch.unibas.dmi.dbis.polyphenydb.catalog.entity.CatalogUser;
import ch.unibas.dmi.dbis.polyphenydb.catalog.exceptions.CatalogTransactionException;
import ch.unibas.dmi.dbis.polyphenydb.config.RuntimeConfig;
import ch.unibas.dmi.dbis.polyphenydb.information.InformationManager;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.ContextImpl;
import ch.unibas.dmi.dbis.polyphenydb.jdbc.JavaTypeFactoryImpl;
import ch.unibas.dmi.dbis.polyphenydb.prepare.PolyphenyDbCatalogReader;
import ch.unibas.dmi.dbis.polyphenydb.schema.PolySchemaBuilder;
import ch.unibas.dmi.dbis.polyphenydb.schema.PolyphenyDbSchema;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParser.SqlParserConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class TransactionImpl implements Transaction {

    @Getter
    private final PolyXid xid;

    @Getter
    private final AtomicBoolean cancelFlag = new AtomicBoolean();

    private QueryProcessor queryProcessor;
    private Catalog catalog;

    @Getter
    private CatalogUser user;
    @Getter
    private CatalogSchema defaultSchema;
    private CatalogDatabase database;

    private TransactionManagerImpl transactionManager;

    @Getter
    private final boolean analyze;

    private DataContext dataContext = null;
    private ContextImpl prepareContext = null;

    @Getter
    private List<Store> involvedStores = new CopyOnWriteArrayList<>();

    private PolyphenyDbSchema cachedSchema = null;


    TransactionImpl( PolyXid xid, TransactionManagerImpl transactionManager, CatalogUser user, CatalogSchema defaultSchema, CatalogDatabase database, boolean analyze ) {
        this.xid = xid;
        this.transactionManager = transactionManager;
        this.user = user;
        this.defaultSchema = defaultSchema;
        this.database = database;
        this.analyze = analyze;
    }


    @Override
    public QueryProcessor getQueryProcessor() {
        if ( queryProcessor == null ) {
            queryProcessor = new VolcanoQueryProcessor( this );
        }
        return queryProcessor;
    }


    @Override
    public SqlProcessor getSqlProcessor( SqlParserConfig parserConfig ) {
        return new SqlProcessorImpl( this, parserConfig );
    }


    @Override
    public Catalog getCatalog() {
        if ( catalog == null ) {
            catalog = CatalogManagerImpl.getInstance().getCatalog( xid );
        }
        return catalog;
    }


    @Override
    public PolyphenyDbSchema getSchema() {
        if ( cachedSchema == null ) {
            cachedSchema = PolySchemaBuilder.getInstance().getCurrent( this );
        }
        return cachedSchema;
    }


    @Override
    public InformationManager getQueryAnalyzer() {
        return InformationManager.getInstance( xid.toString() );
    }


    @Override
    public void registerInvolvedStore( Store store ) {
        if ( !involvedStores.contains( store ) ) {
            involvedStores.add( store );
        }
    }


    @Override
    public void commit() throws TransactionException {
        try {
            // Prepare to commit changes on all involved stores and the catalog
            boolean okToCommit = true;
            if ( catalog != null ) {
                okToCommit &= catalog.prepare();
            }
            if ( RuntimeConfig.TWO_PC_MODE.getBoolean() ) {
                for ( Store store : involvedStores ) {
                    okToCommit &= store.prepare( xid );
                }
            }

            if ( okToCommit ) {
                // Commit changes
                if ( catalog != null ) {
                    catalog.commit();
                }
                for ( Store store : involvedStores ) {
                    store.commit( xid );
                }
            } else {
                log.error( "Unable to prepare all involved entities for commit. Rollback changes!" );
                rollback();
                throw new TransactionException( "Unable to prepare all involved entities for commit. Changes have been rolled back." );
            }
            transactionManager.removeTransaction( xid );
        } catch ( CatalogTransactionException e ) {
            log.error( "Exception while committing changes. Execution rollback!" );
            rollback();
            throw new TransactionException( e );
        } finally {
            cachedSchema = null;
        }
    }


    @Override
    public void rollback() throws TransactionException {
        //  rollback changes to the stores
        for ( Store store : involvedStores ) {
            store.rollback( xid );
        }

        // Rollback changes to the catalog
        try {
            if ( catalog != null ) {
                catalog.rollback();
            }
        } catch ( CatalogTransactionException e ) {
            throw new TransactionException( e );
        } finally {
            cachedSchema = null;
        }
    }


    @Override
    public DataContext getDataContext() {
        if ( dataContext == null ) {
            Map<String, Object> map = new LinkedHashMap<>();
            // Avoid overflow
            int queryTimeout = RuntimeConfig.QUERY_TIMEOUT.getInteger();
            if ( queryTimeout > 0 && queryTimeout < Integer.MAX_VALUE / 1000 ) {
                map.put( DataContext.Variable.TIMEOUT.camelName, queryTimeout * 1000L );
            }

            final AtomicBoolean cancelFlag;
            cancelFlag = getCancelFlag();
            map.put( DataContext.Variable.CANCEL_FLAG.camelName, cancelFlag );
            if ( RuntimeConfig.SPARK_ENGINE.getBoolean() ) {
                return new SlimDataContext();
            }
            dataContext = new DataContextImpl( new QueryProviderImpl(), map, getSchema(), getTypeFactory(), this );
        }
        return dataContext;
    }


    @Override
    public JavaTypeFactory getTypeFactory() {
        return new JavaTypeFactoryImpl();
    }


    @Override
    public ContextImpl getPrepareContext() {
        if ( prepareContext == null ) {
            prepareContext = new ContextImpl( getSchema(), getDataContext(), defaultSchema.name, database.id, user.id, this );
        }
        return prepareContext;
    }


    @Override
    public PolyphenyDbCatalogReader getCatalogReader() {
        return new PolyphenyDbCatalogReader(
                PolyphenyDbSchema.from( getSchema().plus() ),
                PolyphenyDbSchema.from( getSchema().plus() ).path( null ),
                getTypeFactory() );
    }


    /*
     * This should be called in the query interfaces for every new query before we start with processing it.
     */
    @Override
    public void resetQueryProcessor() {
        queryProcessor = null;
        dataContext = null;
        prepareContext = null;
        cachedSchema = null; // TODO: This should no be necessary.
    }

}
