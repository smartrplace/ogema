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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.xmlrpc.XmlRpcException;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.application.Timer;
import org.ogema.core.application.TimerListener;
import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.resourcemanager.ResourceDemandListener;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticDeviceAccess;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmLogicInterface;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
@Component(service = {Application.class},
		property = {"osgi.command.scope=hmhl", "osgi.command.function=listDevices",
			"osgi.command.function=writeCounts"})
public class HomeMaticDriver implements Application, HomeMaticDeviceAccess {

	private ApplicationManager appman;
	private EventAdmin eventAdmin;
	private ComponentContext ctx;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private final Map<HmLogicInterface, HmConnection> connections = new HashMap<>();
	// cache parameter sets (by device type + version) so that they are only retrieved from the CCU once
	private final Map<String, Map<String, Map<String, ParameterDescription<?>>>> deviceParameterSetCache = new ConcurrentHashMap<>();
	private final Set<HmDevice> failedSetup = Collections.newSetFromMap(new ConcurrentHashMap<>()); // devices for which deviceSetup failed.
	private volatile boolean setupInProgress = false;

	private ScheduledFuture<?> serviceRegistrationAction;
	private volatile ServiceRegistration<HomeMaticDeviceAccess> serviceRegistration;

	private final SortedSet<HandlerRegistration> handlerFactories = new TreeSet<>();

	// store accepted devices (by address) so they are not offered again on a different connection
	private final Map<String, ConnectedDevice> acceptedDevices = new TreeMap<>();

	private static class ConnectedDevice {

		final HmDevice toplevelDevice;

		final HmDevice device;

		final HomeMaticConnection connection;

		final DeviceHandler handler;

		public ConnectedDevice(HmDevice toplevelDevice, HmDevice device, HomeMaticConnection connection, DeviceHandler handler) {
			this.toplevelDevice = toplevelDevice;
			this.device = device;
			this.connection = connection;
			this.handler = handler;
		}

		@Override
		public String toString() {
			return String.format("%s (%s): %s%n", device.getPath(), toplevelDevice.getPath(), handler.getClass().getSimpleName());
		}

	}

	private static class HandlerRegistration implements Comparable<HandlerRegistration> {

		DeviceHandlerFactory fac;
		int ranking;

		public HandlerRegistration(DeviceHandlerFactory fac, int ranking) {
			this.fac = fac;
			this.ranking = ranking;
		}

		@Override
		public int compareTo(HandlerRegistration o) {
			int rankCompare = Integer.compare(ranking, o.ranking);
			return rankCompare == 0 ? o.fac.getClass().getCanonicalName().compareTo(fac.getClass().getCanonicalName())
					: -rankCompare;
		}

		@Override
		public boolean equals(Object obj) {
			return (obj instanceof HandlerRegistration) && fac.getClass() == ((HandlerRegistration) obj).fac.getClass();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(fac);
		}

		@Override
		public String toString() {
			return String.format("%d: %s", ranking, fac.getClass().getCanonicalName());
		}

	}

	// this is a static + greedy reference so that external handler factories
	// can always replace the built-in handlers.
	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policyOption = ReferencePolicyOption.GREEDY)
	protected void addHandlerFactory(DeviceHandlerFactory fac, Map<String, Object> serviceProperties) {
		int ranking = 1;
		if (serviceProperties.containsKey(Constants.SERVICE_RANKING)) {
			ranking = (int) serviceProperties.get(Constants.SERVICE_RANKING);
		}
		logger.debug("adding handler factory {}, rank {}", fac, ranking);
		synchronized (handlerFactories) {
			handlerFactories.add(new HandlerRegistration(fac, ranking));
		}
	}

	protected void removeHandlerFactory(DeviceHandlerFactory fac, Map<String, Object> serviceProperties) {
		// nothing to do for static reference handling
	}

	@Activate
	protected void activate(ComponentContext ctx) {
		this.ctx = ctx;
		/*
        delay registration until set of DeviceHandlerFactories is stable,
        this is to protect apps which use this service but do not deal with
        service dynamics properly.
		 */
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
		serviceRegistrationAction = ses.schedule(() -> {
			serviceRegistration = ctx.getBundleContext().registerService(HomeMaticDeviceAccess.class, this, null);
			logger.debug("registered HomeMaticDeviceAccess, available handlers: {}", handlerFactories);
		}, 3, TimeUnit.SECONDS);
		ses.shutdown();
	}

	@Deactivate
	protected void deactivate(ComponentContext ctx) {
		serviceRegistrationAction.cancel(true);
		if (serviceRegistration != null) {
			serviceRegistration.unregister();
		}
	}

	final ResourceDemandListener<HmLogicInterface> configListener = new ResourceDemandListener<HmLogicInterface>() {

		@Override
		public void resourceAvailable(HmLogicInterface t) {
			List<DeviceHandlerFactory> l = new ArrayList<>(handlerFactories.size());
			synchronized (handlerFactories) {
				for (HandlerRegistration reg : handlerFactories) {
					l.add(reg.fac);
				}
			}
			HmConnection conn = new HmConnection(l, appman, eventAdmin, ctx, HomeMaticDriver.this, t);
			connections.put(t, conn);
			conn.init();
		}

		@Override
		public void resourceUnavailable(HmLogicInterface t) {
			connections.remove(t).close();
		}

	};

	@Override
	public void start(ApplicationManager am) {
		appman = am;
		logger = am.getLogger();
		// delay actual setup so that all external ChannelHandlerFactories
		// are available (cosmetic change, quick restarts seem to work fine)
		final Timer t = appman.createTimer(2000);
		t.addListener(new TimerListener() {
			@Override
			public void timerElapsed(Timer timer) {
				timer.destroy();
				logger.info("HomeMatic driver ready, configuration pending");
				appman.getResourceAccess().addResourceDemand(HmLogicInterface.class, configListener);
			}
		});
		final Timer retrySetupTimer = appman.createTimer(60_000);
		retrySetupTimer.addListener(timer -> {
			failedSetup.forEach(dev -> {
				logger.debug("retrying setup for device {}", dev.getPath());
				setupDevice(dev);
			});
		});
	}

	@Override
	public void stop(AppStopReason asr) {
		if (appman != null) {
			appman.getResourceAccess().removeResourceDemand(HmLogicInterface.class, configListener);
		}
		final Iterator<HmConnection> it = connections.values().iterator();
		while (it.hasNext()) {
			final HmConnection conn = it.next();
			it.remove();
			conn.close();
		}
		acceptedDevices.clear();
	}

	protected HmConnection findConnection(HmDevice dev) {
		Resource p = dev;
		while (p.getParent() != null && !(p instanceof HmLogicInterface)) {
			p = p.getParent();
		}
		if (p == null) {
			throw new IllegalStateException("HmDevice in wrong place: " + dev.getPath());
		}
		return connections.get((HmLogicInterface) p);
	}

	public void setupDevice(HmDevice dev) {
		String address = dev.address().getValue();
		if (acceptedDevices.containsKey(address)) {
			logger.debug("device {} already controlled by handler type {}", address, acceptedDevices.get(address));
			return;
		}
		HmConnection conn = findConnection(dev);
		if (conn == null) {
			logger.warn("no connection for device {}", dev.getPath());
			return;
		}
		try {
			setupInProgress = true;
			DeviceDescription channelDesc = conn.persistence.getDeviceDescription(address);
			if (channelDesc == null) {
				logger.debug("requesting device description for {}", address);
				int maxRetries = 10;
				int retry = 0;
				while (true) {
					if (retry++ > maxRetries) {
						// handled by enclosing try, device will be added to failedSetup:
						throw new XmlRpcException("too many retries");
					}
					try {
						channelDesc = conn.client.getDeviceDescription(address);
						break;
					} catch (XmlRpcException ex) {
						if (ex.getMessage().equals("Unknown instance")) {
							throw ex;
						}
						if (ex.getMessage().toLowerCase().contains("invalid device")) {
							logger.warn("Device {} no longer on CCU @ {}, deactivating resource {}",
									dev.address().getValue(), conn.client.getServerUrl(), dev.getPath());
							dev.deactivate(true);
							return;
						}
						logger.warn("could not get device description for {}, retrying ({})", address, ex.getMessage());
					}
				}
			}
			if (channelDesc.isDevice()) {
				dev.addStructureListener(new DeletionListener(address, conn.client, true, logger));
			}
			for (DeviceHandler h : conn.handlers) {
				if (h.accept(channelDesc)) {
					logger.debug("handler available for {}: {}", address, h.getClass().getCanonicalName());
					Map<String, Map<String, ParameterDescription<?>>> paramSets;
					String parametersKey = channelDesc.getParentType() + "/" + channelDesc.getType() + "/" + channelDesc.getVersion();
					paramSets = deviceParameterSetCache.get(parametersKey);
					if (paramSets == null) {
						paramSets = new HashMap<>();
						for (String set : dev.paramsets().getValues()) {
							logger.debug("requesting paramset {} of device {}", set, address);
							for (int i = 0; i < 30; i++) {
								try {
									paramSets.put(set, conn.client.getParamsetDescription(address, set));
									break;
								} catch (XmlRpcException ex) {
									if (ex.getMessage().equals("Unknown instance")
											|| ex.getMessage().equals("Invalid device")
											|| i > 20) {
										throw ex;
									}
									logger.warn("could not get parameter set {}/{} from {}, retrying ({})", parametersKey, set, address, ex.getMessage());
								}
							}
						}
						deviceParameterSetCache.put(parametersKey, paramSets);
					} else {
						logger.debug("using cached parameter set for {} ({})", address, parametersKey);
					}
					HmDevice toplevelDevice = channelDesc.isDevice()
							? dev : (HmDevice) dev.getParent().getParent();
					h.setup(toplevelDevice, channelDesc, paramSets);
					acceptedDevices.put(address, new ConnectedDevice(toplevelDevice, dev, conn, h));
					logger.info("{} completed setup of {}", h.getClass().getSimpleName(), address);
					break;
				}
			}
			failedSetup.remove(dev);
		} catch (XmlRpcException ex) {
			logger.warn("failed to configure value resources for device {}, address {}: {}", dev.getPath(), address, ex.getMessage());
			failedSetup.add(dev);
		} finally {
			setupInProgress = false;
		}
	}

	public void storeEvent(HmEvent e, SingleValueResource res) {
		logger.debug("storing event data for {}@{} to {}", e.getValueKey(), e.getAddress(), res.getPath());
		if (res instanceof FloatResource) {
			((FloatResource) res).setValue(e.getValueFloat());
		} else if (res instanceof IntegerResource) {
			((IntegerResource) res).setValue(e.getValueInt());
		} else if (res instanceof StringResource) {
			((StringResource) res).setValue(e.getValueString());
		} else if (res instanceof BooleanResource) {
			((BooleanResource) res).setValue(e.getValueBoolean());
		} else {
			logger.warn("HomeMatic parameter resource is of unsupported type: {}", res.getResourceType());
		}
	}

	@Reference
	public void setEventAdmin(EventAdmin eventAdmin) {
		this.eventAdmin = eventAdmin;
	}

	@Override
	public Optional<HomeMaticConnection> getConnection(HmDevice device) {
		return acceptedDevices.values().stream()
				.filter(cd -> cd.device.equalsLocation(device))
				.map(cd -> cd.connection).findAny();
	}

	@Override
	public boolean update(HmDevice device) {
		Optional<ConnectedDevice> dev = acceptedDevices.values().stream()
				.filter(d -> d.device.equals(device)).findAny();
		if (!dev.isPresent()) {
			dev = acceptedDevices.values().stream()
					.filter(d -> d.toplevelDevice.equals(device)).findAny();
		}
		return dev.map(c -> c.handler.update(device)).orElse(Boolean.FALSE);
	}

	public void listDevices() {
		acceptedDevices.forEach((addr, cd) -> {
			System.out.printf("%s => %s, %s, %s%n",
					addr,
					cd.toplevelDevice.getName(),
					cd.handler.getClass().getSimpleName(),
					cd.device.getPath());
		});
		if (!failedSetup.isEmpty()) {
			failedSetup.forEach(dev -> {
				System.out.println("setup failed for: " + dev.getPath());
			});
		}
		if (setupInProgress) {
			System.out.println("Setup in progress...");
		}
	}

	public Map<String, Integer> writeCounts(String hmInterface) {
		return connections.entrySet().stream().filter(e -> e.getKey().getName()
				.equalsIgnoreCase(hmInterface)).findAny()
				.map(Entry::getValue).map(c -> c.writer.writeCounts.getCounts())
				.orElse(null);
	}

	@Override
	public Map<HmLogicInterface, HomeMaticConnection> getConnections() {
		return Collections.unmodifiableMap(connections);
	}
	
}
