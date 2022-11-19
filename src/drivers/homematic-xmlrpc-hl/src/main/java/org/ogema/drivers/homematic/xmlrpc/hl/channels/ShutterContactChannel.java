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

import java.io.IOException;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.sensors.DoorWindowSensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.tools.resource.util.ResourceUtils;

/**
 *
 * @author jlapp
 */
public class ShutterContactChannel extends AbstractDeviceHandler {

    Logger logger = LoggerFactory.getLogger(getClass());
    private static final float LOWBATVALUE = 0.1f;

    public ShutterContactChannel(HomeMaticConnection conn) {
        super(conn);
    }
    
    enum PARAMS {

        STATE,
        LOWBAT

    }

    class ShutterContactListener implements HmEventListener {

        final DoorWindowSensor sens;
        final String address;

        public ShutterContactListener(DoorWindowSensor sens, String address) {
            this.sens = sens;
            this.address = address;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                if (PARAMS.STATE.name().equals(e.getValueKey())) {
                    sens.reading().setValue(e.getValueBoolean());
                    logger.debug("SHUTTER_CONTACT {} = {}", address, e.getValueBoolean());
                } else if (PARAMS.LOWBAT.name().equals(e.getValueKey())) {
                    sens.battery().chargeSensor().reading().setValue(e.getValueBoolean() ? LOWBATVALUE : 1.0f);
                }
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        return accept(desc.getType());
    }
    
    private boolean accept(String channelType) {
        return "SHUTTER_CONTACT".equalsIgnoreCase(channelType);
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        logger.debug("setup SHUTTER_CONTACT handler for address {}", desc.getAddress());
        String swName = ResourceUtils.getValidResourceName("SHUTTER_CONTACT" + desc.getAddress());
        Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
        if (values == null) {
            logger.warn("received no VALUES parameters for device {}", desc.getAddress());
            return;
        }
        DoorWindowSensor sens = parent.addDecorator(swName, DoorWindowSensor.class);
        sens.reading().create();
        sens.activate(true);
        sens.battery().chargeSensor().reading().create();
        sens.activate(true);
		conn.registerControlledResource(parent, sens);
        conn.addEventListener(new ShutterContactListener(sens, desc.getAddress()));
    }

    @Override
    public boolean update(HmDevice device) {
        HmDevice top = conn.getToplevelDevice(device);
        return findChannels(top, "SHUTTER_CONTACT").findAny()
                .map(d -> update(top, d.address().getValue())).orElse(Boolean.FALSE);
    }
    
    private boolean update(HmDevice toplevel, String channeAddress) {
        List<DoorWindowSensor> sens = toplevel.getSubResources(DoorWindowSensor.class, false);
        if (!sens.isEmpty()) {
            logger.debug("trying to update {}", sens.get(0));
            DoorWindowSensor s = sens.get(0);
            try {
                boolean state = conn.getValue(channeAddress, "STATE");
                s.reading().setValue(state);
                return true;
            } catch (IOException ex) {
                logger.warn("update failed for {}", s, ex);
            }
        }
        return false;
    }
    
    static Stream<HmDevice> findChannels(HmDevice toplevelDevice, String type) {
        return toplevelDevice.channels().getAllElements().stream()
                .filter(d -> type.equalsIgnoreCase(d.type().getValue()));
    }
    
}
