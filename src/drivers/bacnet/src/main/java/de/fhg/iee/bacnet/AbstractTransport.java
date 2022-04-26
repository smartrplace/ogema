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
package de.fhg.iee.bacnet;

import de.fhg.iee.bacnet.apdu.ApduConstants;
import de.fhg.iee.bacnet.apdu.ProtocolControlInformation;
import de.fhg.iee.bacnet.api.DeviceAddress;
import de.fhg.iee.bacnet.api.Indication;
import de.fhg.iee.bacnet.api.IndicationListener;
import de.fhg.iee.bacnet.api.Transport;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract Transport base class that handles invoke IDs and IndicationListeners
 * for concrete implementations. Implementations need to perform the link level
 * I/O through
 * {@link #sendData(java.nio.ByteBuffer, de.iwes.bacnet.api.Transport.Priority, boolean, de.iwes.bacnet.api.DeviceAddress) sendData}
 * and {@link #receivedPackage(de.iwes.bacnet.api.Indication) receivedPackage}.
 *
 * @author jlapp
 */
public abstract class AbstractTransport implements Transport {

    Collection<IndicationListener<?>> listeners = new ConcurrentLinkedQueue<>();
    final PriorityQueue<PendingReply> pendingReplies = new PriorityQueue<>();

    final Logger logger = LoggerFactory.getLogger(getClass());
    final Thread timeoutThread;
    private final Map<DeviceAddress, InvokeIds> invokeIds = new HashMap<>();

    private long messageTimeout = 350;
    private long segmentTimeout = 350;
    private int messageRetries = 3;
    
    private final ThreadGroup executorThreads = new ThreadGroup("BACnet UDP transport executors");
    private final ExecutorService executor;

    public AbstractTransport() {
        timeoutThread = new Thread(timeoutHandler, getClass().getSimpleName() + " Timeout and Retry Handler");
        executor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(executorThreads, r, AbstractTransport.this.getClass().getSimpleName() + " executor");
                return t;
            }
        });
    }

    protected class InvokeIds {

        BitSet ids = new BitSet(256);
        int lastId = 0;
        int inUse = 0;
        final DeviceAddress destination;

        public InvokeIds(DeviceAddress destination) {
            this.destination = destination;
        }
        
        synchronized int getId() {
            inUse++;
            //FIXME stupid way to prevent running out of invoke IDs, OTOH reduces load on slow devices
			int slowDownMin = 10;
            int slowDownCount = 5;
            int slowDownAmount = 200;
            int slowDownMax = 1500;
            //if (inUse > slowDownCount) {
                //logger.debug("running out of invoke IDs for destination={}, all destinations={}", destination, invokeIds.keySet());
                try {
                    int sleep = Math.min(slowDownMax,
							Math.max(slowDownMin, (inUse / slowDownCount) * slowDownAmount));
                    //logger.debug("sleeping {}ms...", sleep);
                    Thread.sleep(sleep);
                } catch (InterruptedException ex) {
                    logger.debug("", ex);
                }
            //}
            if (ids.nextClearBit(0) > 255) {
                logger.warn("out of invoke IDs");
                throw new IllegalStateException("out of invoke IDs");
            }
            do {
                lastId = (lastId + 1) % 256;
            } while (ids.get(lastId));
            ids.set(lastId);
            return lastId;
        }

        synchronized void release(int id) {
            inUse--;
            ids.clear(id & 255);
        }

    }
    
    protected InvokeIds getInvokeIds(DeviceAddress destination) {
        return invokeIds.computeIfAbsent(destination, InvokeIds::new);
    }

    private class PendingReply implements Comparable<PendingReply> {

        private final int invokeId;
        private final DeviceAddress destination;
        private final ByteBuffer data;
        private final Priority prio;
        private final IndicationListener<?> listener;
        private final IndicationFuture<?> f;
        private int tryNumber = 1;
        private volatile long expiryTime;
        private final boolean isSegmentTransmission;

        public PendingReply(boolean isSegmentTransmission, int invokeId, DeviceAddress destination, ByteBuffer data,
                Priority prio, IndicationListener<?> listener, IndicationFuture<?> f) {
            this.invokeId = invokeId;
            this.destination = destination;
            this.data = data.duplicate();
            this.prio = prio;
            this.listener = listener;
            this.f = f;
            this.isSegmentTransmission = isSegmentTransmission;
            if (isSegmentTransmission) {
                expiryTime = System.currentTimeMillis() + segmentTimeout;
            } else {
                expiryTime = System.currentTimeMillis() + messageTimeout;
            }
        }

        public int getInvokeId() {
            return invokeId;
        }

        public IndicationListener<?> getListener() {
            return listener;
        }

        public int getTryNumber() {
            return tryNumber;
        }

        public long getExpiryTime() {
            return expiryTime;
        }
        
        public long increaseExpiryTime() {
            if (isSegmentTransmission) {
                expiryTime = System.currentTimeMillis() + segmentTimeout;
            } else {
                expiryTime = System.currentTimeMillis() + messageTimeout;
            }
            return expiryTime;
        }

        public void setTryNumber(int tryNumber) {
            this.tryNumber = tryNumber;
        }

        @Override
        public int compareTo(PendingReply o) {
            int cmpTimes = Long.compare(expiryTime, o.expiryTime);
            return cmpTimes;
        }

    }

    private final Runnable timeoutHandler = new Runnable() {

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                synchronized (pendingReplies) {
                    if (pendingReplies.isEmpty()) {
                        try {
                            pendingReplies.wait();
                        } catch (InterruptedException ie) {
                            //shutdown...
                            break;
                        }
                        continue;
                    }
                    PendingReply next = pendingReplies.peek();
                    long now = System.currentTimeMillis();
                    if (now >= next.expiryTime) {
                        pendingReplies.remove();
                        if (next.tryNumber >= messageRetries) {
                            //TODO: notify listener
                            logger.warn("no reply from {} for invoke ID {}", next.destination, next.invokeId);
                            next.f.cancel(true);
                            getInvokeIds(next.destination).release(next.invokeId);
                        } else {
                            next.tryNumber++;
                            next.increaseExpiryTime();
                            pendingReplies.offer(next);
                            try {
                                logger.trace("resending message to {} for invoke ID {}", next.destination, next.invokeId);
                                //TODO: blocking i/o ?
                                next.data.rewind();
                                sendData(next.data, next.prio, true, next.destination);
                            } catch (IOException ex) {
                                //TODO: logging
                                logger.warn("resending failed", ex);
                            }
                        }
                    }
                    try {
                        long wait = next.expiryTime - now;
                        if (wait > 0) {
                            pendingReplies.wait(wait);
                        }
                    } catch (InterruptedException ie) {
                        //shutdown...
                        break;
                    }
                }
            }
            logger.debug("Message timeout handler shutting down.");
        }
    };

    protected final boolean hasLocalInvokeId(ProtocolControlInformation pci) {
        int pduType = pci.getPduType();
        return pduType == ApduConstants.TYPE_COMPLEX_ACK
                || pduType == ApduConstants.TYPE_SIMPLE_ACK
                || pduType == ApduConstants.TYPE_SEGMENT_ACK
                || pduType == ApduConstants.TYPE_ERROR
                || pduType == ApduConstants.TYPE_REJECT
                || pduType == ApduConstants.TYPE_ABORT;
    }

    private void executeListener(final PendingReply r, final Indication i) {
        if (r.getListener() == null) {
            return;
        }
        Runnable listenerCall = new Runnable() {
            @Override
            public void run() {
                try {
                    Object v = r.getListener().event(i);
                    r.f.resolve(v, null);
                } catch (Throwable t) {
                    //logger.warn("exception in event listener", t);
                    r.f.resolve(null, t);
                }
            }
        };
        executor.execute(listenerCall);
    }

    protected final void receivedPackage(Indication indication) {
        boolean indicationHandled = false;
        ProtocolControlInformation pci = indication.getProtocolControlInfo();
        int invokeId = -1;
        if (hasLocalInvokeId(pci)) {
            invokeId = pci.getInvokeId();
            boolean isSegmentAck = pci.getPduType() == ApduConstants.TYPE_SEGMENT_ACK;
            logger.trace("received message from {} for invoke ID {}", indication.getSource(), invokeId);
            synchronized (pendingReplies) {
                Iterator<PendingReply> it = pendingReplies.iterator();
                while (it.hasNext()) {
                    PendingReply r = it.next();
                    //FIXME pending replies for segments need special treatment 
                    if (r.getInvokeId() == invokeId) {
                        logger.trace("executing callback for invoke ID {}", invokeId);
                        it.remove();
                        indicationHandled = true;
                        Indication i = new DefaultIndication(indication);
                        executeListener(r, i);
                        break;
                    }
                }
                getInvokeIds(indication.getSource().toDestinationAddress()).release(invokeId);
                pendingReplies.notifyAll();
            }
        } else {
            logger.trace("indication PDU type={}", Integer.toBinaryString(pci.getPduType()));
        }
        if (!indicationHandled) {
            logger.trace("calling {} default handlers for unhandled indication from {}, invoke ID {}",
                    listeners.size(), indication.getSource(), invokeId);
            for (IndicationListener<?> l : listeners) {
                //TODO: needs executor
                Indication i = new DefaultIndication(indication);
                l.event(i);
            }
        }
    }

    @Override
    public final void addListener(IndicationListener<?> l) {
        listeners.add(l);
    }

    @Override
    public final void removeListener(IndicationListener<?> l) {
        Iterator<IndicationListener<?>> it = listeners.iterator();
        while (it.hasNext()) {
            if (it.next() == l) {
                it.remove();
            }
        }
    }

    @Override
    public abstract DeviceAddress getLocalAddress();

    @Override
    public abstract DeviceAddress getBroadcastAddress();

    @Override
    public final <V> Future<V> request(DeviceAddress destination, ByteBuffer data, Priority prio, boolean expectingReply, IndicationListener<V> l) throws IOException {
        ProtocolControlInformation pci = new ProtocolControlInformation(data);
        //FIXME: segmentation parameters...
        pci = pci.withAcceptanceInfo(true, ApduConstants.MAX_SEGMENTS.SIXTYFOUR, ApduConstants.RESPONSE_SIZE.UPTO_1476);
        IndicationFuture<V> f = new IndicationFuture<>();
        
        if (pci.isSegmented()) {
            data.rewind();
            PendingReply r = new PendingReply(true, pci.getInvokeId(), destination, data, prio, l, f);
            synchronized (pendingReplies) {
                pendingReplies.add(r);
                pendingReplies.notifyAll();
            }
            executeSend(data, prio, expectingReply, destination);
            return f;
        }
        
        if (pci.getPduType() == ApduConstants.TYPE_CONFIRMED_REQ) {
            int invokeId = getInvokeIds(destination).getId();
            
            PendingReply r = new PendingReply(false, invokeId, destination, data, prio, l, f);
            synchronized (pendingReplies) {
                pendingReplies.add(r);
                pendingReplies.notifyAll();
            }
            pci = pci.withInvokeId(invokeId);
            data.rewind();
            pci.write(data);
        	logger.trace("Schedulding message {} bytes to {}, expReply:{} invoke:{}",data.limit(), destination, expectingReply,
        			invokeId);
        } else {
        	logger.trace("Schedulding unconfirmed message {} bytes to {}, expReply:{}",data.limit(), destination, expectingReply);
        }
        data.rewind();
        
        if (pci.getPduType() == ApduConstants.TYPE_COMPLEX_ACK) {
            int segSize = getMaxSegmentSize(destination);
            if (data.limit() > segSize) {
                logger.trace("sending {} bytes to {} with segment size {}", data.limit(),segSize);
                //int invokeId = invokeIds.getId();
                pci = new ProtocolControlInformation(data);//.withInvokeId(invokeId);
                ByteBuffer rawData = data.slice();
                new SegmentationSender(this).complexAck(destination, pci,
                        rawData, prio, expectingReply, l, segSize - 20);
                return f;
            }    
        }

        executeSend(data, prio, expectingReply, destination);
        return f;
    }
    
    int getMaxSegmentSize(DeviceAddress destination) {
        //TODO respect the segment size specified by receiver
        return 1476;
    }
    
    private class IndicationFuture<V> implements Future<V> {
        
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final CountDownLatch done = new CountDownLatch(1);
        volatile V result;
        volatile Throwable t;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled.set(true);
            done.countDown();
            return result == null;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return cancelled.get() || result != null;
        }
        
        @SuppressWarnings("unchecked")
        private void resolve(Object result, Throwable t) {
            if (isDone()) {
                return;
            }
            this.result = (V) result;
            this.t = t;
            done.countDown();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            done.await();
            if (t != null) {
                throw new ExecutionException(t);
            }
            return result;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            done.await(timeout, unit);
            if (t != null) {
                throw new ExecutionException(t);
            }
            return result;
        }
        
    }

    private void executeSend(final ByteBuffer data, Priority prio, boolean expectingReply, DeviceAddress destination) {
        Runnable send = new Runnable() {
            @Override
            public void run() {
                try {
                	logger.trace("Sending out message {} bytes to {}, expReply:{}",data.limit(), destination, expectingReply);
                    sendData(data, prio, expectingReply, destination);
                } catch (IOException ex) {
                    logger.error("send failed for destination {}", destination, ex);
                }
            }
        };
        executor.execute(send);
    }

    /**
     * @param apdu a BACnet apdu, i.e. the application protocol control
     * information plus the actual service data.
     * @param prio BACnet message priority
     * @param expectingReply
     * @param destination
     * @throws java.io.IOException
     */
    protected abstract void sendData(ByteBuffer apdu, Priority prio, boolean expectingReply, DeviceAddress destination) throws IOException;

    @Override
    public final void close() throws IOException {
        timeoutThread.interrupt();
        executor.shutdown();
        doClose();
    }

    protected abstract void doClose() throws IOException;

    @Override
    public final AbstractTransport start() {
        timeoutThread.start();
        doStart();
        return this;
    }

    protected abstract void doStart();

}
