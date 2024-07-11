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
package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.model.sensors.SmokeDetector;
import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

/**
 * Handler for the SMOKE_DETECTOR channel version 2.
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=10"})
public class IpSmokeDetector extends AbstractDeviceHandler implements DeviceHandlerFactory {

	static Logger logger = LoggerFactory.getLogger(IpSmokeDetector.class);

	public static enum AlarmStatus {
		IDLE_OFF, PRIMARY_ALARM, INTRUSION_ALARM, SECONDARY_ALARM;
	}

	public static enum TestResult {
		NONE, SMOKE_TEST_OK, SMOKE_TEST_FAILED, COMMUNICATION_TEST_SENT, COMMUNICATION_TEST_OK;
	}

	public static enum Command {
		RESERVED_ALARM_OFF, INTRUSION_ALARM_OFF, INTRUSION_ALARM, SMOKE_TEST, COMMUNICATION_TEST, COMMUNICATION_TEST_REPEATED;
	}

	enum VALUES {

		SMOKE_DETECTOR_ALARM_STATUS("alarmStatus", IntegerResource.class) {
			@Override
			void setValue(SmokeDetector sd, HmEvent e) {
				super.setValue(sd, e);
				sd.reading().create();
				sd.reading().setValue(e.getValueInt() != 0);
				sd.reading().activate(false);
			}
		},
		SMOKE_DETECTOR_TEST_RESULT("testResult", IntegerResource.class),
		ERROR_DEGRADED_CHAMBER("degradedChamber", BooleanResource.class),
		ERROR_CODE("errorCode", IntegerResource.class),
		SMOKE_DETECTOR_COMMAND("command", IntegerResource.class) {
			@Override
			void setupListener(HomeMaticConnection conn, SmokeDetector sens, String address) {
				IntegerResource cmd = (IntegerResource) init(sens);
				logger.debug("adding command listener on {} / {}", cmd.getPath(), address);
				cmd.addValueListener((IntegerResource i) -> {
					conn.performSetValue(address, name(), i.getValue());
				}, true);
			}
		};

		private final String decorator;
		private final Class<? extends SingleValueResource> type;

		private VALUES(String decorator, Class<? extends SingleValueResource> type) {
			this.decorator = decorator;
			this.type = type;
		}

		SingleValueResource init(SmokeDetector sd) {
			SingleValueResource svr = sd.getSubResource(decorator, type).create();
			svr.activate(false);
			return svr;
		}

		void setValue(SmokeDetector sd, HmEvent e) {
			SingleValueResource svr = init(sd);
			logger.debug("setting {}={}", svr.getPath(), e.getValue());
			ValueResourceUtils.setValue(svr, e.getValue());
		}
		
		void setupListener(HomeMaticConnection conn, SmokeDetector sens, String address) {}

	}

	@Override
	public DeviceHandler createHandler(HomeMaticConnection connection) {
		return new IpSmokeDetector(connection);
	}

	public IpSmokeDetector() { //service factory constructor called by SCR
		super(null);
	}

	public IpSmokeDetector(HomeMaticConnection conn) {
		super(conn);
	}

	class SmokeDetectorListener implements HmEventListener {

		final SmokeDetector sens;
		final String address;

		public SmokeDetectorListener(SmokeDetector sens, String address) {
			this.sens = sens;
			this.address = address;
		}

		@Override
		public void event(List<HmEvent> events) {
			for (HmEvent e : events) {
				if (!address.equals(e.getAddress())) {
					continue;
				}
				try {
					VALUES v = VALUES.valueOf(e.getValueKey());
					v.setValue(sens, e);
				} catch (IllegalArgumentException iae) {
					logger.debug("unsupported event on {}: {}", address, e);
				}
			}
		}

	}

	@Override
	public boolean accept(DeviceDescription desc) {
		if (desc.getType().equals("SMOKE_DETECTOR")) {
			System.out.printf("%s: %s (v%d)%n", desc.getAddress(), desc.getType(), desc.getVersion());
			return desc.getVersion() == 2;
		}
		return false;
	}

	@Override
	public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
		logger.debug("setup SMOKE_DETECTOR handler for address {}", desc.getAddress());
		String resname = ResourceUtils.getValidResourceName("SMOKE_DETECTOR" + desc.getAddress());
		SmokeDetector sens = parent.addDecorator(resname, SmokeDetector.class).create();
		sens.reading().create().activate(false);

		Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
		if (values == null) {
			logger.warn("received no VALUES parameters for device {}", desc.getAddress());
			return;
		}

		Stream.of(VALUES.values())
				.forEach(v -> {
					if (values.keySet().contains(v.name())) {
						logger.debug("initializing value resource for {} {}/{}", desc.getType(), desc.getAddress(), v);
						v.init(sens);
						v.setupListener(conn, sens, desc.getAddress());
					}
				});
		conn.addEventListener(new SmokeDetectorListener(sens, desc.getAddress()));
		ChannelUtils.linkMaintenanceWhenAvailable(parent, sens.battery());
		sens.activate(true);
	}

}
