/*
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.resourcemanager.ResourceValueListener;
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
import org.ogema.model.devices.buildingtechnology.ElectricDimmer;
import org.ogema.tools.resource.util.ResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

/**
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=1"})
public class IpWuaDimmerChannel extends AbstractDeviceHandler implements DeviceHandlerFactory {
	
	private final static Map<String, Class<? extends SingleValueResource>> PARAMETERS;

	static {
		PARAMETERS = new LinkedHashMap<>();
		PARAMETERS.put("VOLTAGE_100", FloatResource.class);
		PARAMETERS.put("VOLTAGE_0", FloatResource.class);
	}

    Logger logger = LoggerFactory.getLogger(getClass());
	Map<Resource, List<ResourceValueListener<?>>> listeners = new ConcurrentHashMap<>();
    
    public IpWuaDimmerChannel() {
        super(null);
    }
    
    public IpWuaDimmerChannel(HomeMaticConnection conn) {
        super(conn);
    }
    
    enum PARAMS {

        LEVEL

    }

    class DimmerListener implements HmEventListener {

        final ElectricDimmer dimmer;
        final String address;

        public DimmerListener(ElectricDimmer sens, String address) {
            this.dimmer = sens;
            this.address = address;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                if (PARAMS.LEVEL.name().equals(e.getValueKey())) {
                    dimmer.setting().stateFeedback().setValue(e.getValueFloat());
                    logger.debug("DIMMER_TRANSMITTER {} = {}", address, e.getValueFloat());
                }
            }
        }

    }

    @Override
    public DeviceHandler createHandler(HomeMaticConnection connection) {
        return new IpWuaDimmerChannel(connection);
    }

    @Override
    public boolean accept(DeviceDescription desc) {
		return "DIMMER_TRANSMITTER".equalsIgnoreCase(desc.getType()) && "HmIP-WUA".equalsIgnoreCase(desc.getParentType());
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        logger.debug("setup HmIP DIMMER_TRANSMITTER handler for address {}", desc.getAddress());
        String swName = ResourceUtils.getValidResourceName("DIMMER_TRANSMITTER" + desc.getAddress());
        Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
        if (values == null) {
            logger.warn("received no VALUES parameters for device {}", desc.getAddress());
            return;
        }
        ElectricDimmer dimmer = parent.addDecorator(swName, ElectricDimmer.class);
        dimmer.setting().stateControl().create();
		dimmer.setting().stateFeedback().create();
		dimmer.setting().stateControl().activate(false);
		dimmer.setting().stateFeedback().activate(false);
		dimmer.setting().activate(false);
        dimmer.activate(false);
		//only the DIMMER_VIRTUAL_RECEIVER channels actually accept values
		String controlAddress = desc.getAddress().replace(":1", ":2");
		ResourceValueListener<FloatResource> l = r -> {
			logger.debug("setting level on {} / {} = {}", dimmer.getPath(), controlAddress, r.getValue());
			conn.performSetValue(controlAddress, PARAMS.LEVEL.name(), Double.valueOf(r.getValue()));
		};
		listeners.computeIfAbsent(dimmer.setting().stateControl(), _r -> new ArrayList<>()).add(l);
		dimmer.setting().stateControl().addValueListener(l, true);
        conn.addEventListener(new DimmerListener(dimmer, desc.getAddress()));
		ThermostatUtils.setupParameterResources(parent, desc, paramSets, PARAMETERS, conn, dimmer, false, logger);
		conn.registerControlledResource(parent, dimmer);
		/*
        try {
            Double v = conn.getValue(desc.getAddress(), PARAMS.LEVEL.name());
            dimmer.setting().stateFeedback().setValue(v.floatValue());
            logger.debug("dimmer level on start: {} = {}", desc.getAddress(), v);
        } catch (IOException | ClassCastException ex) {
            logger.warn("could not get initial value reading for dimmer {}: {}", desc.getAddress(), ex.getMessage());
			logger.debug("could not get initial value reading for dimmer {}", desc.getAddress(), ex);
        }
		*/
    }

	@Override
	public void close() {
		listeners.forEach((r, l) -> l.forEach(r::removeValueListener));
	}

}
