/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
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
 *
 *
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
 */

package ch.unibas.dmi.dbis.polyphenydb.adapter.geode.rel;


import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.distributed.AbstractLauncher;
import org.apache.geode.distributed.ServerLauncher;
import org.junit.rules.ExternalResource;


/**
 * Manages embedded Geode instance using native {@link ServerLauncher}.
 */
public class GeodeEmbeddedPolicy extends ExternalResource {

    private final ServerLauncher launcher;


    private GeodeEmbeddedPolicy( final ServerLauncher launcher ) {
        Objects.requireNonNull( launcher, "launcher" );
        Preconditions.checkState( !launcher.isRunning(), "Launcher process is already running" );
        this.launcher = launcher;
    }


    @Override
    protected void before() {
        requireStatus( AbstractLauncher.Status.NOT_RESPONDING );
        launcher.start();
    }


    @Override
    protected void after() {
        if ( launcher.status().getStatus() == AbstractLauncher.Status.ONLINE ) {
            CacheFactory.getAnyInstance().close();
        }

        final Path pidFile = Paths.get( launcher.getWorkingDirectory() ).resolve( "vf.gf.server.pid" );
        launcher.stop();

        if ( Files.exists( pidFile ) ) {
            // delete PID file. Otherwise ("next") geode instance complains about existing process
            try {
                Files.delete( pidFile );
            } catch ( IOException e ) {
                throw new UncheckedIOException( e );
            }
        }
    }


    /**
     * Allows this instance to be shared by multiple test classes (in parallel). Guarantees that {@code before()} and {@code after()} methods will be called only once. This setup is useful
     * for maven (surefire) plugin which executes tests in parallel (including {@code @ClassRule} methods) and may initialize (or destroy) same resource multiple times.
     */
    GeodeEmbeddedPolicy share() {
        return new RefCountPolicy( this );
    }


    /**
     * Returns current cache instance which was initialized for tests.
     *
     * @throws IllegalStateException if server process didn't start
     */
    Cache cache() {
        requireStatus( AbstractLauncher.Status.ONLINE );
        return CacheFactory.getAnyInstance();
    }


    private void requireStatus( AbstractLauncher.Status expected ) {
        final AbstractLauncher.Status current = launcher.status().getStatus();
        Preconditions.checkState( current == expected, "Expected state %s but got %s", expected, current );
    }


    static GeodeEmbeddedPolicy create() {
        final ServerLauncher launcher = new ServerLauncher.Builder()
                .setMemberName( "fake-geode" )
                .set( "log-file", "" ) // log to stdout
                .set( "log-level", "severe" ) // minimal logging
                .set( "bind-address", "127.0.0.1" ) // accept internal connections only
                .setServerPort( 0 ) // bind to any available port
                .setPdxPersistent( false )
                .setPdxReadSerialized( true )
                .build();

        return new GeodeEmbeddedPolicy( launcher );
    }


    /**
     * Calls {@code before()} and {@code after()} methods only once (for first and last subscriber respectively). The implementation counts number of times {@link #before()} was called
     * which determines number of "clients". Delegate {@link #after()} is called when that count reaches zero again (when last "client" called that method).
     */
    private static class RefCountPolicy extends GeodeEmbeddedPolicy {

        private final AtomicInteger refCount;

        private final GeodeEmbeddedPolicy policy;


        RefCountPolicy( final GeodeEmbeddedPolicy policy ) {
            super( Objects.requireNonNull( policy, "policy" ).launcher );
            this.policy = policy;
            this.refCount = new AtomicInteger();
        }


        @Override
        GeodeEmbeddedPolicy share() {
            // for cases like share().share()
            return this;
        }


        @Override
        public synchronized void before() {
            if ( refCount.getAndIncrement() == 0 ) {
                // initialize only once
                policy.before();
            }
        }


        @Override
        protected void after() {
            if ( refCount.decrementAndGet() == 0 ) {
                // destroy only once
                policy.after();
            }
        }
    }
}

