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

import java.util.HashMap;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import java.util.List;
import java.util.Map;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.sensors.OccupancySensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.model.devices.sensoractordevices.SensorDevice;
import org.ogema.model.devices.sensoractordevices.SensorDeviceLabelled;
import org.ogema.model.sensors.GenericBinarySensor;
import org.ogema.model.sensors.LightSensor;
import org.ogema.tools.resource.util.ResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

/**
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=1"})
public class IpWaterDetectionTransmitter extends AbstractDeviceHandler implements DeviceHandlerFactory {

    Logger logger = LoggerFactory.getLogger(getClass());
    enum PARAMS {

        ALARMSTATE,
        MOISTURE_DETECTED,
		WATERLEVEL_DETECTED
    }

    @Override
    public DeviceHandler createHandler(HomeMaticConnection connection) {
        return new IpWaterDetectionTransmitter(connection);
    }
    
    public IpWaterDetectionTransmitter() { //service factory constructor called by SCR
        super(null);
    }

    public IpWaterDetectionTransmitter(HomeMaticConnection conn) {
        super(conn);
    }
    
    class SensorEventListener implements HmEventListener {

        final Map<String, BooleanResource> readingResources;
        final String address;

        public SensorEventListener(String address, Map<String, BooleanResource> readingResources) {
            this.address = address;
			this.readingResources = readingResources;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
				BooleanResource br = readingResources.get(e.getValueKey());
				if (br == null) {
					logger.debug("unsupported / ignored event type '{}' on {}", e.getValueKey(), address);
				} else {
					logger.debug("received event on {}: {}={}", address, e.getValueKey(), e.getValueBoolean());
					br.setValue(e.getValueBoolean());
				}
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        return "WATER_DETECTION_TRANSMITTER".equalsIgnoreCase(desc.getType());
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        logger.debug("setup WATER_DETECTION_TRANSMITTER handler for address {}", desc.getAddress());
        String swName = ResourceUtils.getValidResourceName("WATER_DETECTION_TRANSMITTER" + desc.getAddress());
        Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
        if (values == null) {
            logger.warn("received no VALUES parameters for device {}", desc.getAddress());
            return;
        }
        SensorDeviceLabelled sd = parent.addDecorator(swName, SensorDeviceLabelled.class);
		Map<String, BooleanResource> readings = new HashMap<>();
        
		GenericBinarySensor alarmSens = sd.sensors().getSubResource("ALARM", GenericBinarySensor.class);
		alarmSens.reading().create();
		alarmSens.reading().activate(false);
		alarmSens.activate(false);
		readings.put(PARAMS.ALARMSTATE.name(), alarmSens.reading());
		
		GenericBinarySensor waterSens = sd.sensors().getSubResource("WATERLEVEL", GenericBinarySensor.class);
		waterSens.reading().create();
		waterSens.reading().activate(false);
		waterSens.activate(false);
		readings.put(PARAMS.WATERLEVEL_DETECTED.name(), waterSens.reading());
		
		GenericBinarySensor moistureSens = sd.sensors().getSubResource("MOISTURE", GenericBinarySensor.class);
		moistureSens.reading().create();
		moistureSens.reading().activate(false);
		moistureSens.activate(false);
		readings.put(PARAMS.MOISTURE_DETECTED.name(), moistureSens.reading());
		
		sd.sensors().activate(false);
		sd.activate(false);
		
		sd.deviceTypeName().create();
		sd.deviceTypeName().setValue("Water Detector");
		sd.deviceTypeName().activate(false);
		
		sd.mainSensor().setAsReference(alarmSens);
		sd.mainSensorTitle().create();
		sd.mainSensorTitle().setValue("Alarm");
		sd.mainSensorTitle().activate(false);
        
        conn.addEventListener(new SensorEventListener(desc.getAddress(), readings));
		ThermostatUtils.setupParameterResources(parent, desc, paramSets, conn, sd, logger);
        
        sd.activate(true);
        //sd.sensors().activate(false);
    }

}
