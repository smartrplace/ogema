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
package de.fhg.iee.bacnet.services;

import de.fhg.iee.bacnet.apdu.ApduConstants;
import de.fhg.iee.bacnet.apdu.ProtocolControlInformation;
import de.fhg.iee.bacnet.api.DeviceAddress;
import de.fhg.iee.bacnet.api.Indication;
import de.fhg.iee.bacnet.api.IndicationListener;
import de.fhg.iee.bacnet.api.Transport;
import de.fhg.iee.bacnet.enumerations.BACnetAbortReason;
import de.fhg.iee.bacnet.enumerations.BACnetConfirmedServiceChoice;
import de.fhg.iee.bacnet.enumerations.BACnetErrorClass;
import de.fhg.iee.bacnet.enumerations.BACnetErrorCode;
import de.fhg.iee.bacnet.enumerations.BACnetObjectType;
import de.fhg.iee.bacnet.enumerations.BACnetPropertyIdentifier;
import de.fhg.iee.bacnet.enumerations.BACnetRejectReason;
import de.fhg.iee.bacnet.enumerations.BACnetUnconfirmedServiceChoice;
import de.fhg.iee.bacnet.tags.CompositeTag;
import de.fhg.iee.bacnet.tags.ObjectIdentifierTag;
import de.fhg.iee.bacnet.tags.Tag;
import de.fhg.iee.bacnet.tags.UnsignedIntTag;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
public class CovSubscriber implements Closeable {

	private final Transport transport;
	private final ObjectIdentifierTag subscribingObject;

	//private final AtomicInteger subscriptionID = new AtomicInteger();
	private final Set<DeviceAddress> knownDevices = new HashSet<>();
	private final Map<SubscriptionKey, List<Subscription>> subscriptions = new ConcurrentHashMap<>();
	//store subscriptions that have returned an unknown object response, do not try those again
	private final ScheduledExecutorService subscriptionRefresher = Executors.newSingleThreadScheduledExecutor();
	// randomize refresh intervals a little
	private final Random rnd = new Random();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public static class CovNotification {

		final int processIdentifier;

		final ObjectIdentifierTag initiatingDevice;

		final ObjectIdentifierTag monitoredObject;

		final int timeRemaining;

		final Map<Integer, CompositeTag> values;

		protected CovNotification(int processIdentifier, ObjectIdentifierTag initiatingDevice, ObjectIdentifierTag monitoredObject, int timeRemaining, Map<Integer, CompositeTag> values) {
			this.processIdentifier = processIdentifier;
			this.initiatingDevice = initiatingDevice;
			this.monitoredObject = monitoredObject;
			this.timeRemaining = timeRemaining;
			this.values = values;
		}

		public int getProcessIdentifier() {
			return processIdentifier;
		}

		public ObjectIdentifierTag getInitiatingDevice() {
			return initiatingDevice;
		}

		public ObjectIdentifierTag getMonitoredObject() {
			return monitoredObject;
		}

		public int getTimeRemaining() {
			return timeRemaining;
		}

		public Map<Integer, CompositeTag> getValues() {
			return values;
		}

	}

	public static interface CovListener {

		void receivedNotification(CovNotification n);

	}

	private static class SubscriptionKey {

		final int id;
		final int deviceInstance;
		final ObjectIdentifierTag object;
		volatile long nextRefresh;

		public SubscriptionKey(int id, int deviceInstance, ObjectIdentifierTag object) {
			this.id = id;
			this.deviceInstance = deviceInstance;
			this.object = object;
		}

		public SubscriptionKey(Subscription sub) {
			this.id = sub.id;
			this.deviceInstance = sub.deviceInstance;
			this.object = sub.object;
		}

		@Override
		public int hashCode() {
			int hash = 5;
			hash = 89 * hash + this.id;
			hash = 89 * hash + Objects.hashCode(this.deviceInstance);
			hash = 89 * hash + Objects.hashCode(this.object);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final SubscriptionKey other = (SubscriptionKey) obj;
			if (this.id != other.id) {
				return false;
			}
			if (!Objects.equals(this.deviceInstance, other.deviceInstance)) {
				return false;
			}
			return Objects.equals(this.object, other.object);
		}
	}

	public class Subscription {

		public final int id;
		public final DeviceAddress destination;
		public final int deviceInstance;
		public final ObjectIdentifierTag object;
		public final boolean confirmed;
		public final int lifetime;
		public final CovListener listener;
		private ScheduledFuture<?> refreshHandle;
		private volatile boolean subscribed = false;
		private volatile long lastRefresh = 0;

		public Subscription(int id, DeviceAddress destination, int deviceInstance, ObjectIdentifierTag object, boolean confirmed, int lifetime, CovListener listener) {
			this.id = id;
			this.destination = destination;
			this.deviceInstance = deviceInstance;
			this.object = object;
			this.confirmed = confirmed;
			this.lifetime = lifetime;
			this.listener = listener;
		}

		@Override
		public int hashCode() {
			int hash = 3;
			hash = 29 * hash + this.id;
			hash = 29 * hash + Objects.hashCode(this.destination);
			hash = 29 * hash + Objects.hashCode(this.object);
			hash = 29 * hash + (this.confirmed ? 1 : 0);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final Subscription other = (Subscription) obj;
			if (this.id != other.id) {
				return false;
			}
			if (this.confirmed != other.confirmed) {
				return false;
			}
			if (!Objects.equals(this.destination, other.destination)) {
				return false;
			}
			return Objects.equals(this.object, other.object);
		}

		public Future<Boolean> cancel() {
			logger.debug("Cancel subscription on {} / {}", object.getObjectType(), object.getInstanceNumber());
			if (refreshHandle != null) {
				refreshHandle.cancel(false);
			}
			return CovSubscriber.this.cancel(this);
		}

		public boolean isSubscribed() {
			return subscribed;
		}

	}

	public CovSubscriber(Transport transport, ObjectIdentifierTag subscribingObject) {
		this.transport = transport;
		this.subscribingObject = subscribingObject;
		transport.addListener(covNotificationListener);
		subscriptionRefresher.scheduleWithFixedDelay(this::checkFailedSubsriptions, 3, 3, TimeUnit.MINUTES);
	}

	private ByteBuffer createCancellationMessage(ObjectIdentifierTag oid, int subId) {
		ByteBuffer bb = ByteBuffer.allocate(50);
		ProtocolControlInformation pci = new ProtocolControlInformation(
				ApduConstants.APDU_TYPES.CONFIRMED_REQUEST, BACnetConfirmedServiceChoice.subscribeCOV)
				.withAcceptanceInfo(false, ApduConstants.MAX_SEGMENTS.UNSPECIFIED, ApduConstants.RESPONSE_SIZE.UPTO_1476);
		pci.write(bb);
		UnsignedIntTag subscriberProcessIdentifier = new UnsignedIntTag(0, Tag.TagClass.Context, subId);
		subscriberProcessIdentifier.write(bb);
		ObjectIdentifierTag monitoredObjectId = new ObjectIdentifierTag(
				1, Tag.TagClass.Context, oid.getObjectType(), oid.getInstanceNumber());
		monitoredObjectId.write(bb);
		bb.flip();
		return bb;
	}

	private Future<Boolean> cancel(Subscription sub) {
		logger.debug("Cancel subscription {} on {} / {} @ {}", sub.id, sub.object.getObjectType(), sub.object.getInstanceNumber(), sub.destination);
		try {
			IndicationListener<Boolean> simpleAckListener = new IndicationListener<Boolean>() {
				@Override
				public Boolean event(Indication ind) {
					ProtocolControlInformation pci = ind.getProtocolControlInfo();
					if (pci.getPduType() == ApduConstants.TYPE_SIMPLE_ACK) {
						subscriptions.getOrDefault(new SubscriptionKey(sub), Collections.emptyList()).remove(sub);
						sub.subscribed = false;
						return true;
					}
					return false;
				}
			};
			return transport.request(sub.destination, createCancellationMessage(sub.object, sub.id), Transport.Priority.Normal, true, simpleAckListener);
		} catch (IOException ex) {
			logger.error("sending of cancellation message failed", ex);
			CompletableFuture<Boolean> rval = new CompletableFuture<>();
			rval.completeExceptionally(ex);
			return rval;
		}
	}

	private ByteBuffer createSubscriptionMessage(int id, ObjectIdentifierTag object, boolean confirmed, int lifetime) {
		ByteBuffer bb = ByteBuffer.allocate(50);
		ProtocolControlInformation pci = new ProtocolControlInformation(
				ApduConstants.APDU_TYPES.CONFIRMED_REQUEST, BACnetConfirmedServiceChoice.subscribeCOV)
				.withAcceptanceInfo(false, ApduConstants.MAX_SEGMENTS.UNSPECIFIED, ApduConstants.RESPONSE_SIZE.UPTO_1476);
		pci.write(bb);
		UnsignedIntTag subscriberProcessIdentifier = new UnsignedIntTag(0, Tag.TagClass.Context, id);
		subscriberProcessIdentifier.write(bb);
		ObjectIdentifierTag monitoredObjectId = new ObjectIdentifierTag(
				1, Tag.TagClass.Context, object.getObjectType(), object.getInstanceNumber());
		monitoredObjectId.write(bb);
		new UnsignedIntTag(2, Tag.TagClass.Context, confirmed ? 1 : 0).write(bb);
		new UnsignedIntTag(3, Tag.TagClass.Context, lifetime).write(bb);

		bb.flip();
		return bb;
	}

	private void scheduleRefresh(final Subscription sub, long initialDelayS) {
		if (sub.lifetime > 0 && (sub.refreshHandle == null || sub.refreshHandle.isDone())) {
			long interval
					= //sub.lifetime; 
					delayJitter(sub.lifetime);
			initialDelayS = delayJitter(initialDelayS);
			try {
				logger.trace("scheduling refresh for ({})@{}: delay={}s, period={}s",
						sub.object, sub.destination, initialDelayS, interval);
				sub.refreshHandle = subscriptionRefresher.scheduleAtFixedRate(
						() -> resubscribe(sub), initialDelayS, interval, TimeUnit.SECONDS);
			} catch (IllegalArgumentException iae) {
				logger.error("delay={}, interval={}", initialDelayS, interval, iae);
			}
		}
	}

	private long delayJitter(long l) {
		double d = (9000d + rnd.nextInt(960)) / 10_000d;
		return (long) (d * l);
	}

	private IndicationListener<Subscription> subscribeResponseListener(Subscription sub) {
		return new IndicationListener<Subscription>() {
			@Override
			public Subscription event(Indication i) {
				ProtocolControlInformation pci = i.getProtocolControlInfo();
				switch (pci.getPduType()) {
					case ApduConstants.TYPE_SIMPLE_ACK:
						logger.trace("subscription confirmed for ({})@{}", sub.object, sub.destination);
						subscriptions.getOrDefault(new SubscriptionKey(sub), Collections.emptyList()).forEach(s -> {
							s.subscribed = true;
						});
						scheduleRefresh(sub, sub.lifetime);
						return sub;
					case ApduConstants.TYPE_REJECT: {
						logger.warn("subscription attempt returned REJECT: {}",
								BACnetRejectReason.getReasonString(pci.getServiceChoice()));
						subscriptions.getOrDefault(new SubscriptionKey(sub), Collections.emptyList()).forEach(s -> {
							s.subscribed = false;
						});
						return sub;
					}
					case ApduConstants.TYPE_ABORT: {
						logger.warn("subscription attempt returned ABORT: {}",
								BACnetAbortReason.getReasonString(pci.getServiceChoice()));
						subscriptions.getOrDefault(new SubscriptionKey(sub), Collections.emptyList()).forEach(s -> {
							s.subscribed = false;
						});
						return sub;
					}
					case ApduConstants.TYPE_ERROR: {
						ByteBuffer apdu = i.getData();
						CompositeTag errorClass = new CompositeTag(apdu);
						Optional<BACnetErrorClass> eClass = Stream.of(BACnetErrorClass.values()).filter(bec -> bec.getBACnetEnumValue() == errorClass.getUnsignedInt().intValue()).findAny();
						CompositeTag errorCode = new CompositeTag(apdu);
						Optional<BACnetErrorCode> eCode = Stream.of(BACnetErrorCode.values()).filter(bec -> bec.getBACnetEnumValue() == errorCode.getUnsignedInt().intValue()).findAny();
						apdu.reset();
						logger.warn("subscription attempt for {} on {} returned error {} / {}",
								sub.object, sub.destination,
								eClass.map(BACnetErrorClass::toString).orElse("unknown:" + errorClass.getUnsignedInt()),
								eCode.map(BACnetErrorCode::toString).orElse("unknown:" + errorCode.getUnsignedInt()));
						if (BACnetErrorClass.object.code == errorClass.getUnsignedInt().intValue()
								&& BACnetErrorCode.unknown_object.code == errorCode.getUnsignedInt().intValue()) {
							logger.warn("disabling subscription for unknown object ({})@{} ({})", sub.object, sub.deviceInstance, sub.destination);
							SubscriptionKey sk = new SubscriptionKey(sub);
							List<Subscription> l = subscriptions.remove(sk);
							if (l != null) {
								l.forEach(s -> {
									if (s.refreshHandle != null) {
										s.refreshHandle.cancel(false);
									}
								});
							}
							return sub;
						}
						subscriptions.getOrDefault(new SubscriptionKey(sub), Collections.emptyList()).forEach(s -> {
							s.subscribed = false;
						});
						return sub;
					}
					default: {
						logger.warn("subscription attempt for {} on {} returned unsupported response type {}",
								sub.object, sub.destination, pci.getPduType());
						return sub;
					}
				}
			}
		};
	}

	private void checkFailedSubsriptions() {
		logger.trace("retrying failed subscriptions");
		subscriptions.entrySet().stream()
				.filter(e -> e.getValue().stream().anyMatch(Predicate.not(Subscription::isSubscribed)))
				.map(Map.Entry::getValue).filter(Predicate.not(List::isEmpty))
				.map(l -> l.get(0)).forEach(sub -> {
			logger.trace("retrying subscription for ({})@{}", sub.object, sub.destination);
			resubscribe(sub);
		});
	}

	private void resubscribe(Subscription sub) {
		ByteBuffer bb = createSubscriptionMessage(sub.id, sub.object, sub.confirmed, sub.lifetime);
		try {
			IndicationListener<Subscription> refreshConfirmedListener = subscribeResponseListener(sub);
			logger.debug("request refresh ({}s) for subscription on ({})@{} (last refresh: {})",
					sub.lifetime, sub.object, sub.destination, Instant.ofEpochMilli(sub.lastRefresh));
			transport.request(sub.destination, bb, Transport.Priority.Normal, true, refreshConfirmedListener);
			sub.lastRefresh = System.currentTimeMillis();
		} catch (IOException ex) {
			logger.error("could not refresh subscription for ({})@{}: {}", sub.object, sub.destination, ex);
		}
	}

	public void resumeSubscriptions(DeviceAddress addr, int device) {
		//FIXME
		if (1 == 2) {
			return;
		}
		if (knownDevices.contains(addr.toDestinationAddress())) {
			return;
		}
		knownDevices.add(addr.toDestinationAddress());
		synchronized (subscriptions) {
			logger.debug("requesting active cov subscriptions from {} ({})",
					addr.toDestinationAddress(), device);
			ByteBuffer data = ConfirmedServices.buildReadPropertyApdu(BACnetObjectType.device.code,
					device, BACnetPropertyIdentifier.active_cov_subscriptions.code);
			try {
				transport.request(addr.toDestinationAddress(), data, Transport.Priority.Normal,
						true, createCovSubscriptionListener(device)).get(3, TimeUnit.SECONDS);
			} catch (IOException | InterruptedException | ExecutionException | TimeoutException ex) {
				logger.warn("could not get active subscriptions from {}: {}",
						addr.toDestinationAddress(), ex.getMessage());
			}
		}
	}

	private IndicationListener<Void> createCovSubscriptionListener(int deviceInstanceId) {
		return (Indication i) -> {
			ByteBuffer apdu = i.getData();
			ProtocolControlInformation pci = i.getProtocolControlInfo();
			if (pci.getPduType() == ApduConstants.APDU_TYPES.ERROR.getBACnetEnumValue()) {
				CompositeTag errorClass = new CompositeTag(apdu);
				BACnetErrorClass eClass = Stream.of(BACnetErrorClass.values())
						.filter(bec -> bec.getBACnetEnumValue() == errorClass.getUnsignedInt().intValue())
						.findAny().orElse(null);
				CompositeTag errorCode = new CompositeTag(apdu);
				BACnetErrorCode eCode = Stream.of(BACnetErrorCode.values())
						.filter(bec -> bec.getBACnetEnumValue() == errorCode.getUnsignedInt().intValue())
						.findAny().orElse(null);
				logger.debug("request for active COV subscriptions from {} failed: {} / {}",
						i.getSource().toDestinationAddress(), eClass, eCode);
				return null;
			}
			long now = System.currentTimeMillis();

			//object-id
			ObjectIdentifierTag _oid = new ObjectIdentifierTag(apdu);
			//property-id
			CompositeTag _propId = new CompositeTag(apdu);
			//property-values
			CompositeTag valueTagMulti = new CompositeTag(apdu);

			int myProcessIdentifier = subscribingObject.getInstanceNumber();
			int total = 0;
			int resumed = 0;
			CompositeTag recipient = null;
			CompositeTag processIdentifier = null;
			CompositeTag subscriptionOid = null;
			CompositeTag propRef = null;
			boolean confirmed;
			int timeRemaining = 0;
			Float covIncrement = null; //optional value

			for (CompositeTag t : valueTagMulti.getSubTags()) {
				switch (t.getTagNumber()) {
					case 0:
						total++;
						if (recipient != null) {
							//... build
							//XXX this will miss the last entry
							if (myProcessIdentifier == processIdentifier.getUnsignedInt().intValue()) {
								SubscriptionKey sk = new SubscriptionKey(
										processIdentifier.getUnsignedInt().intValue(),
										deviceInstanceId,
										new ObjectIdentifierTag(subscriptionOid.getOidType(), subscriptionOid.getOidInstanceNumber()));
								sk.nextRefresh = now + (1000 * timeRemaining);
								synchronized (subscriptions) {
									logger.debug("resumed subscription for ID {}, object {} @ {}", sk.id, sk.object, deviceInstanceId);
									subscriptions.computeIfAbsent(sk, __ -> new CopyOnWriteArrayList<>());
								}
								resumed++;
							}
							//recipient = null;
							covIncrement = null;
						}
						List<CompositeTag> recipientFields = new ArrayList<>(t.getSubTags());
						recipient = recipientFields.get(0);
						processIdentifier = recipientFields.get(1);
						break;
					case 1:
						List<CompositeTag> objectPropRefFields = new ArrayList<>(t.getSubTags());
						subscriptionOid = objectPropRefFields.get(0);
						propRef = objectPropRefFields.get(1);
						break;
					case 2:
						confirmed = t.getBooleanValue();
						break;
					case 3:
						timeRemaining = t.getUnsignedInt().intValue();
						break;
					case 4:
						covIncrement = t.getFloat();
						break;
				}
			}

			// last entry
			if (myProcessIdentifier == processIdentifier.getUnsignedInt().intValue()) {
				SubscriptionKey sk = new SubscriptionKey(
						processIdentifier.getUnsignedInt().intValue(),
						deviceInstanceId,
						new ObjectIdentifierTag(subscriptionOid.getOidType(), subscriptionOid.getOidInstanceNumber()));
				sk.nextRefresh = now + (1000 * timeRemaining);
				synchronized (subscriptions) {
					logger.debug("resumed subscription for ID {}, object {} @ {}", sk.id, sk.object, deviceInstanceId);
					subscriptions.computeIfAbsent(sk, __ -> new CopyOnWriteArrayList<>());
				}
				resumed++;
			}

			logger.info("read {} subscriptions from {} ({}), resumed {}, total subs {}",
					total, i.getSource().toDestinationAddress(), _oid.getInstanceNumber(), resumed, subscriptions.size());
			return null;
		};
	}

	public Future<Subscription> subscribe(DeviceAddress device, int instNum, ObjectIdentifierTag object, boolean confirmed, int lifetime, CovListener l) throws IOException {
		logger.trace("Send Subscribe to " + device + " for " + object.getObjectType() + " / " + object.getInstanceNumber());
		Objects.requireNonNull(object);
		Objects.requireNonNull(l);
		//int id = subscriptionID.incrementAndGet();
		int id = subscribingObject.getInstanceNumber();
		ByteBuffer bb = createSubscriptionMessage(id, object, confirmed, lifetime);
		final Subscription sub = new Subscription(id, device, instNum, object, confirmed, lifetime, l);

		synchronized (subscriptions) {
			SubscriptionKey key = new SubscriptionKey(sub);
			if (subscriptions.containsKey(key)) {
				SubscriptionKey existingKey = subscriptions.entrySet().stream()
						.filter(e -> e.getKey().equals(key)).findFirst().get().getKey();
				logger.trace("adding listener to existing subscription for {}, {}, {}",
						device, object.getObjectType(), object.getInstanceNumber());
				subscriptions.get(existingKey).add(sub);
				//XXX will create unnecessary refreshs / refresh should be based on SubscriptionKey
				long initialDelay = lifetime;
				if (existingKey.nextRefresh > 0) {
					initialDelay = Math.max(0, (existingKey.nextRefresh - System.currentTimeMillis()) / 1000);
				}
				scheduleRefresh(sub, initialDelay);
				CompletableFuture<Subscription> rval = new CompletableFuture<>();
				rval.complete(sub);
				return rval;
			} else {
				subscriptions.computeIfAbsent(key, _s -> new ArrayList<>()).add(sub);
			}
		}
		final IndicationListener<Subscription> subAckListener = subscribeResponseListener(sub);
		return transport.request(device, bb, Transport.Priority.Normal, true, subAckListener);
		//return id;
	}

	IndicationListener<Void> covNotificationListener = new IndicationListener<Void>() {
		@Override
		public Void event(Indication i) {
			ProtocolControlInformation pci = i.getProtocolControlInfo();
			boolean isConfirmed = pci.getPduType() == ApduConstants.TYPE_CONFIRMED_REQ
					&& pci.getServiceChoice() == BACnetConfirmedServiceChoice.confirmedCOVNotification.getBACnetEnumValue();
			if (isConfirmed || (pci.getPduType() == ApduConstants.TYPE_UNCONFIRMED_REQ
					&& pci.getServiceChoice() == BACnetUnconfirmedServiceChoice.unconfirmedCOVNotification.getBACnetEnumValue())) {
				int id = new UnsignedIntTag(i.getData()).getValue().intValue();
				ObjectIdentifierTag device = new ObjectIdentifierTag(i.getData());
				ObjectIdentifierTag object = new ObjectIdentifierTag(i.getData());
				DeviceAddress src = i.getSource();
				List<Subscription> subs = subscriptions.get(new SubscriptionKey(id, device.getInstanceNumber(), object));
				if (subs == null || subs.isEmpty()) {
					logger.debug("have no subscriptions for ID {}, object {} from {} @ {}", id, object, device.getInstanceNumber(), src);
					return null;
				}
				UnsignedIntTag timeRemaining = new UnsignedIntTag(i.getData());
				CompositeTag values = new CompositeTag(i.getData());
				Map<Integer, CompositeTag> valueMap = new HashMap<>();
				CompositeTag[] valuesArray = new CompositeTag[0];
				valuesArray = values.getSubTags().toArray(valuesArray);
				if (valuesArray.length % 2 != 0) {
					logger.error("received flaky values list from {}", i.getSource());
					return null;
				}
				for (int j = 0; j < valuesArray.length / 2; j++) {
					CompositeTag propId = valuesArray[2 * j];
					CompositeTag val = valuesArray[2 * j + 1];
					if (!val.isConstructed() || val.getSubTags().isEmpty()) {
						logger.error("cannot determine value for property #{} from {}", j, i.getSource());
						continue;
					}
					valueMap.put(propId.getUnsignedInt().intValue(), val.getSubTags().iterator().next());
				}
				if (isConfirmed) {
					sendAck(i);
				}
				CovNotification n = new CovNotification(id, device, object,
						timeRemaining.getValue().intValue(), Collections.unmodifiableMap(valueMap));
				//already on an event thread, call listener directly
				subs.forEach(sub -> sub.listener.receivedNotification(n));
			}
			return null;
		}
	};

	private void sendAck(Indication i) {
		ProtocolControlInformation ackPci
				= new ProtocolControlInformation(ApduConstants.APDU_TYPES.SIMPLE_ACK, BACnetConfirmedServiceChoice.confirmedCOVNotification)
						.withInvokeId(i.getProtocolControlInfo().getInvokeId());
		logger.trace("Send Ack to " + i.getSource().toDestinationAddress() + " from CovSubscriber");
		ByteBuffer bb = ByteBuffer.allocate(10);
		ackPci.write(bb);
		bb.flip();
		try {
			i.getTransport().request(i.getSource().toDestinationAddress(), bb, Transport.Priority.Normal, false, null);
		} catch (IOException ex) {
			logger.error("failed to send confirmedCOV acknowledgement", ex);
		}
	}

	/**
	 * Cancel all active subscriptions and shutdown threads.
	 */
	@Override
	public void close() throws IOException {
		transport.removeListener(covNotificationListener);
		subscriptionRefresher.shutdown();
		subscriptions.values().stream().filter(Predicate.not(List::isEmpty))
				.map(l -> l.get(0)).forEach(sub -> {
			try {
				sub.cancel().get();
			} catch (InterruptedException | ExecutionException ex) {
				logger.warn("could not cancel subscription {} to {}: {}", sub.id, sub.destination, ex);
			}
		});
		subscriptions.clear();
		/*
        for (Subscription sub : subscriptions.values()) {
            try {
                sub.cancel().get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.warn("could not cancel subscription {} to {}: {}", sub.id, sub.destination, ex);
            }
        }
        activeSubscriptions.clear();
		 */
	}

}
