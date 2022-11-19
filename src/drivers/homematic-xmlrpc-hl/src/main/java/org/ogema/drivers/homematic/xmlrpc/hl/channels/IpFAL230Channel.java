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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ogema.core.model.Resource;

import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.resourcemanager.ResourceStructureEvent;
import org.ogema.core.resourcemanager.ResourceStructureListener;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import static org.ogema.drivers.homematic.xmlrpc.hl.channels.DeviceHandlers.findDeviceChannel;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.devices.buildingtechnology.Thermostat;
import org.ogema.model.devices.connectiondevices.ThermalValve;
import org.ogema.model.devices.sensoractordevices.SensorDevice;
import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=5"})
public class IpFAL230Channel extends AbstractDeviceHandler implements DeviceHandlerFactory {
	
	public static final String LINKED_THERMOSTAT_DECORATOR = "linkedThermostat";
    
    Logger logger = LoggerFactory.getLogger(getClass());
    
    @Override
    public DeviceHandler createHandler(HomeMaticConnection connection) {
        return new IpFAL230Channel(connection);
    }

    public IpFAL230Channel() {
        super(null);
    }

    public IpFAL230Channel(HomeMaticConnection conn) {
        super(conn);
    }
    
    enum PARAMS {
		STATE() {
			@Override
			public void setEventValue(HmEvent e, SingleValueResource res) {
				// FAL230 provides only false / true values for the valve state
				((FloatResource) res).setValue(e.getValueBoolean() ? 1.0f : 0.0f);
			}
		},
		LEVEL() {
			@Override
			public void setEventValue(HmEvent e, SingleValueResource res) {
				// FALMOT provides valve state as 0..100%
				((FloatResource) res).setValue(e.getValueFloat() / 100f);
			}
		};

        public void setEventValue(HmEvent e, SingleValueResource res) {
            ValueResourceUtils.setValue(res, e.getValueString());
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
                try {
                    PARAMS p = PARAMS.valueOf(e.getValueKey());
					p.setEventValue(e, res);
                    logger.debug("resource updated: {} = {}", res.getPath(), e.getValue());
                } catch (IllegalArgumentException ex) {
                    //this block intentionally left blank
                }
            }
        }

    }

	/*
	accept FLOOR & FLOOR_PUMP_TRANCEIVERs, but FLOOR_PUMP_TRANCEIVER will be treated same as other channels
	 */
	@Override
	public boolean accept(DeviceDescription desc) {
		//System.out.println("parent type = " + desc.getParentType());
		return (("HmIP-FAL230-C10".equalsIgnoreCase(desc.getParentType()) || "HmIP-FAL230-C6".equalsIgnoreCase(desc.getParentType()))
				&& ("CLIMATECONTROL_FLOOR_TRANSCEIVER".equalsIgnoreCase(desc.getType())
				|| "CLIMATECONTROL_FLOOR_PUMP_TRANSCEIVER".equalsIgnoreCase(desc.getType())))
				|| ("HmIP-FALMOT-C12".equalsIgnoreCase(desc.getParentType()) && "CLIMATECONTROL_FLOOR_TRANSCEIVER".equalsIgnoreCase(desc.getType()));
	}

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        final String deviceAddress = desc.getAddress();
        logger.debug("setup FAL230-VALVE handler for address {} type {}", desc.getAddress(), desc.getType());
        String swName = ResourceUtils.getValidResourceName("VALVE" + desc.getAddress());
        Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
        if (values == null) {
            logger.warn("received no VALUES parameters for device {}", desc.getAddress());
            return;
        }

        SensorDevice actDev = parent.getSubResource("valves", SensorDevice.class);
        if(!actDev.isActive()) {
        	actDev.create();
        	actDev.activate(false);
        }
        ThermalValve valve = actDev.addDecorator(swName, ThermalValve.class);
        conn.registerControlledResource(conn.getChannel(parent, deviceAddress), valve);
        Map<String, SingleValueResource> resources = new HashMap<>();
        for (Map.Entry<String, ParameterDescription<?>> e : values.entrySet()) {
            switch (e.getKey()) {
				case "LEVEL":
                case "STATE": {
                    FloatResource reading = valve.setting().stateFeedback();
                    if (!reading.exists()) {
                        reading.create();
                        valve.activate(true);
                    }
                    logger.debug("found supported valve parameter {} on {}", e.getKey(), desc.getAddress());
                    resources.put(e.getKey(), reading);
                    break;
                }
             }
        }
        
        valve.activate(true);
        
        conn.addEventListener(new WeatherEventListener(resources, desc.getAddress()));
        ThermostatUtils.setupParameterResources(parent, desc, paramSets, conn, valve, logger);
        setupThermostatLinking(valve, deviceAddress, conn, logger);
    }
    
	static void setupThermostatLinking(final ThermalValve valve, final String falmotChannel, HomeMaticConnection conn, Logger logger) {
		String TEMPERATURE_SENDER_CHANNEL = "CLIMATECONTROL_FLOOR_TRANSMITTER"; //wall thermostat
		//String TEMPERATURE_RECEIVER_CHANNEL = "CLIMATECONTROL_FLOOR_TRANSCEIVER"; //falmot
		
        Thermostat tempSens = valve.getSubResource(LINKED_THERMOSTAT_DECORATOR, Thermostat.class);
        
        ResourceStructureListener l = new ResourceStructureListener() {

            @Override
            public void resourceStructureChanged(ResourceStructureEvent event) {
                Resource added = event.getChangedResource();
                if (event.getType() == ResourceStructureEvent.EventType.SUBRESOURCE_ADDED) {
                    if (added.getName().equals(LINKED_THERMOSTAT_DECORATOR) && added instanceof Thermostat) {
						Optional<HmDevice> senderChannel = findDeviceChannel(conn, added, TEMPERATURE_SENDER_CHANNEL, logger);
						senderChannel.ifPresent(sender -> {
							String senderAddress = sender.address().getValue();
							conn.performAddLink(senderAddress, falmotChannel, "Valve-Thermostat-Link", "Link wall thermostat - floor heating");
						});
                    }
                } else if (event.getType() == ResourceStructureEvent.EventType.SUBRESOURCE_REMOVED
                		&& added.getName().equals(LINKED_THERMOSTAT_DECORATOR)) {
                	// since we do not know which resource the link referenced before it got deleted
                	// we need to use the low level API to find out all links for the weather receiver channel
                    String receiverChannelAddress = falmotChannel;
                	for (Map<String, Object> link : conn.performGetLinks(receiverChannelAddress, 0)) {
                		if (!receiverChannelAddress.equals(link.get("RECEIVER")))
                			continue;
                		final Object sender = link.get("SENDER");
                		if (!(sender instanceof String))
                			continue;
                		conn.performRemoveLink((String) sender, receiverChannelAddress);
                		logger.info("Valve thermostat connection removed. Valve {}, thermostat {}",
                				receiverChannelAddress, sender);
                	}
                }
            }
        };
		valve.addStructureListener(l);
		if (tempSens.isActive()) {
			Optional<HmDevice> senderChannel = findDeviceChannel(conn, tempSens, TEMPERATURE_SENDER_CHANNEL, logger);
			senderChannel.ifPresent(sender -> {
				String senderAddress = sender.address().getValue();
				conn.performAddLink(senderAddress, falmotChannel, "Valve-Thermostat-Link", "Link wall thermostat - floor heating");
			});
		}
    }
	
}
