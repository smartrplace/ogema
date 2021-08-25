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
import java.util.Locale;
import java.util.Map;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.units.TemperatureResource;
import org.ogema.core.resourcemanager.ResourceStructureEvent;
import org.ogema.core.resourcemanager.ResourceStructureListener;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.devices.buildingtechnology.Thermostat;
import org.ogema.model.sensors.TemperatureSensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import static org.ogema.drivers.homematic.xmlrpc.hl.channels.IpThermostatBChannel.CONTROL_MODE_DECORATOR;
import org.ogema.tools.resource.util.ResourceUtils;

/**
 *
 * @author jlapp
 */
public class ThermostatChannel extends AbstractDeviceHandler {
    
    public static final String PARAM_TEMPERATUREFALL_MODUS = "TEMPERATUREFALL_MODUS";
    /**
     * Name ({@value}) of the decorator linking to the TempSens to be used instead
     * of the internal temperature sensor.
     */
    public static final String LINKED_TEMP_SENS_DECORATOR = "linkedTempSens";

    Logger logger = LoggerFactory.getLogger(getClass());

    public ThermostatChannel(HomeMaticConnection conn) {
        super(conn);
    }
    
    enum PARAMS {

        SET_TEMPERATURE() {

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
        BATTERY_STATE;

        public float convertInput(float v) {
            return v;
        }

        public float convertOutput(float v) {
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
            for (HmEvent e: events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                SingleValueResource res = resources.get(e.getValueKey());
                if (res == null) {
                    continue;
                }
                for (PARAMS p: PARAMS.values()) {
                    if (p.name().equals(e.getValueKey())) {
                        ((FloatResource) res).setValue(p.convertInput(e.getValueFloat()));
                        logger.debug("resource updated: {} = {}", res.getPath(), e.getValue());
                    }
                }
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        //System.out.println("parent type = " + desc.getParentType());
        return ("HM-CC-RT-DN".equalsIgnoreCase(desc.getParentType()) && "CLIMATECONTROL_RT_TRANSCEIVER".equalsIgnoreCase(desc.getType()))
                || "THERMALCONTROL_TRANSMIT".equalsIgnoreCase(desc.getType());
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
        conn.registerControlledResource(conn.getChannel(parent, deviceAddress), thermos);
        ThermostatUtils.setupParameterResources(parent, desc, paramSets, conn, thermos, logger);
        Map<String, SingleValueResource> resources = new HashMap<>();
        for (Map.Entry<String, ParameterDescription<?>> e : values.entrySet()) {
            switch (e.getKey()) {
                case "SET_TEMPERATURE": {
                    TemperatureResource reading = thermos.temperatureSensor().deviceFeedback().setpoint();
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
            }
        }
        
        TemperatureResource setpoint = thermos.temperatureSensor().settings().setpoint();
        setpoint.create();
        thermos.activate(true);
        
        setpoint.addValueListener(new ResourceValueListener<TemperatureResource>() {
            @Override
            public void resourceChanged(TemperatureResource resource) {
                //XXX value type ??? does the homematic CCU really need string?
                String value = String.format(Locale.ENGLISH, "%.1f", resource.getCelsius());
                conn.performSetValue(deviceAddress, "SET_TEMPERATURE", Double.valueOf(resource.getCelsius()));
            }            
        }, true);
        
        conn.addEventListener(new WeatherEventListener(resources, desc.getAddress()));
        setupHmParameterValues(thermos, parent.address().getValue());
        setupTempSensLinking(thermos);
        setupControlModeResource(thermos, deviceAddress);
    }
    
    /* the control_mode param is not writeable on the bidcos model
      to set manual mode (1): write temperature (double) to MANU_MODE (use setpoint value)
      to set auto mode (0): write true to AUTO_MODE
    */
    private void setupControlModeResource(final Thermostat thermos, final String deviceAddress) {
        IntegerResource controlMode = thermos.addDecorator(CONTROL_MODE_DECORATOR, IntegerResource.class);
        controlMode.create().activate(false);
        controlMode.addValueListener(new ResourceValueListener<IntegerResource>() {
            @Override
            public void resourceChanged(IntegerResource resource) {
                int mode = resource.getValue();
                if (mode != 0 && mode != 1) {
                    logger.warn("invalid value ({}) set for HomeMatic CONTROL_MODE on {}", mode, resource.getLocation());
                    return;
                }
                //conn.performSetValue(deviceAddress, "CONTROL_MODE", CONTROL_MODES[mode]);
                setManualModeState(thermos, deviceAddress, mode == 1);
            }
        }, true);
    }
    
    void setManualModeState(Thermostat thermos, final String deviceAddress, boolean on) {
        if (on) {
            double val = thermos.temperatureSensor().settings().setpoint().getCelsius();
            conn.performSetValue(deviceAddress, "MANU_MODE", val);
        } else {
            conn.performSetValue(deviceAddress, "AUTO_MODE", true);
        }
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
        //XXX address mangling (parameters are set on device, not channel)
        if (address.lastIndexOf(":") != -1) {
            address = address.substring(0, address.lastIndexOf(":"));
        }
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
    
    private void linkTempSens(Thermostat thermos, TemperatureSensor tempSens, boolean removeLink) {
    	final String thermosAddress = getWeatherReceiverChannelAddress(thermos);
    	if (thermosAddress == null)
    		return;
        HmDevice tempSensChannel = conn.findControllingDevice(tempSens);
        if (tempSensChannel == null) {
            logger.warn("cannot find HomeMatic channel for TemperatureSensor {}", tempSens);
            return;
        }
        if (!tempSensChannel.type().getValue().startsWith("WEATHER")) {
            logger.warn(
                    "HomeMatic channel controlling TemperatureSensor {} is not a WEATHER channel (type is {}). Cannot link",
                    tempSens, tempSensChannel.type().getValue());
            return;
        }
        //XXX: address mangling (find WEATHER_RECEIVER channel instead?)
        String weatherAddress = tempSensChannel.address().getValue();
        
        if(removeLink) {
            conn.performRemoveLink(weatherAddress, thermosAddress);
        	return;
        }
        logger.info("HomeMatic weather channel for TempSens {}: {} being linked to thermostat {}", 
        		tempSens, weatherAddress, thermosAddress);
        conn.performAddLink(weatherAddress, thermosAddress, "TempSens", "external temperature sensor");
    }
    
    private void setupTempSensLinking(final Thermostat thermos) {
        TemperatureSensor tempSens = thermos.getSubResource(LINKED_TEMP_SENS_DECORATOR, TemperatureSensor.class);

        ResourceStructureListener l = new ResourceStructureListener() {
            @Override
            public void resourceStructureChanged(ResourceStructureEvent event) {
            	final Resource added = event.getChangedResource();
                if (event.getType() == ResourceStructureEvent.EventType.SUBRESOURCE_ADDED) {
                    if (added.getName().equals(LINKED_TEMP_SENS_DECORATOR) && added instanceof TemperatureSensor) {
                        linkTempSens(thermos, (TemperatureSensor) added, false);
                    }
                } else if (event.getType() == ResourceStructureEvent.EventType.SUBRESOURCE_REMOVED
                			&& added.getName().equals(LINKED_TEMP_SENS_DECORATOR)) {
                	// since we do not know which resource the link referenced before it got deleted
                	// we need to use the low level API to find out all links for the weather receiver channel
                    final String weatherChannelAddress = getWeatherReceiverChannelAddress(thermos);
                    if (weatherChannelAddress == null)
                    	return;
                    /*
                     * Example:
                     * Links... [{DESCRIPTION=external temperature sensor, SENDER=LEQ0568335:1, FLAGS=0, RECEIVER=OEQ2083227:1, NAME=TempSens}]
                     */
                	for (Map<String, Object> link : getConnection().performGetLinks(weatherChannelAddress, 0)) {
                		if (!weatherChannelAddress.equals(link.get("RECEIVER")))
                			continue;
                		final Object sender = link.get("SENDER");
                		if (!(sender instanceof String))
                			continue;
                		getConnection().performRemoveLink((String) sender, weatherChannelAddress);
                		logger.info("Thermostat-temperature sensor connection removed. Thermostat channel {}, temperature sensor {}",
                				weatherChannelAddress, sender);
                	}
                }

            }
        };
        thermos.addStructureListener(l);
        if (tempSens.isActive()) {
            linkTempSens(thermos, tempSens, false);
        }
    }
    
    private String getWeatherReceiverChannelAddress(final Thermostat thermos) {
    	final HmDevice thermostatChannel = conn.findControllingDevice(thermos);
        if (thermostatChannel == null) {
            logger.error("cannot find HomeMatic channel for Thermostat {}", thermos);
            return null;
        }
        final HmDevice thermostatDevice = conn.getToplevelDevice(thermostatChannel);
        return thermostatDevice.address().getValue() + ":1";
    }

}
