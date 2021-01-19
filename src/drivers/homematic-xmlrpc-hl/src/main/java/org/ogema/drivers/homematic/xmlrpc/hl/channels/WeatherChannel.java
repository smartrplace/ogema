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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.devices.sensoractordevices.SensorDevice;
import org.ogema.model.sensors.HumiditySensor;
import org.ogema.model.sensors.Sensor;
import org.ogema.model.sensors.TemperatureSensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.model.sensors.AngleSensor;
import org.ogema.model.sensors.GenericBinarySensor;
import org.ogema.model.sensors.GenericFloatSensor;
import org.ogema.model.sensors.LightSensor;
import org.ogema.model.sensors.VelocitySensor;
import org.ogema.tools.resource.util.ResourceUtils;

/**
 *
 * @author jlapp
 */
public class WeatherChannel extends AbstractDeviceHandler {
    
    private static final double BRIGHTNESS_LUX_BASE = 1.04618;
    /**
     * Name of optional FloatResource decorator on the LightSensor for the raw
     * measurement to Lux computation (default value {@value #BRIGHTNESS_LUX_BASE}).
     */
    public static final String BRIGHTNESS_LUX_BASE_DECORATOR = "brightnessLuxBase";
    public static final String BRIGHTNESS_RAW_DECORATOR = "rawValue";

    Logger logger = LoggerFactory.getLogger(getClass());

    public WeatherChannel(HomeMaticConnection conn) {
        super(conn);
    }

    enum PARAMS {
        
        BRIGHTNESS,
        HUMIDITY {
                    @Override
                    public float convertInput(float v) {
                        return v / 100f; // percentage value -> range 0..1
                    }
        },
        RAIN_COUNTER,
        RAINING,
        TEMPERATURE {
                    @Override
                    public float convertInput(float v) {
                        return v + 273.15f;
                    }
                },
        WIND_DIRECTION,
        WIND_SPEED {
                    @Override
                    public float convertInput(float v) {
                        return v / 3.6f; // km/h -> m/s
                    }
        };

        public float convertInput(float v) {
            return v;
        }

    }

    class WeatherEventListener implements HmEventListener {

        final Map<String, SingleValueResource> resources;
        final String address;

        public WeatherEventListener(Map<String, SingleValueResource> resources, String address) {
            this.resources = resources;
            this.address = address;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                SingleValueResource res = resources.get(e.getValueKey());
                if (res == null) {
                    continue;
                }
                PARAMS p;
                try {
                    p = PARAMS.valueOf(e.getValueKey());
                } catch (IllegalArgumentException ex) {
                    // unsupported parameter
                    continue;
                }
                try {
                    switch (p) {
                        case BRIGHTNESS:
                            FloatResource v = (FloatResource) res;
                            LightSensor ls = v.getParent();
                            FloatResource baseDec = ls.getSubResource(BRIGHTNESS_LUX_BASE_DECORATOR, FloatResource.class);
                            int reading = e.getValueInt();
                            double base = baseDec.isActive()
                                    ? baseDec.getValue()
                                    : BRIGHTNESS_LUX_BASE;
                            double luxVal = Math.pow(base, reading);
                            v.setValue((float) luxVal);
                            FloatResource rawValDec = ls.getSubResource(BRIGHTNESS_RAW_DECORATOR, FloatResource.class);
                            rawValDec.create();
                            rawValDec.setValue(reading);
                            rawValDec.activate(false);
                            break;
                        case RAINING:
                            BooleanResource b = (BooleanResource) res;
                            b.setValue(e.getValueBoolean());
                            break;
                        default:
                            ((FloatResource) res).setValue(p.convertInput(e.getValueFloat()));
                    }
                }  catch (RuntimeException re) {
                    logger.debug("error in event handler", re);
                }
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        return "WEATHER".equalsIgnoreCase(desc.getType()) // WDS40, WDS100-C6-O-2
                || "WEATHER_TRANSMIT".equalsIgnoreCase(desc.getType()); // TC-IT-WM-W
    }
    
    private ResourceList<Sensor> getSensorList(HmDevice parent, String deviceName) {
        SensorDevice sd = parent.addDecorator(deviceName, SensorDevice.class);
        ResourceList<Sensor> sensors = sd.sensors();
        sensors.create();
        sd.activate(false);
        sensors.activate(false);
        return sensors;
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        logger.debug("setup WEATHER handler for address {}", desc.getAddress());
        String swName = ResourceUtils.getValidResourceName("WEATHER" + desc.getAddress());
        Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
        if (values == null) {
            logger.warn("received no VALUES parameters for device {}", desc.getAddress());
            return;
        }
        HmDevice weatherChannel = conn.getChannel(parent, desc.getAddress());
        Map<String, SingleValueResource> resources = new HashMap<>();
        for (Map.Entry<String, ParameterDescription<?>> e : values.entrySet()) {
            PARAMS p;
            try {
                p = PARAMS.valueOf(e.getKey());
            } catch (IllegalArgumentException ex) {
                // unsupported parameter
                continue;
            }
            switch (p) {
                case BRIGHTNESS: {
                    addSensorResource(parent, desc, swName, p, LightSensor.class, weatherChannel, resources);
                    break;
                }
                case HUMIDITY: {
                    addSensorResource(parent, desc, swName, p, HumiditySensor.class, weatherChannel, resources);
                    break;
                }
                case RAIN_COUNTER: {
                    addSensorResource(parent, desc, swName, p, GenericFloatSensor.class, weatherChannel, resources);
                    break;
                }
                case RAINING: {
                    addSensorResource(parent, desc, swName, p, GenericBinarySensor.class, weatherChannel, resources);
                    break;
                }
                case TEMPERATURE: {
                    addSensorResource(parent, desc, swName, p, TemperatureSensor.class, weatherChannel, resources);
                    break;
                }
                case WIND_DIRECTION: {
                    addSensorResource(parent, desc, swName, p, AngleSensor.class, weatherChannel, resources);
                    break;
                }
                case WIND_SPEED: {
                    addSensorResource(parent, desc, swName, p, VelocitySensor.class, weatherChannel, resources);
                    break;
                }
            }
        }
        conn.addEventListener(new WeatherEventListener(resources, desc.getAddress()));
    }
    
    <T extends Sensor> void addSensorResource(HmDevice parent, DeviceDescription desc, String swName, PARAMS p,
            Class<T> type, HmDevice weatherChannel, Map<String, SingleValueResource> resources) {
        ResourceList<Sensor> sensors = getSensorList(parent, swName);
                    SingleValueResource reading = (SingleValueResource) sensors.addDecorator(p.name(), type).reading();
                    conn.registerControlledResource(weatherChannel, reading.getParent());
                    if (!reading.exists()) {
                        reading.create();
                        reading.getParent().activate(true);
                    }
                    logger.debug("found supported WEATHER parameter {} on {}", p.name(), desc.getAddress());
                    resources.put(p.name(), reading);
    }

}
