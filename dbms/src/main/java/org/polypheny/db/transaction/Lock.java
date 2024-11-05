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

package org.polypheny.db.transaction;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Lock {

    public enum LockType {
        SHARED,
        EXCLUSIVE
    }


    private final ReentrantLock concurrencyLock = new ReentrantLock( true );
    private final Condition concurrencyCondition = concurrencyLock.newCondition();
    private final Set<Transaction> owners = new HashSet<>();

    private boolean isExclusive = false;


    public void acquire( Transaction transaction, LockType lockType, WaitForGraph waitForGraph ) throws InterruptedException {
        switch ( lockType ) {
            case SHARED -> acquireShared( transaction, waitForGraph );
            case EXCLUSIVE -> acquireExclusive( transaction, waitForGraph );
        }
    }


    public void upgradeToExclusive( Transaction transaction, WaitForGraph waitForGraph ) throws InterruptedException {
        concurrencyLock.lock();
        try {
            if ( isExclusive ) {
                return;
            }
            owners.remove( transaction );
            while ( !owners.isEmpty() ) {
                waitForGraph.addAndAbortIfDeadlock( transaction, owners );
                concurrencyCondition.await();
            }
            isExclusive = true;
            owners.add( transaction );
        } finally {
            concurrencyLock.unlock();
        }
    }


    public void release( Transaction transaction, WaitForGraph waitForGraph ) throws InterruptedException {
        concurrencyLock.lock();
        try {
            if ( isExclusive ) {
                owners.clear();
                isExclusive = false;
            }
            if ( !owners.isEmpty() ) {
                owners.remove( transaction );
            }
            waitForGraph.remove( transaction );
            concurrencyCondition.signalAll();
        } finally {
            concurrencyLock.unlock();
        }
    }


    public LockType getLockType() {
        return isExclusive ? LockType.EXCLUSIVE : LockType.SHARED;
    }


    private void acquireShared( Transaction transaction, WaitForGraph waitForGraph ) throws InterruptedException {
        concurrencyLock.lock();
        try {
            while ( isExclusive || hasWaitingTransactions() ) {
                waitForGraph.addAndAbortIfDeadlock( transaction, owners );
                concurrencyCondition.await();
            }
            owners.add( transaction );
        } finally {
            concurrencyLock.unlock();
        }
    }


    private void acquireExclusive( Transaction transaction, WaitForGraph waitForGraph ) throws InterruptedException {
        concurrencyLock.lock();
        try {
            while ( !owners.isEmpty() ) {
                waitForGraph.addAndAbortIfDeadlock( transaction, owners );
                concurrencyCondition.await();
            }
            isExclusive = true;
        } finally {
            concurrencyLock.unlock();
        }
    }


    private boolean hasWaitingTransactions() {
        return concurrencyLock.hasWaiters( concurrencyCondition );
    }


}
