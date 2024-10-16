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
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.units.TemperatureResource;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.devices.buildingtechnology.Thermostat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.model.sensors.HumiditySensor;
import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resource.util.ValueResourceUtils;

/**
 *
 * @author jlapp
 */
public class IpThermostatChannel extends AbstractDeviceHandler {

    public static final String PARAM_TEMPERATUREFALL_MODUS = "TEMPERATUREFALL_MODUS";
    /**
     * Name ({@value}) of the decorator linking to the TempSens which shall be
     * used instead of the internal temperature sensor.
     */
    public static final String LINKED_TEMP_SENS_DECORATOR = "linkedTempSens";
    public static final String CONTROL_MODE_DECORATOR = "controlMode";

    Logger logger = LoggerFactory.getLogger(getClass());

    public IpThermostatChannel(HomeMaticConnection conn) {
        super(conn);
    }

    enum PARAMS {

        SET_POINT_TEMPERATURE() {

            @Override
            public float convertInput(float v) {
                return v + 273.15f;
            }

            @Override
            public float convertOutput(float v) {
                return v - 273.15f;
            }

        },
        ACTUAL_TEMPERATURE() {

            @Override
            public float convertInput(float v) {
                return v + 273.15f;
            }

        },
        VALVE_STATE() {

            @Override
            public float convertInput(float v) {
                return v / 100f;
            }

        },
        BATTERY_STATE,
        
        HUMIDITY() {

            @Override
            public float convertInput(float v) {
                return v / 100f;
            }

        },
        
        SET_POINT_MODE;

        public float convertInput(float v) {
            return v;
        }

        public float convertOutput(float v) {
            return v;
        }

    }

    class ThermostatEventListener implements HmEventListener {

        final Map<String, SingleValueResource> resources;
        final String address;
		final Thermostat thermos;

        public ThermostatEventListener(Map<String, SingleValueResource> resources, Thermostat t, String address) {
            this.resources = resources;
            this.address = address;
			this.thermos = t;
        }

        @Override
        public void event(List<HmEvent> events) {
			boolean setpointTempRead = false;
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                SingleValueResource res = resources.get(e.getValueKey());
                if (res == null) {
                    continue;
                }
                try {
                    PARAMS p = PARAMS.valueOf(e.getValueKey());
                    ValueResourceUtils.setValue(res, p.convertInput(e.getValueFloat()));
                    //((FloatResource) res).setValue(p.convertInput(e.getValueFloat()));
                    logger.debug("resource updated ({}/{}): {} = {}", p, e, res.getPath(), e.getValue());
					setpointTempRead |= p == PARAMS.SET_POINT_TEMPERATURE;
                } catch (IllegalArgumentException ex) {
                    //this block intentionally left blank
                }
            }
			if (setpointTempRead) {
				ThermostatUtils.checkForAdaptionFailure(conn, thermos, address, logger);
			}
        }

    }

    /*
    HmIP-eTRV-* (actual valves) are handled by IpThermostatBChannel
    
    provided parameters:
    HmIP-WTH-B: ACTUAL_TEMPERATURE, HUMIDITY, SET_POINT_TEMPERATURE
    */
    @Override
    public boolean accept(DeviceDescription desc) {
		String pType = desc.getParentType();
		boolean parentTypeMatches = pType != null && (
				/*"HMIP-eTRV".equalsIgnoreCase(pType) //XXX also matched by IpThermostatBChannel
				||*/ pType.toLowerCase().startsWith("hmip-wth-")
				|| pType.toLowerCase().startsWith("hmip-bwth")
		);
		return parentTypeMatches && "HEATING_CLIMATECONTROL_TRANSCEIVER".equalsIgnoreCase(desc.getType());
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        final String deviceAddress = desc.getAddress();
        logger.debug("setup THERMOSTAT handler for address {} type {}", desc.getAddress(), desc.getType());
        String swName = ResourceUtils.getValidResourceName("THERMOSTAT" + desc.getAddress());
        Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
        if (values == null) {
            logger.warn("received no VALUES parameters for device {}", desc.getAddress());
            return;
        }

        Thermostat thermos = parent.addDecorator(swName, Thermostat.class);
        ThermostatUtils.setupParameterResources(parent, desc, paramSets, conn, thermos, logger);
		ThermostatUtils.setupThermostatDecorators(parent, desc, paramSets, conn, thermos, logger);
        Map<String, SingleValueResource> resources = new HashMap<>();
        for (Map.Entry<String, ParameterDescription<?>> e : values.entrySet()) {
            switch (e.getKey()) {
                case "SET_POINT_TEMPERATURE": {
                    TemperatureResource reading = thermos.temperatureSensor().deviceFeedback().setpoint();
                    if (!reading.exists()) {
                        reading.create();
                        thermos.activate(true);
                    }
                    logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                    resources.put(e.getKey(), reading);
                    break;
                }
                case "SET_POINT_MODE": {
                        IntegerResource reading = thermos.getSubResource("controlModeFeedback", IntegerResource.class);
                        if (!reading.exists()) {
                            reading.create();
                            thermos.activate(true);
                        }
                        logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                        resources.put(e.getKey(), reading);
                        break;
                    }
                case "ACTUAL_TEMPERATURE": {
                    TemperatureResource reading = thermos.temperatureSensor().reading();
                    if (!reading.exists()) {
                        reading.create();
                        thermos.activate(true);
                    }
                    logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                    resources.put(e.getKey(), reading);
                    break;
                }
                case "VALVE_STATE": {
                    FloatResource reading = thermos.valve().setting().stateFeedback();
                    if (!reading.exists()) {
                        reading.create();
                        thermos.activate(true);
                    }
                    logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                    resources.put(e.getKey(), reading);
                    break;
                }
                case "BATTERY_STATE": {
                    FloatResource reading = thermos.battery().internalVoltage().reading();
                    if (!reading.exists()) {
                        reading.create();
                        thermos.activate(true);
                    }
                    logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                    resources.put(e.getKey(), reading);
                    break;
                }
                case "HUMIDITY": {
                    HumiditySensor humSens = thermos.getSubResource("humiditySensor", HumiditySensor.class);
                    if (!humSens.reading().isActive()) {
                        humSens.reading().create();
                        humSens.reading().activate(false);
                        humSens.activate(false);
                    }
                    logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                    resources.put(e.getKey(), humSens.reading());
                    break;
                }
            }
        }

        TemperatureResource setpoint = thermos.temperatureSensor().settings().setpoint();
        setpoint.create();
        thermos.activate(true);

        setpoint.addValueListener(new ResourceValueListener<TemperatureResource>() {
            @Override
            public void resourceChanged(TemperatureResource t) {
                //XXX fails without the Double conversion...
                conn.performSetValue(deviceAddress, "SET_POINT_TEMPERATURE", Double.valueOf(t.getCelsius()));
            }

        }, true);

        conn.registerControlledResource(conn.getChannel(parent, deviceAddress), thermos);
        conn.registerControlledResource(conn.getChannel(parent, deviceAddress), thermos.temperatureSensor());
        ThermostatUtils.setupControlModeResource(thermos, conn, deviceAddress);
        ThermostatUtils.setupProgramListener(deviceAddress, conn, thermos, logger);
        conn.addEventListener(new ThermostatEventListener(resources, thermos, desc.getAddress()));
        setupHmParameterValues(thermos, desc.getAddress());
		ThermostatUtils.setupShutterContactLinking(thermos, conn, logger);
        IpThermostatBChannel.setupTempSensLinking(thermos, conn, logger);
    }

    class ParameterListener implements ResourceValueListener<SingleValueResource> {

        final String address;

        public ParameterListener(String address) {
            this.address = address;
        }

        @Override
        public void resourceChanged(SingleValueResource resource) {
            String paramName = resource.getName();

            Object resourceValue = null;
            if (resource instanceof IntegerResource) {
                resourceValue = ((IntegerResource) resource).getValue();
            } else {
                logger.warn("unsupported parameter type: " + resource);
            }

            Map<String, Object> parameterSet = new HashMap<>();
            parameterSet.put(paramName, resourceValue);
            conn.performPutParamset(address, "MASTER", parameterSet);
            logger.info("Parameter set 'MASTER' updated for {}: {}", address, parameterSet);
        }

    };

    private void setupHmParameterValues(Thermostat thermos, String address) {
        @SuppressWarnings("unchecked")
        ResourceList<SingleValueResource> masterParameters = thermos.addDecorator("HmParametersMaster", ResourceList.class);
        if (!masterParameters.exists()) {
            masterParameters.setElementType(SingleValueResource.class);
            masterParameters.create();
        }
        IntegerResource tf_modus = masterParameters.getSubResource(PARAM_TEMPERATUREFALL_MODUS, IntegerResource.class);
        ParameterListener l = new ParameterListener(address);
        if (tf_modus.isActive()) { //send active parameter on startup
            l.resourceChanged(tf_modus);
        }
        tf_modus.addValueListener(l, true);
    }

}
