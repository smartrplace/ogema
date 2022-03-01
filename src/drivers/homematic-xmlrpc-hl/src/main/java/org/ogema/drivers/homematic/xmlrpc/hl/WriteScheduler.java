/**
 * Copyright 2011-2018 Fraunhofer-Gesellschaft zur Förderung der angewandten Wissenschaften e.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ogema.drivers.homematic.xmlrpc.hl;

import java.io.Closeable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.ogema.core.application.ApplicationManager;
import org.ogema.drivers.homematic.xmlrpc.hl.events.HomeMaticEventMessages;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Schedules all writes for a single HomeMatic connection on a single thread and
 * handles retries of failed writes.
 */
public class WriteScheduler implements Closeable {
    
    class WriteCounts {
        
        long nextHourlyReset = setNextResetTime();
        
        Map<String, Integer> targetWriteCounts = new ConcurrentHashMap<>();
        
        long setNextResetTime() {
            return nextHourlyReset = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS).toEpochMilli();
        }
        
        void reset() {
            targetWriteCounts.clear();
            setNextResetTime();
        }
        
        void log() {
            AtomicLong total = new AtomicLong();
            StringBuilder sb = new StringBuilder("Homematic write operations for last hour: ");
            targetWriteCounts.forEach((t, c) -> {
                sb.append(t).append(": ").append(c).append(", ");
                total.addAndGet(c);
            });
            sb.append("total: ").append(total);
            logger.info(sb.toString());
        }
        
        public Map<String, Integer> getCounts() {
            return Collections.unmodifiableMap(targetWriteCounts);
        }
        
        void addAction(WriteAction a) {
            targetWriteCounts.compute(a.target(), (t,c) -> c == null ? 1 : c + 1);
        }
        
    }
    
    /** If a write action fails, sleep for this amount of time (ms) before
     the next write operation */
    private final long SLEEP_AFTER_ERROR = Long.getLong("ogema.homematic.xmlrpc.errorsleep", 100L);
    private final long RETRY_AFTER = Long.getLong("ogema.homematic.xmlrpc.retryafter", 15000L);
    private final int MAX_RETRIES = Integer.getInteger("ogema.homematic.xmlrpc.retries", 5);
    private final boolean DISABLE_COALESCE = Boolean.getBoolean("ogema.homematic.xmlrpc.no_coalesce");
    
    private final EventAdmin eventAdmin;
    private final ApplicationManager appman;
    final WriteCounts writeCounts = new WriteCounts();

    final static Comparator<WriteAction> WRITE_ACTION_COMPARATOR = new Comparator<WriteAction>() {
        @Override
        public int compare(WriteAction o1, WriteAction o2) {
            return o1.target().equals(o2.target())
                    ? Long.compare(o1.creationTimestamp(), o2.creationTimestamp())
                    : Long.compare(o1.nextRun, o2.nextRun);
        }
    };

    final PriorityBlockingQueue<WriteAction> writeActions = new PriorityBlockingQueue<>(50, WRITE_ACTION_COMPARATOR);
    private final Thread writerThread = new Thread(new Runnable() {
        @Override
        public void run() {
            schedulerImpl();
        }
    });

    final Logger logger = LoggerFactory.getLogger(getClass());

    public WriteScheduler(String name, ApplicationManager appman, EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
        this.appman = appman;
        writerThread.setName("OGEMA HomeMatic-XMLRPC writer thread " + name);
    }    

    public void start() {
        writerThread.start();
    }

    @Override
    public void close() {
        if (writerThread.isAlive()) {
            writerThread.interrupt();
        }
    }

    public void addWriteAction(WriteAction write) {
        writeActions.add(write);
    }

    private void schedulerImpl() {
        try {
            while (!Thread.interrupted()) {
                WriteAction next = writeActions.take();
                logger.trace("pending writes: {}", writeActions.size());
                
                int coalesce_count = 0;
                if (!DISABLE_COALESCE) {
                    Iterator<WriteAction> it = writeActions.iterator();
                    while (it.hasNext()) {
                        WriteAction wa = it.next();
                        if (wa.target().equals(next.target())) {
                            wa.coalesce(next); //next is the older action here
                            next = wa;
                            it.remove();
                            coalesce_count++;
                        }
                    }
                }
                if (coalesce_count > 0) {
                    logger.trace("skipped (removed) {} older write actions for {}", coalesce_count, next.target());
                }
                
                boolean success = false;
                try {
                    if (System.currentTimeMillis() > writeCounts.nextHourlyReset) {
                        writeCounts.log();
                        writeCounts.reset();
                    }
                    success = next.write();
                    writeCounts.addAction(next);
                } catch (Throwable t) {
                    logger.warn("WriteAction misbehaved and threw an exception", t);
                }
                if (!success) {
                    if (next.tries() >= MAX_RETRIES) {
                        logger.error("discarding write to {} after {} failed tries.",
                                next.target(), next.tries());
                        eventAdmin.postEvent(HomeMaticEventMessages.createWriteFailedEvent(appman, next.target()));
                    } else {
                        // the scheduler does not actually use system time,
                        // increasing nextRun will however push back failing writes
                        // so that other writes may be tried first.
                        // note that the comparator will not alter the order
                        // of writes to the same target.
                        next.nextRun = System.currentTimeMillis() + RETRY_AFTER;
                        writeActions.offer(next);
                    }
                    logger.debug("sleeping after failed write: {}ms", SLEEP_AFTER_ERROR);
                    Thread.sleep(SLEEP_AFTER_ERROR);
                } else {
                    if (next.tries() > 1) {
                        logger.info("failed write for {} succeeded on try {}", next.target(), next.tries());
                    }
                }
            }
        } catch (InterruptedException ie) {
            //ok, shutting down
            logger.debug("write thread shutting down");
        }

    }

}
