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

package org.polypheny.db.plan;


import java.util.ArrayList;
import java.util.List;


/**
 * MulticastRelOptListener implements the {@link AlgOptListener} interface by forwarding events on to a collection of other listeners.
 */
public class MulticastAlgOptListener implements AlgOptListener {

    private final List<AlgOptListener> listeners;


    /**
     * Creates a new empty multicast listener.
     */
    public MulticastAlgOptListener() {
        listeners = new ArrayList<>();
    }


    /**
     * Adds a listener which will receive multicast events.
     *
     * @param listener listener to add
     */
    public void addListener( AlgOptListener listener ) {
        listeners.add( listener );
    }


    // implement RelOptListener
    @Override
    public void algEquivalenceFound( AlgEquivalenceEvent event ) {
        for ( AlgOptListener listener : listeners ) {
            listener.algEquivalenceFound( event );
        }
    }


    // implement RelOptListener
    @Override
    public void ruleAttempted( RuleAttemptedEvent event ) {
        for ( AlgOptListener listener : listeners ) {
            listener.ruleAttempted( event );
        }
    }


    // implement RelOptListener
    @Override
    public void ruleProductionSucceeded( RuleProductionEvent event ) {
        for ( AlgOptListener listener : listeners ) {
            listener.ruleProductionSucceeded( event );
        }
    }


    // implement RelOptListener
    @Override
    public void algChosen( AlgChosenEvent event ) {
        for ( AlgOptListener listener : listeners ) {
            listener.algChosen( event );
        }
    }


    // implement RelOptListener
    @Override
    public void algDiscarded( AlgDiscardedEvent event ) {
        for ( AlgOptListener listener : listeners ) {
            listener.algDiscarded( event );
        }
    }
}

