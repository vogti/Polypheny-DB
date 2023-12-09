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

package org.polypheny.db.backup.datagatherer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.PolyImplementation;
import org.polypheny.db.ResultIterator;
import org.polypheny.db.backup.BackupManager;
import org.polypheny.db.catalog.Catalog;
import org.polypheny.db.catalog.exceptions.GenericRuntimeException;
import org.polypheny.db.catalog.logistic.DataModel;
import org.polypheny.db.languages.LanguageManager;
import org.polypheny.db.languages.QueryLanguage;
import org.polypheny.db.processing.ImplementationContext.ExecutedContext;
import org.polypheny.db.processing.QueryContext;
import org.polypheny.db.transaction.Statement;
import org.polypheny.db.transaction.Transaction;
import org.polypheny.db.transaction.TransactionManager;
import org.polypheny.db.type.entity.PolyValue;

@Slf4j
public class ExecuteQuery implements Runnable {
    private TransactionManager transactionManager;
    private String query;
    private DataModel dataModel;
    private long namespaceId;
    private File dataFile;

    public ExecuteQuery( TransactionManager transactionManager, String query, DataModel dataModel, long namespaceId, File dataFile ) {
        this.transactionManager = transactionManager;   //TODO(FF): is transactionmanager thread safe to pass it like this??
        this.query = query;
        this.dataModel = dataModel;
        this.namespaceId = namespaceId;
        this.dataFile = dataFile;
    }


    @Override
    public void run() {
        log.debug( "thread for gather entries entered" );
        Transaction transaction;
        Statement statement = null;
        PolyImplementation result;


        switch ( dataModel ) {
            case RELATIONAL:
                //fileChannel (is blocking... does it matter?) or
                // DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(dataFile)));

                /*
                //fileChannel way (randomaccessfile, nio)
                try(
                        //DataOutputStream out = new DataOutputStream( new BufferedOutputStream( new FileOutputStream( dataFile ) ) );  //channel doesn't work with this
                        RandomAccessFile writer = new RandomAccessFile( dataFile, "rw" );
                        FileChannel channel = writer.getChannel();

                        //method2
                        FileOutputStream fos = new FileOutputStream( dataFile );
                        FileChannel channel1 = fos.getChannel();

                    ) {

                    transaction = transactionManager.startTransaction( Catalog.defaultUserId, false, "Backup Entry-Gatherer" );
                    statement = transaction.createStatement();
                    //TODO(FF): be aware for writing into file with batches that you dont overwrite the entries already in the file (evtl you need to read the whole file again
                    ExecutedContext executedQuery = LanguageManager.getINSTANCE().anyQuery( QueryContext.builder().language( QueryLanguage.from( "sql" ) ).query( query ).origin( "Backup Manager" ).transactionManager( transactionManager ).batch( BackupManager.batchSize ).namespaceId( namespaceId ).build(), statement ).get( 0 );
                    ExecutedContext executedQuery1 = LanguageManager.getINSTANCE().anyQuery( QueryContext.builder().language( QueryLanguage.from( "sql" ) ).query( query ).origin( "Backup Manager" ).transactionManager( transactionManager ).namespaceId( Catalog.defaultNamespaceId ).build(), statement ).get( 0 );
                    // in case of results
                    ResultIterator iter = executedQuery.getIterator();
                    while ( iter.hasMoreRows() ) {
                        // liste mit tuples
                        List<List<PolyValue>> resultsPerTable = iter.getNextBatch();
                        log.info( resultsPerTable.toString() );
                        //FIXME(FF): if this is array: [[1, PolyList(value=[PolyList(value=[PolyList(value=[PolyBigDecimal(value=111), PolyBigDecimal(value=112)]), PolyList(value=[PolyBigDecimal(value=121), PolyBigDecimal(value=122)])]), PolyList(value=[PolyList(value=[PolyBigDecimal(value=211), PolyBigDecimal(value=212)]), PolyList(value=[PolyBigDecimal(value=221), PolyBigDecimal(value=222)])])])]]
                        //value is shown correctly for tojson

                        for ( List<PolyValue> row : resultsPerTable ) {
                            for ( PolyValue polyValue : row ) {
                                String byteString = polyValue.serialize();
                                //byte[] byteString2 = polyValue.serialize().getBytes(StandardCharsets.UTF_8);
                                String jsonString = polyValue.toTypedJson();

                                ByteBuffer buff = ByteBuffer.wrap(byteString.getBytes( StandardCharsets.UTF_8));
                                channel.write( buff );


                                //larger, testing easier, replace later
                                PolyValue deserialized = PolyValue.deserialize( byteString );
                                PolyValue deserialized2 = PolyValue.fromTypedJson( jsonString, PolyValue.class );
                                int jhg=87;
                            }
                        }

                        // flush only batchwise? is this even possible? does it make sense?

                    }

                } catch(Exception e){
                    throw new GenericRuntimeException( "Error while starting transaction", e );
                }

                 */


                // bufferedOutputStream, io way
                try(
                        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile), 32768));
                        //DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(dataFile)));

                        //String result = in.readUTF();
                        //in.close();

                ) {

                    transaction = transactionManager.startTransaction( Catalog.defaultUserId, false, "Backup Entry-Gatherer" );
                    statement = transaction.createStatement();
                    //TODO(FF): be aware for writing into file with batches that you dont overwrite the entries already in the file (evtl you need to read the whole file again
                    ExecutedContext executedQuery = LanguageManager.getINSTANCE().anyQuery( QueryContext.builder().language( QueryLanguage.from( "sql" ) ).query( query ).origin( "Backup Manager" ).transactionManager( transactionManager ).batch( BackupManager.batchSize ).namespaceId( namespaceId ).build(), statement ).get( 0 );
                    ExecutedContext executedQuery1 = LanguageManager.getINSTANCE().anyQuery( QueryContext.builder().language( QueryLanguage.from( "sql" ) ).query( query ).origin( "Backup Manager" ).transactionManager( transactionManager ).namespaceId( Catalog.defaultNamespaceId ).build(), statement ).get( 0 );
                    // in case of results
                    ResultIterator iter = executedQuery.getIterator();
                    while ( iter.hasMoreRows() ) {
                        // liste mit tuples
                        List<List<PolyValue>> resultsPerTable = iter.getNextBatch();
                        log.info( resultsPerTable.toString() );
                        //FIXME(FF): if this is array: [[1, PolyList(value=[PolyList(value=[PolyList(value=[PolyBigDecimal(value=111), PolyBigDecimal(value=112)]), PolyList(value=[PolyBigDecimal(value=121), PolyBigDecimal(value=122)])]), PolyList(value=[PolyList(value=[PolyBigDecimal(value=211), PolyBigDecimal(value=212)]), PolyList(value=[PolyBigDecimal(value=221), PolyBigDecimal(value=222)])])])]]
                        //value is shown correctly for tojson

                        for ( List<PolyValue> row : resultsPerTable ) {
                            for ( PolyValue polyValue : row ) {
                                String byteString = polyValue.serialize();
                                byte[] byteBytes = polyValue.serialize().getBytes(StandardCharsets.UTF_8);
                                String jsonString = polyValue.toTypedJson();

                                //out.write( byteBytes );
                                //out.write( byteString.getBytes( StandardCharsets.UTF_8 ) );
                                out.writeChars( jsonString );


                                //larger, testing easier, replace later
                                PolyValue deserialized = PolyValue.deserialize( byteString );
                                PolyValue deserialized2 = PolyValue.fromTypedJson( jsonString, PolyValue.class );
                                int jhg=87;
                            }
                        }

                        // flush only batchwise? is this even possible? does it make sense?

                    }

                } catch(Exception e){
                    throw new GenericRuntimeException( "Error while starting transaction", e );
                }




                /*
                try {
                    // get a transaction and a statement
                    transaction = transactionManager.startTransaction( Catalog.defaultUserId, false, "Backup Entry-Gatherer" );
                    statement = transaction.createStatement();
                    //TODO(FF): be aware for writing into file with batches that you dont overwrite the entries already in the file (evtl you need to read the whole file again
                    ExecutedContext executedQuery = LanguageManager.getINSTANCE().anyQuery( QueryContext.builder().language( QueryLanguage.from( "sql" ) ).query( query ).origin( "Backup Manager" ).transactionManager( transactionManager ).batch( BackupManager.batchSize ).namespaceId( namespaceId ).build(), statement ).get( 0 );
                    ExecutedContext executedQuery1 = LanguageManager.getINSTANCE().anyQuery( QueryContext.builder().language( QueryLanguage.from( "sql" ) ).query( query ).origin( "Backup Manager" ).transactionManager( transactionManager ).namespaceId( Catalog.defaultNamespaceId ).build(), statement ).get( 0 );
                    // in case of results
                    ResultIterator iter = executedQuery.getIterator();
                    while ( iter.hasMoreRows() ) {
                        // liste mit tuples
                        List<List<PolyValue>> resultsPerTable = iter.getNextBatch();
                        log.info( resultsPerTable.toString() );
                        //FIXME(FF): if this is array: [[1, PolyList(value=[PolyList(value=[PolyList(value=[PolyBigDecimal(value=111), PolyBigDecimal(value=112)]), PolyList(value=[PolyBigDecimal(value=121), PolyBigDecimal(value=122)])]), PolyList(value=[PolyList(value=[PolyBigDecimal(value=211), PolyBigDecimal(value=212)]), PolyList(value=[PolyBigDecimal(value=221), PolyBigDecimal(value=222)])])])]]
                        //value is shown correctly for tojson

                        for ( List<PolyValue> row : resultsPerTable ) {
                            for ( PolyValue polyValue : row ) {
                                String test = polyValue.serialize();
                                String jsonString = polyValue.toTypedJson();    //larger, testing easier, replace later
                                PolyValue deserialized = PolyValue.deserialize( test );
                                PolyValue deserialized2 = PolyValue.fromTypedJson( jsonString, PolyValue.class );    // gives nullpointerexception
                                int jhg=87;
                            }
                        }

                    }

                } catch ( Exception e ) {
                    throw new GenericRuntimeException( "Error while starting transaction", e );
                }

                 */
                break;

            case DOCUMENT:
                try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile), 32768));)
                {
                    // get a transaction and a statement
                    transaction = transactionManager.startTransaction( Catalog.defaultUserId, false, "Backup Entry-Gatherer" );
                    statement = transaction.createStatement();
                    ExecutedContext executedQuery = LanguageManager.getINSTANCE().anyQuery( QueryContext.builder().language( QueryLanguage.from( "mql" ) ).query( query ).origin( "Backup Manager" ).transactionManager( transactionManager ).namespaceId( namespaceId ).build(), statement ).get( 0 );

                    ResultIterator iter = executedQuery.getIterator();
                    while ( iter.hasMoreRows() ) {
                        // liste mit tuples
                        List<List<PolyValue>> resultsPerCollection = iter.getNextBatch();
                        out.writeChars( resultsPerCollection.toString() );
                        log.info( resultsPerCollection.toString() );
                    }
                } catch ( Exception e ) {
                    throw new GenericRuntimeException( "Error while starting transaction", e );
                }
                break;

            case GRAPH:
                try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile), 32768));)
                {
                    // get a transaction and a statement
                    transaction = transactionManager.startTransaction( Catalog.defaultUserId, false, "Backup Entry-Gatherer" );
                    statement = transaction.createStatement();
                    ExecutedContext executedQuery = LanguageManager.getINSTANCE().anyQuery( QueryContext.builder().language( QueryLanguage.from( "cypher" ) ).query( query ).origin( "Backup Manager" ).transactionManager( transactionManager ).namespaceId( namespaceId ).build(), statement ).get( 0 );

                    ResultIterator iter = executedQuery.getIterator();
                    while ( iter.hasMoreRows() ) {
                        // liste mit tuples
                        List<List<PolyValue>> graphPerNamespace = iter.getNextBatch();
                        log.info( graphPerNamespace.toString() );
                    }
                } catch ( Exception e ) {
                    throw new GenericRuntimeException( "Error while starting transaction", e );
                }
                break;

            default:
                throw new GenericRuntimeException( "Backup - GatherEntries: DataModel not supported" );
        }

    }

    private void createFile(String path) {

    }

}
