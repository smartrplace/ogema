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
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.resourcemanager.ResourceAlreadyExistsException;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.model.sensors.SmokeDetector;
import org.ogema.tools.resource.util.ResourceUtils;

/**
 *
 * @author jlapp
 */
public class SmokeDetectorChannel extends AbstractDeviceHandler {

    Logger logger = LoggerFactory.getLogger(getClass());
    private static final float LOWBATVALUE = 0.1f;
    
    public static final String ERROR_DECORATOR = "error";

    public SmokeDetectorChannel(HomeMaticConnection conn) {
        super(conn);
    }
    
    enum PARAMS {

        STATE,
        LOWBAT,
        ERROR_SMOKE_CHAMBER

    }

    class SmokeDetectorListener implements HmEventListener {

        final SmokeDetector sens;
        final String address;

        public SmokeDetectorListener(SmokeDetector sens, String address) {
            this.sens = sens;
            this.address = address;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                logger.trace("received: {}", e);
                if (PARAMS.STATE.name().equals(e.getValueKey())) {
                    sens.reading().setValue(e.getValueBoolean());
                    logger.debug("SMOKE_DETECTOR {} = {}", address, e.getValueBoolean());
                } else if (PARAMS.LOWBAT.name().equals(e.getValueKey())) {
                    sens.battery().chargeSensor().reading().setValue(e.getValueBoolean() ? LOWBATVALUE : 1.0f);
                } else if (PARAMS.ERROR_SMOKE_CHAMBER.name().equals(e.getValueKey())) {
                    try {
                        BooleanResource error = sens.addDecorator(ERROR_DECORATOR, BooleanResource.class).create();
                        error.setValue(e.getValueBoolean());
                        error.activate(false);
                    } catch (ResourceAlreadyExistsException ex) {
                        logger.warn("cannot store error value for {}", sens);
                    }
                }
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        return accept(desc.getType());
    }
    
    private boolean accept(String channelType) {
        return "SMOKE_DETECTOR".equalsIgnoreCase(channelType);
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        logger.debug("setup SMOKE_DETECTOR handler for address {}", desc.getAddress());
        String swName = ResourceUtils.getValidResourceName("SMOKE_DETECTOR" + desc.getAddress());
        SmokeDetector sens = parent.addDecorator(swName, SmokeDetector.class);
        sens.reading().create();
        sens.activate(true);
        sens.battery().chargeSensor().reading().create();
        sens.activate(true);
        conn.addEventListener(new SmokeDetectorListener(sens, desc.getAddress()));
        update(parent);
    }

    @Override
    public boolean update(HmDevice device) {
        HmDevice top = conn.getToplevelDevice(device);
        return findChannels(top, "SMOKE_DETECTOR").findAny()
                .map(d -> update(top, d.address().getValue())).orElse(Boolean.FALSE);
    }
    
    private boolean update(HmDevice toplevel, String channelAddress) {
        List<SmokeDetector> sens = toplevel.getSubResources(SmokeDetector.class, false);
        if (!sens.isEmpty()) {
            logger.debug("trying to update {}", sens.get(0));
            SmokeDetector s = sens.get(0);
            try {
                boolean state = conn.getValue(channelAddress, PARAMS.STATE.name());
                s.reading().setValue(state);
                boolean lowbat = conn.getValue(channelAddress, PARAMS.LOWBAT.name());
                s.battery().chargeSensor().reading().setValue(lowbat ? LOWBATVALUE : 1.0f);
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
