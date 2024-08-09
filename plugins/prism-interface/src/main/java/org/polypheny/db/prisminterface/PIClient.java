/*
 * Copyright 2019-2024 The Polypheny Project
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

package org.polypheny.db.prisminterface;

import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.entity.LogicalUser;
import org.polypheny.db.catalog.entity.logical.LogicalNamespace;
import org.polypheny.db.prisminterface.metaRetrieval.PIClientInfoProperties;
import org.polypheny.db.prisminterface.statements.StatementManager;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionException;
import org.polypheny.db.transaction.TransactionManager;

@Slf4j
public class PIClient {

    @Getter
    private final String clientUUID;
    private final LogicalUser catalogUser;
    private Transaction currentTransaction;
    private final TransactionManager transactionManager;
    @Getter
    private final StatementManager statementManager;
    @Getter
    private final org.polypheny.db.prisminterface.metaRetrieval.PIClientInfoProperties PIClientInfoProperties;
    @Getter
    private final ClientConfiguration clientConfig;
    @Getter
    private final MonitoringPage monitoringPage;


    PIClient(
            String clientUUID,
            LogicalUser catalogUser,
            TransactionManager transactionManager,
            MonitoringPage monitoringPage,
            ClientConfiguration clientConfig ) {
        this.statementManager = new StatementManager( this );
        this.PIClientInfoProperties = new PIClientInfoProperties();
        this.clientConfig = clientConfig;
        this.clientUUID = clientUUID;
        this.catalogUser = catalogUser;
        this.transactionManager = transactionManager;
        this.monitoringPage = monitoringPage;
        monitoringPage.addStatementManager( statementManager );
    }


    public Transaction getOrCreateNewTransaction() {
        LogicalNamespace namespace = getNamespace();
        if ( hasNoTransaction() ) {
            currentTransaction = transactionManager.startTransaction(
                    catalogUser.id,
                    namespace.id,
                    false,
                    "PrismInterface" );
        }
        return currentTransaction;
    }


    public boolean isAutoCommit() {
        return Boolean.getBoolean( clientConfig.getProperty( ClientConfiguration.AUTOCOMMIT_PROPERTY_KEY ) );
    }

    public LogicalNamespace getNamespace() {
        Optional<LogicalNamespace> namespace = Catalog.getInstance().getSnapshot().getNamespace( clientConfig.getProperty( ClientConfiguration.NAMESPACE_PROPERTY_KEY ) );
        if ( namespace.isEmpty() ) {
            throw new PIServiceException( "Could not resolve default namespace." );
        }
        return namespace.get();
    }


    public void commitCurrentTransactionIfAuto() {
        if ( !isAutoCommit() ) {
            return;
        }
        commitCurrentTransactionUnsynchronized();
    }


    public void commitCurrentTransaction() throws PIServiceException {
        commitCurrentTransactionUnsynchronized();
    }


    private void commitCurrentTransactionUnsynchronized() throws PIServiceException {
        if ( hasNoTransaction() ) {
            return;
        }
        try {
            currentTransaction.commit();
        } catch ( TransactionException e ) {
            throw new PIServiceException( "Committing current transaction failed: " + e.getMessage() );
        } finally {
            clearCurrentTransaction();
        }
    }


    public void rollbackCurrentTransaction() throws PIServiceException {
        if ( hasNoTransaction() ) {
            return;
        }
        try {
            currentTransaction.getCancelFlag().set( true );
            currentTransaction.rollback();
        } catch ( TransactionException e ) {
            throw new PIServiceException( "Rollback of current transaction failed: " + e.getLocalizedMessage() );
        } finally {
            clearCurrentTransaction();
        }
    }


    private void clearCurrentTransaction() {
        currentTransaction = null;
    }


    public boolean hasNoTransaction() {
        return currentTransaction == null || !currentTransaction.isActive();
    }


    void prepareForDisposal() {
        statementManager.closeAll();
        rollbackCurrentTransaction();
        monitoringPage.removeStatementManager( statementManager );
    }

}
