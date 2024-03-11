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
 *
 * This file incorporates code covered by the following terms:
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.adapter.mongodb;


import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.calcite.linq4j.tree.Expression;
import org.polypheny.db.adapter.mongodb.MongoPlugin.MongoStore;
import org.polypheny.db.catalog.entity.physical.PhysicalEntity;
import org.polypheny.db.catalog.entity.physical.PhysicalField;
import org.polypheny.db.catalog.impl.Expressible;
import org.polypheny.db.plan.Convention;
import org.polypheny.db.schema.Namespace;


/**
 * Schema mapped onto a directory of MONGO files. Each table in the schema is a MONGO file in that directory.
 */
public class MongoNamespace extends Namespace implements Expressible {

    @Getter
    final MongoDatabase database;

    @Getter
    private final Convention convention = MongoAlg.CONVENTION;

    @Getter
    private final Map<String, org.polypheny.db.schema.Entity> tableMap = new HashMap<>();

    @Getter
    private final Map<String, org.polypheny.db.schema.Entity> collectionMap = new HashMap<>();
    private final MongoClient connection;
    private final TransactionProvider transactionProvider;
    @Getter
    private final GridFSBucket bucket;
    @Getter
    private final MongoStore store;
    @Getter
    private final long id;


    /**
     * Creates a MongoDB schema.
     *
     * @param database Mongo database name, e.g. "foodmart"
     */
    public MongoNamespace( long id, String database, MongoClient connection, TransactionProvider transactionProvider, MongoStore mongoStore ) {
        super( id, mongoStore.getAdapterId() );
        this.id = id;
        this.transactionProvider = transactionProvider;
        this.connection = connection;
        this.database = this.connection.getDatabase( database );
        this.bucket = GridFSBuckets.create( this.database, database );
        this.store = mongoStore;
    }


    public MongoEntity createEntity( PhysicalEntity physical, List<? extends PhysicalField> fields ) {
        return new MongoEntity( physical, fields, this, transactionProvider );
    }


    @Override
    public Expression asExpression() {
        return null;
    }


}

