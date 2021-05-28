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
import org.ogema.core.model.simple.StringResource;
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
import org.ogema.tools.resource.util.ResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

/**
 * Handler for a HomeMatic CARBON_DIOXIDE_RECEIVER channel, created as
 * {@link org.ogema.model.sensors.CO2Sensor} resource in OGEMA.
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=1"})
public class CO2Receiver extends AbstractDeviceHandler implements DeviceHandlerFactory {

    Logger logger = LoggerFactory.getLogger(getClass());
    static final String STATUS_DEC = "hmConcentrationStatus";

    enum PARAMS {

        CONCENTRATION,
        CONCENTRATION_STATUS
    }

    @Override
    public DeviceHandler createHandler(HomeMaticConnection connection) {
        return new CO2Receiver(connection);
    }

    public CO2Receiver() { //service factory constructor called by SCR
        super(null);
    }

    public CO2Receiver(HomeMaticConnection conn) {
        super(conn);
    }

    class ConcentrationEventListener implements HmEventListener {

        final org.ogema.model.sensors.CO2Sensor sens;
        final String address;

        public ConcentrationEventListener(org.ogema.model.sensors.CO2Sensor sens, String address) {
            this.sens = sens;
            this.address = address;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                if (PARAMS.CONCENTRATION.name().equals(e.getValueKey())) {
                    float ppm = e.getValueFloat();
                    sens.reading().setValue(ppm);
                } else if (PARAMS.CONCENTRATION_STATUS.name().equals(e.getValueKey())) {
                    StringResource status = sens.getSubResource(STATUS_DEC, StringResource.class);
                    status.create();
                    status.setValue(e.getValueString());
                    status.activate(false);
                } else {
                    logger.trace("unsupported / ignored event: {}", e);
                }
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        return "CARBON_DIOXIDE_RECEIVER".equalsIgnoreCase(desc.getType());
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        logger.debug("setup CARBON_DIOXIDE_RECEIVER handler for address {}", desc.getAddress());
        String swName = ResourceUtils.getValidResourceName("CARBON_DIOXIDE_RECEIVER_" + desc.getAddress());
        Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
        if (values == null) {
            logger.warn("received no VALUES parameters for device {}", desc.getAddress());
            return;
        }
        //SensorDevice sd = parent.addDecorator(swName, SensorDevice.class);
        org.ogema.model.sensors.CO2Sensor sens = parent.addDecorator(swName, org.ogema.model.sensors.CO2Sensor.class);
        sens.reading().create();
        if (values.containsKey(PARAMS.CONCENTRATION_STATUS.name())) {
            StringResource status = sens.getSubResource(STATUS_DEC, StringResource.class);
            status.create();
        }
        conn.addEventListener(new ConcentrationEventListener(sens, desc.getAddress()));
        sens.activate(true);
    }

}
