/*
 * Copyright 2019-2021 The Polypheny Project
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

package org.polypheny.db.monitoring.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.polypheny.db.config.RuntimeConfig;
import org.polypheny.db.monitoring.events.MonitoringEvent;
import org.polypheny.db.monitoring.persistence.MonitoringRepository;
import org.polypheny.db.util.background.BackgroundTask;
import org.polypheny.db.util.background.BackgroundTaskManager;

/**
 * MonitoringQueue implementation which stores the monitoring jobs in a
 * concurrentQueue and will process them with a background worker task.
 */
@Slf4j
public class MonitoringQueueImpl implements MonitoringQueue {

    // region private fields

    /**
     * monitoring queue which will queue all the incoming jobs.
     */
    private final Queue<MonitoringEvent> monitoringJobQueue = new ConcurrentLinkedQueue<>();
    private final Lock processingQueueLock = new ReentrantLock();
    private final MonitoringRepository repository;
    // number of elements beeing processed from the queue to the backend per "batch"
    private String backgroundTaskId;
    //For ever
    private long processedEventsTotal;

    //Since restart
    private long processedEvents;

    // endregion

    // region ctors


    /**
     * Ctor which automatically will start the background task based on the given boolean
     *
     * @param startBackGroundTask Indicates whether the background task for consuming the queue will be started.
     */
    public MonitoringQueueImpl( boolean startBackGroundTask, @NonNull MonitoringRepository repository ) {
        log.info( "write queue service" );

        if ( repository == null ) {
            throw new IllegalArgumentException( "repo parameter is null" );
        }

        this.repository = repository;

        if ( startBackGroundTask ) {
            this.startBackgroundTask();
        }
    }


    /**
     * Ctor will automatically start the background task for consuming the queue.
     */
    public MonitoringQueueImpl( @NonNull MonitoringRepository repository ) {
        this( true, repository );
    }

    // endregion

    // region public methods


    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if ( backgroundTaskId != null ) {
            BackgroundTaskManager.INSTANCE.removeBackgroundTask( backgroundTaskId );
        }
    }


    @Override
    public void queueEvent( @NonNull MonitoringEvent event ) {
        this.monitoringJobQueue.add( event );
    }


    /**
     * Display current number of elements in queue
     *
     * @return Current numbe of elements in Queue
     */
    @Override
    public long getNumberOfElementsInQueue() {
        return getElementsInQueue().size();
    }


    @Override
    public List<HashMap<String, String>> getInformationOnElementsInQueue() {
        List<HashMap<String, String>> infoList = new ArrayList<>();


        for ( MonitoringEvent event : getElementsInQueue() ) {
            HashMap<String, String> infoRow = new HashMap<String,String>();
            infoRow.put("type", event.getEventType() );
            infoRow.put("id", event.getId().toString() );
            infoRow.put("timestamp", event.getRecordedTimestamp().toString() );

            infoList.add( infoRow );
        }
        return infoList;
    }


    @Override
    public long getNumberOfProcessedEvents( boolean all ) {
        // TODO: Wird hier noch das persistiert? Könnten wir selbst als Metric aufbauen und persistieren ;-)
        if ( all ) {
            return processedEventsTotal;
        }
        //returns only processed events since last restart
        return processedEvents;
    }

    // endregion

    // region private helper methods


    private void startBackgroundTask() {
        if ( backgroundTaskId == null ) {
            backgroundTaskId = BackgroundTaskManager.INSTANCE.registerTask(
                    this::processQueue,
                    "Send monitoring jobs to job consumers",
                    BackgroundTask.TaskPriority.LOW,
                    BackgroundTask.TaskSchedulingType.EVERY_TEN_SECONDS
            );
        }
    }


    private List<MonitoringEvent> getElementsInQueue() {
        // TODO: Würde ich definitiv nicht so machen. Wenn du im UI die Anzahl Events
        //   wissen willst dann unbedingt nur die Anzahl rausgeben. Sonst gibt du die ganzen Instanzen raus und
        //   könntest die Queue zum übelsten missbrauchen ;-)

        List<MonitoringEvent> eventsInQueue = new ArrayList<>();

        for ( MonitoringEvent event : monitoringJobQueue ) {
            eventsInQueue.add( event );
        }

        return eventsInQueue;
    }

    private void processQueue() {
        log.debug( "Start processing queue" );
        this.processingQueueLock.lock();

        Optional<MonitoringEvent> event;

        try {

            // while there are jobs to consume:
            int countEvents = 0;
            while ( (event = this.getNextJob()).isPresent() && countEvents < RuntimeConfig.QUEUE_PROCESSING_ELEMENTS.getInteger() ) {
                log.debug( "get new monitoring job" + event.get().getId().toString() );

                //returns list of metrics which was produced by this particular event
                val dataPoints = event.get().analyze();

                //Sends all extracted metrics to subscribers
                for ( val dataPoint : dataPoints ) {
                    this.repository.persistDataPoint( dataPoint );
                }

                countEvents++;
            }
            processedEvents += countEvents;
            processedEventsTotal += countEvents;
        } finally {
            this.processingQueueLock.unlock();
        }
    }


    private Optional<MonitoringEvent> getNextJob() {
        if ( monitoringJobQueue.peek() != null ) {
            return Optional.of( monitoringJobQueue.poll() );
        }
        return Optional.empty();
    }

    // endregion
}
