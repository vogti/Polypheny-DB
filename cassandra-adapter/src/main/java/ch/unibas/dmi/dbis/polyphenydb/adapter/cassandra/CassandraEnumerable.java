/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Databases and Information Systems Research Group, University of Basel, Switzerland
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

package ch.unibas.dmi.dbis.polyphenydb.adapter.cassandra;


import ch.unibas.dmi.dbis.polyphenydb.jdbc.PolyphenyDbPrepare.Query;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerator;


@Slf4j
public class CassandraEnumerable extends AbstractEnumerable<Object> {

    final CqlSession session;
    final SimpleStatement simpleStatement;
    final BatchStatement batchStatement;
    final String stringStatement;
    final Integer offset;


    public CassandraEnumerable( final CqlSession session, final SimpleStatement simpleStatement ) {
        this( session, simpleStatement, 0 );
    }


    public CassandraEnumerable( final CqlSession session, final BatchStatement batchStatement ) {
        this.session = session;
        this.simpleStatement = null;
        this.batchStatement = batchStatement;
        this.stringStatement = null;
        this.offset = 0;
    }


    public CassandraEnumerable( CqlSession session, SimpleStatement statement, Integer offset ) {
        this.session = session;
        this.simpleStatement = statement;
        this.batchStatement = null;
        this.stringStatement = null;
        this.offset = offset;
    }


    public CassandraEnumerable( CqlSession session, String statement ) {
        this.session = session;
        this.simpleStatement = null;
        this.batchStatement = null;
        this.stringStatement = statement;
        this.offset = 0;

    }


    public static CassandraEnumerable of( CqlSession session, String statement ) {
        log.debug( "Creating string enumerable with: {}", statement );
        return new CassandraEnumerable( session, statement );
    }


    public static CassandraEnumerable of( CqlSession session, BatchStatement batchStatement ) {
        // BatchStatement cannot be turned into a string easily.
        log.debug( "Creating batched enumerable." );
        return new CassandraEnumerable( session, batchStatement );
    }


    public static CassandraEnumerable of( CqlSession session, SimpleStatement statement ) {
        log.debug( "Creating simple enumerable with: {}", statement.getQuery() );
        return new CassandraEnumerable( session, statement );
    }


    @Override
    public Enumerator<Object> enumerator() {

        final ResultSet results;
        if ( this.simpleStatement != null ) {
            results = session.execute( simpleStatement );
        } else if ( this.batchStatement != null ) {
            results = session.execute( batchStatement );
        } else if ( this.stringStatement != null ) {
            results = session.execute( this.stringStatement );
        } else {
            throw new AssertionError( "unable to run enumerator..." );
        }
        // Skip results until we get to the right offset
        int skip = 0;
        Enumerator<Object> enumerator = new CassandraEnumerator( results );
        while ( skip < offset && enumerator.moveNext() ) {
            skip++;
        }
        return enumerator;
    }

}
