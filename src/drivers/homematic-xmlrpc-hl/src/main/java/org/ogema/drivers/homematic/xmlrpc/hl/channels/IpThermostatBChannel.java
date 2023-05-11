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

import java.util.Collections;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.BooleanResource;
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
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmMaintenance;
import org.ogema.model.actors.OnOffSwitch;
import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resource.util.ValueResourceUtils;

/**
 * HEATING_CLIMATECONTROL_TRANSCEIVER channel for the HmIP-SK9 thermostat, copied
 * from the older IpThermostatChannel but does not have valve state or battery readings.
 * 
 * @author jlapp
 */
public class IpThermostatBChannel extends AbstractDeviceHandler {
    
    public static final String PARAM_TEMPERATUREFALL_MODUS = "TEMPERATUREFALL_MODUS";
    /**
     * Name ({@value}) of the decorator linking to the TempSens which shall be used instead
     * of the internal temperature sensor.
     */
    public static final String LINKED_TEMP_SENS_DECORATOR = "linkedTempSens";
    public static final String CONTROL_MODE_DECORATOR = "controlMode";
    
    static final String TEMPERATURE_RECEIVER_CHANNEL = "HEATING_CLIMATECONTROL_CL_RECEIVER";
    static final String TEMPERATURE_SENDER_CHANNEL = "HEATING_CLIMATECONTROL_CL_TRANSMITTER";

    Logger logger = LoggerFactory.getLogger(getClass());
    
    public IpThermostatBChannel(HomeMaticConnection conn) {
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
        
        LEVEL,
        
        SET_POINT_MODE,
		
		VALVE_ADAPTION,
        
        VALVE_STATE;		

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
		final Thermostat thermos;

        public WeatherEventListener(Map<String, SingleValueResource> resources, Thermostat thermos, String address) {
            this.resources = resources;
            this.address = address;
			this.thermos = thermos;
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
                try {
                    PARAMS p = PARAMS.valueOf(e.getValueKey());
                    ValueResourceUtils.setValue(res, p.convertInput(e.getValueFloat()));
                    logger.debug("resource updated ({}/{}): {} = {}", p, e, res.getPath(), e.getValue());
					if (p == PARAMS.SET_POINT_TEMPERATURE) {
						ThermostatUtils.checkForAdaptionFailure(conn, thermos, address, logger);
					}
                } catch (IllegalArgumentException ex) {
                    //this block intentionally left blank
                }
            }
        }

    }
    
    /*
      tested with HMIP-eTRV-B and HMIP-eTRV-2
    */
    @Override
    public boolean accept(DeviceDescription desc) {
        return desc.getParentType().toLowerCase().startsWith("hmip-etrv-")
                && "HEATING_CLIMATECONTROL_TRANSCEIVER".equalsIgnoreCase(desc.getType());
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
            try {
                switch (PARAMS.valueOf(e.getKey())) {
                    case SET_POINT_TEMPERATURE: {
                        TemperatureResource reading = thermos.temperatureSensor().deviceFeedback().setpoint();
                        if (!reading.exists()) {
                            reading.create();
                            thermos.activate(true);
                        }
                        logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                        resources.put(e.getKey(), reading);
                        break;
                    }
                    case SET_POINT_MODE: {
                        IntegerResource reading = thermos.getSubResource("controlModeFeedback", IntegerResource.class);
                        if (!reading.exists()) {
                            reading.create();
                            thermos.activate(true);
                        }
                        logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                        resources.put(e.getKey(), reading);
                        break;
                    }
                    case ACTUAL_TEMPERATURE: {
                        TemperatureResource reading = thermos.temperatureSensor().reading();
                        if (!reading.exists()) {
                            reading.create();
                            thermos.activate(true);
                        }
                        logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                        resources.put(e.getKey(), reading);
                        break;
                    }
                    case LEVEL: {
                        FloatResource reading = thermos.valve().setting().stateFeedback();
                        if (!reading.exists()) {
                            reading.create();
                            thermos.activate(true);
                        }
                        logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                        resources.put(e.getKey(), reading);
                        break;
                    }
					case VALVE_ADAPTION: {
						Resource adaOld = thermos.valve().getSubResource("adaption");
						if (adaOld != null && adaOld instanceof OnOffSwitch) {
							adaOld.delete();
						}
						logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
						BooleanResource adapt = thermos.valve()
								.getSubResource("startAdaption", BooleanResource.class).create();
                        adapt.create().activate(false);
                        adapt.addValueListener((BooleanResource r) -> {
							if (r.getValue()) {
								conn.performPutParamset(desc.getAddress(), "VALUES", Collections.singletonMap("VALVE_ADAPTION", true));
							}
						}, true);
                        break;
                    }
                    case VALVE_STATE: {
                        FloatResource valveState = thermos.valve().getSubResource("eq3state", FloatResource.class);
                        if (!valveState.exists()) {
                            valveState.create();
                            valveState.activate(false);
                        }
                        logger.debug("found supported thermostat parameter {} on {}", e.getKey(), desc.getAddress());
                        resources.put(e.getKey(), valveState);
                        break;
                    }
                }
            } catch (IllegalArgumentException iae) {
                // unsupported event type
            }
        }
        
        TemperatureResource setpoint = thermos.temperatureSensor().settings().setpoint();
        setpoint.create();
        linkMaintenanceWhenAvailable(parent, thermos);
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
        conn.addEventListener(new WeatherEventListener(resources, thermos, desc.getAddress()));
        setupHmParameterValues(thermos, desc.getAddress());
		ThermostatUtils.setupShutterContactLinking(thermos, conn, logger);
        setupTempSensLinking(thermos, conn, logger);
    }
    
    void linkMaintenanceWhenAvailable(final HmDevice device, final Thermostat thermos) {
        List<HmMaintenance> l = device.getSubResources(HmMaintenance.class, false);
        if (l.isEmpty()) {
            device.addStructureListener(new ResourceStructureListener() {
                @Override
                public void resourceStructureChanged(ResourceStructureEvent event) {
                    if (event.getType() != ResourceStructureEvent.EventType.SUBRESOURCE_ADDED) {
                        return;
                    }
                    if (HmMaintenance.class.isAssignableFrom(event.getChangedResource().getResourceType())) {
                        linkMaintencance((HmMaintenance) event.getChangedResource(), thermos);
                        device.removeStructureListener(this);
                    }
                }
            });
            // race condition during creation not resolveable here
            l = device.getSubResources(HmMaintenance.class, false);
            if (!l.isEmpty()) {
                linkMaintencance(l.get(0), thermos);
            }
        } else {
            linkMaintencance(l.get(0), thermos);
        }
    }
    
    void linkMaintencance(HmMaintenance mntn, Thermostat thermos) {
        thermos.battery().create();
        mntn.battery().setAsReference(thermos.battery());
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
    
    static void setupTempSensLinking(final Thermostat thermos, HomeMaticConnection conn, Logger logger) {
        TemperatureSensor tempSens = thermos.getSubResource(LINKED_TEMP_SENS_DECORATOR, TemperatureSensor.class);
        
        ResourceStructureListener l = new ResourceStructureListener() {

            @Override
            public void resourceStructureChanged(ResourceStructureEvent event) {
                Resource added = event.getChangedResource();
                if (event.getType() == ResourceStructureEvent.EventType.SUBRESOURCE_ADDED) {
                    if (added.getName().equals(LINKED_TEMP_SENS_DECORATOR) && added instanceof TemperatureSensor) {
                        DeviceHandlers.linkChannels(conn, added, TEMPERATURE_SENDER_CHANNEL,
                                thermos, TEMPERATURE_RECEIVER_CHANNEL, logger,
                                "TempSens", "external temperature sensor", false);
                    }
                } else if (event.getType() == ResourceStructureEvent.EventType.SUBRESOURCE_REMOVED
                		&& added.getName().equals(LINKED_TEMP_SENS_DECORATOR)) {
                	// since we do not know which resource the link referenced before it got deleted
                	// we need to use the low level API to find out all links for the weather receiver channel
                    Optional<HmDevice> recChan = DeviceHandlers.findDeviceChannel(
                            conn, thermos, TEMPERATURE_RECEIVER_CHANNEL, logger);
                    if (!recChan.isPresent()) {
                    	return;
                    }
                    String receiverChannelAddress = recChan.get().address().getValue();
                	for (Map<String, Object> link : conn.performGetLinks(receiverChannelAddress, 0)) {
                		if (!receiverChannelAddress.equals(link.get("RECEIVER")))
                			continue;
                		final Object sender = link.get("SENDER");
                		if (!(sender instanceof String))
                			continue;
                		conn.performRemoveLink((String) sender, receiverChannelAddress);
                		logger.info("Thermostat-temperature sensor connection removed. Thermostat channel {}, temperature sensor {}",
                				receiverChannelAddress, sender);
                	}
                }
            }
        };
        thermos.addStructureListener(l);
        if (tempSens.isActive()) {
            DeviceHandlers.linkChannels(conn, tempSens, TEMPERATURE_SENDER_CHANNEL,
                    thermos, TEMPERATURE_RECEIVER_CHANNEL, logger,
                    "TempSens", "external temperature sensor", false);
        }
    }
    
}
