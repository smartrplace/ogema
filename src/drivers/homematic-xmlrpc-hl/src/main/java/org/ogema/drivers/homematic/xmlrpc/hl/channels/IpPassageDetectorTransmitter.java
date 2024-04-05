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

import java.util.EnumMap;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import java.util.List;
import java.util.Map;
import org.ogema.core.model.simple.IntegerResource;
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
import org.ogema.model.devices.sensoractordevices.SensorDevice;
import org.ogema.model.sensors.GenericBinarySensor;
import org.ogema.model.sensors.GenericFloatSensor;
import org.ogema.model.sensors.Sensor;
import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

/**
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=1"})
public class IpPassageDetectorTransmitter extends AbstractDeviceHandler implements DeviceHandlerFactory {

	Logger logger = LoggerFactory.getLogger(getClass());

	enum PARAMS {
		PASSAGE_COUNTER_VALUE,
		PASSAGE_COUNTER_OVERFLOW,
		LAST_PASSAGE_DIRECTION,
		CURRENT_PASSAGE_DIRECTION,
	}

	@Override
	public DeviceHandler createHandler(HomeMaticConnection connection) {
		return new IpPassageDetectorTransmitter(connection);
	}

	public IpPassageDetectorTransmitter() { //service factory constructor called by SCR
		super(null);
	}

	public IpPassageDetectorTransmitter(HomeMaticConnection conn) {
		super(conn);
	}

	static <T extends Sensor> void storeSensorValue(SensorDevice sd, Class<T> type, String name, Object value) {
		Sensor s = sd.sensors().getSubResource(name, type);
		s.reading().create();
		ValueResourceUtils.setValue(s.reading(), value);
		if (!s.reading().isActive()) {
			s.reading().activate(false);
			s.activate(false);
			sd.sensors().activate(false);
			sd.activate(false);
		}
	}

	static <T extends Enum<T>> EnumMap<T, HmEvent> getEventsByEnum(Class<T> type, String address, List<HmEvent> events) {
		EnumMap<T, HmEvent> m = new EnumMap<>(type);
		for (HmEvent e : events) {
			if (!address.equals(e.getAddress())) {
				continue;
			}
			T p;
			try {
				p = Enum.valueOf(type, e.getValueKey());
			} catch (IllegalArgumentException iae) {
				//logger.debug("unsupported parameter: {}={}", e.getValueKey(), e.getValue());
				continue;
			}
			m.put(p, e);
		}
		return m;
	}

	class PassageEventListener implements HmEventListener {

		final SensorDevice sens;
		final String address;

		public PassageEventListener(SensorDevice sens, String address) {
			this.sens = sens;
			this.address = address;
		}

		@Override
		public void event(List<HmEvent> events) {
			EnumMap<PARAMS, HmEvent> em = getEventsByEnum(PARAMS.class, address, events);
			if (em.isEmpty()) {
				return;
			}
			logger.debug("got events: {}", em);
			HmEvent pc = em.get(PARAMS.PASSAGE_COUNTER_VALUE);
			HmEvent pco = em.get(PARAMS.PASSAGE_COUNTER_OVERFLOW);
			HmEvent pdirc = em.get(PARAMS.CURRENT_PASSAGE_DIRECTION);
			HmEvent pdirl = em.get(PARAMS.LAST_PASSAGE_DIRECTION);

			if (pc != null) {
				GenericFloatSensor pcSens = sens.sensors()
						.getSubResource(PARAMS.PASSAGE_COUNTER_VALUE.name(),
								GenericFloatSensor.class);
				pcSens.reading().create();
				boolean isIncrement = pcSens.reading().getValue() != pc.getValueInt();
				pcSens.reading().setValue(pc.getValueInt());
				pcSens.reading().activate(false);
				pcSens.activate(false);
				if (pdirc != null && isIncrement) {
					IntegerResource total = sens.getSubResource(
							pdirc.getValueBoolean() ? "TOTAL_PASSAGES_IN" : "TOTAL_PASSAGES_OUT",
							IntegerResource.class);
					total.create();
					total.setValue(total.getValue() + 1);
					total.activate(false);
				}
			}
			if (pco != null) {
				storeSensorValue(sens, GenericBinarySensor.class,
						PARAMS.PASSAGE_COUNTER_OVERFLOW.name(), pco.getValueBoolean());
			}
			if (pdirc != null) {
				storeSensorValue(sens, GenericBinarySensor.class,
						PARAMS.CURRENT_PASSAGE_DIRECTION.name(), pdirc.getValueBoolean());
			}
			if (pdirl != null) {
				storeSensorValue(sens, GenericBinarySensor.class,
						PARAMS.LAST_PASSAGE_DIRECTION.name(), pdirl.getValueBoolean());
			}
		}

	}

	@Override
	public boolean accept(DeviceDescription desc) {
		return "PASSAGE_DETECTOR_DIRECTION_TRANSMITTER".equalsIgnoreCase(desc.getType());
	}

	@Override
	public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
		logger.debug("setup PASSAGE_DETECTOR_DIRECTION_TRANSMITTER handler for address {}", desc.getAddress());
		String swName = ResourceUtils.getValidResourceName("PASSAGE_DETECTOR_DIRECTION_TRANSMITTER_" + desc.getAddress());
		Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
		if (values == null) {
			logger.warn("received no VALUES parameters for device {}", desc.getAddress());
			return;
		}
		SensorDevice sd = parent.addDecorator(swName, SensorDevice.class);
		sd.sensors().create();
		sd.sensors().activate(false);
		sd.activate(false);
		conn.addEventListener(new PassageEventListener(sd, desc.getAddress()));
	}

}
