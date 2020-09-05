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


import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.polypheny.db.adapter.Store;
import org.polypheny.db.adapter.java.JavaTypeFactory;
import org.polypheny.db.catalog.entity.CatalogSchema;
import org.polypheny.db.information.InformationManager;
import org.polypheny.db.schema.PolyphenyDbSchema;


public interface Transaction {

    PolyXid getXid();

    Statement createStatement();

    void commit() throws TransactionException;

    void rollback() throws TransactionException;

    void registerInvolvedStore( Store store );

    List<Store> getInvolvedStores();

    PolyphenyDbSchema getSchema();

    JavaTypeFactory getTypeFactory();

    boolean isAnalyze();

    InformationManager getQueryAnalyzer();

    AtomicBoolean getCancelFlag();

    CatalogSchema getDefaultSchema();

    void addChangedTable( String qualifiedTableName );

}
