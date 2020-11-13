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

package org.polypheny.db.transaction;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.adapter.Store;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.CatalogDatabase;
import org.polypheny.db.catalog.entity.CatalogSchema;
import org.polypheny.db.catalog.entity.CatalogUser;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.information.InformationManager;
import org.polypheny.db.jdbc.JavaTypeFactoryImpl;
import org.polypheny.db.prepare.PolyphenyDbCatalogReader;
import org.polypheny.db.processing.DataMigrator;
import org.polypheny.db.processing.DataMigratorImpl;
import org.polypheny.db.processing.SqlProcessor;
import org.polypheny.db.processing.SqlProcessorImpl;
import org.polypheny.db.schema.PolySchemaBuilder;
import org.polypheny.db.schema.PolyphenyDbSchema;
import org.polypheny.db.statistic.StatisticsManager;


@Slf4j
public class TransactionImpl implements Transaction, Comparable {

    private static final AtomicLong TRANSACTION_COUNTER = new AtomicLong();

    @Getter
    private final long id;

    @Getter
    private final PolyXid xid;

    @Getter
    private final AtomicBoolean cancelFlag = new AtomicBoolean();

    @Getter
    private final CatalogUser user;
    @Getter
    private final CatalogSchema defaultSchema;
    @Getter
    private final CatalogDatabase database;

    private final TransactionManagerImpl transactionManager;

    @Getter
    private final String origin;

    @Getter
    private final MultimediaFlavor flavor;

    @Getter
    private final boolean analyze;

    private final AtomicLong statementCounter = new AtomicLong();

    private final List<String> changedTables = new ArrayList<>();

    @Getter
    private final List<Store> involvedStores = new CopyOnWriteArrayList<>();

    private final Set<Lock> lockList = new HashSet<>();


    TransactionImpl(
            PolyXid xid,
            TransactionManagerImpl transactionManager,
            CatalogUser user,
            CatalogSchema defaultSchema,
            CatalogDatabase database,
            boolean analyze,
            String origin,
            MultimediaFlavor flavor ) {
        this.id = TRANSACTION_COUNTER.getAndIncrement();
        this.xid = xid;
        this.transactionManager = transactionManager;
        this.user = user;
        this.defaultSchema = defaultSchema;
        this.database = database;
        this.analyze = analyze;
        this.origin = origin;
        this.flavor = flavor;
    }


    @Override
    public PolyphenyDbSchema getSchema() {
        return PolySchemaBuilder.getInstance().getCurrent();
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
        // Prepare to commit changes on all involved stores and the catalog
        boolean okToCommit = true;
        if ( RuntimeConfig.TWO_PC_MODE.getBoolean() ) {
            for ( Store store : involvedStores ) {
                okToCommit &= store.prepare( xid );
            }
        }

        if ( okToCommit ) {
            // Commit changes
            for ( Store store : involvedStores ) {
                store.commit( xid );
            }

            if ( changedTables.size() > 0 ) {
                StatisticsManager.getInstance().apply( changedTables );
            }
        } else {
            log.error( "Unable to prepare all involved entities for commit. Rollback changes!" );
            rollback();
            throw new TransactionException( "Unable to prepare all involved entities for commit. Changes have been rolled back." );
        }

        // Release locks
        LockManager.INSTANCE.removeTransaction( this );
        // Remove transaction
        transactionManager.removeTransaction( xid );
    }


    @Override
    public void rollback() throws TransactionException {
        try {
            //  Rollback changes to the stores
            for ( Store store : involvedStores ) {
                store.rollback( xid );
            }
            Catalog.getInstance().rollback();
        } finally {
            // Release locks
            LockManager.INSTANCE.removeTransaction( this );
            // Remove transaction
            transactionManager.removeTransaction( xid );
        }
    }


    @Override
    public JavaTypeFactory getTypeFactory() {
        return new JavaTypeFactoryImpl();
    }


    @Override
    public PolyphenyDbCatalogReader getCatalogReader() {
        return new PolyphenyDbCatalogReader(
                PolyphenyDbSchema.from( getSchema().plus() ),
                PolyphenyDbSchema.from( getSchema().plus() ).path( null ),
                getTypeFactory() );
    }


    @Override
    public SqlProcessor getSqlProcessor() {
        // TODO: This should be cached
        return new SqlProcessorImpl();
    }


    @Override
    public StatementImpl createStatement() {
        statementCounter.incrementAndGet();
        return new StatementImpl( this );
    }


    @Override
    public void addChangedTable( String qualifiedTableName ) {
        if ( !this.changedTables.contains( qualifiedTableName ) ) {
            log.debug( "Add changed table: {}", qualifiedTableName );
            this.changedTables.add( qualifiedTableName );
        }
    }


    @Override
    public int compareTo( @NonNull Object o ) {
        Transaction that = (Transaction) o;
        return this.xid.hashCode() - that.getXid().hashCode();
    }


    @Override
    public int hashCode() {
        return Objects.hash( xid );
    }


    @Override
    public boolean equals( Object o ) {
        if ( this == o ) {
            return true;
        }
        if ( o == null || getClass() != o.getClass() ) {
            return false;
        }
        Transaction that = (Transaction) o;
        return xid.equals( that.getXid() );
    }

    // For locking


    Set<Lock> getLocks() {
        return lockList;
    }


    void addLock( Lock lock ) {
        lockList.add( lock );
    }


    void removeLock( Lock lock ) {
        lockList.remove( lock );
    }


    void abort() {
        Thread.currentThread().interrupt();
    }


    @Override
    public long getNumberOfStatements() {
        return statementCounter.get();
    }


    @Override
    public DataMigrator getDataMigrator() {
        return new DataMigratorImpl();
    }

}
