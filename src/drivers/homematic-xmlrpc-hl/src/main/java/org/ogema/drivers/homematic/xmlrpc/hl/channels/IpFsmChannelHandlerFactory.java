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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import org.osgi.service.component.annotations.Component;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.actors.OnOffSwitch;
import org.ogema.model.sensors.GenericBinarySensor;
import org.ogema.tools.resource.util.ResourceUtils;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up resources for HmIP-FSM devices. The ChannelHandler accepts both
 * SWITCH_TRANSMITTER and SWITCH_VIRTUAL_RECEIVER channels and creates a single
 * {@link OnOffSwitch} that will control the relais. The state of the
 * SWITCH_VIRTUAL_RECEIVERs is made available in {@link GenericBinarySensor}s.
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=1"})
public class IpFsmChannelHandlerFactory implements DeviceHandlerFactory {

    static class FsmChannels extends AbstractDeviceHandler {

        Logger logger = LoggerFactory.getLogger(getClass());
        Map<String, Map<String, GenericBinarySensor>> fsmVirtualReceivers = new ConcurrentHashMap<>();

        private static final String SWITCH_VIRTUAL_RECEIVER_TYPE = "SWITCH_VIRTUAL_RECEIVER";
        private static final String SWITCH_TRANSMITTER_TYPE = "SWITCH_TRANSMITTER";

        public FsmChannels(HomeMaticConnection conn) {
            super(conn);
        }

        class StateEventListener implements HmEventListener {

            final BooleanResource state;
            final String address;

            public StateEventListener(BooleanResource state, String address) {
                this.state = state;
                this.address = address;
            }

            @Override
            public void event(List<HmEvent> events) {
                for (HmEvent e : events) {
                    if (!address.equals(e.getAddress())) {
                        continue;
                    }
                    switch (e.getValueKey()) {
                        case "STATE":
                            state.setValue(e.getValueBoolean());
                            break;
                    }
                }
            }

        }

        @Override
        public boolean accept(DeviceDescription desc) {
            return "HmIP-FSM".equalsIgnoreCase(desc.getParentType())
                    && (SWITCH_TRANSMITTER_TYPE.equalsIgnoreCase(desc.getType())
                    || SWITCH_VIRTUAL_RECEIVER_TYPE.equalsIgnoreCase(desc.getType()));
        }

        @Override
        public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
            switch (desc.getType()) {
                case SWITCH_TRANSMITTER_TYPE:
                    setupSwitchTransmitter(parent, desc, paramSets);
                    break;
                case SWITCH_VIRTUAL_RECEIVER_TYPE:
                    setupSwitchVirtualReceiver(parent, desc, paramSets);
                    break;
            }
        }

        private String baseAddress(String address) {
            String base = address.split(":")[0];
            logger.trace("base for {}: {}", address, base);
            return base;
        }

        private void setupSwitchTransmitter(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
            logger.debug("setup SWITCH handler for address {}", desc.getAddress());
            String swName = ResourceUtils.getValidResourceName("SWITCH_" + desc.getAddress());
            String base = baseAddress(desc.getAddress());
            OnOffSwitch sw = parent.addDecorator(swName, OnOffSwitch.class);
            sw.stateControl().create();
            sw.stateFeedback().create();
            sw.stateControl().create();
            logger.debug("adding STATE listener to {}", sw.stateControl().getPath());
            sw.stateControl().addValueListener((BooleanResource br) -> {
                boolean isOn = br.getValue();
                logger.trace("FSM virtual receivers: {}", fsmVirtualReceivers);
                logger.trace("FSM virtual receivers for {}: {}", base, fsmVirtualReceivers.get(base));
                if (isOn) {
                    //turn on the first virtual receiver channel
                    fsmVirtualReceivers.getOrDefault(base, Collections.emptyMap()).entrySet().stream().findFirst()
                            .ifPresent(e -> {
                                logger.debug("{} STATE:={}", e.getKey(), true);
                                conn.performSetValue(e.getKey(), "STATE", true);
                            });
                } else {
                    //turn off all virtual receiver channels with state=true
                    fsmVirtualReceivers.getOrDefault(base, Collections.emptyMap())
                            .forEach((addr, sens) -> {
                                if (sens.reading().getValue() || !sens.reading().isActive()) {
                                    logger.debug("{} STATE:={}", addr, false);
                                    conn.performSetValue(addr, "STATE", false);
                                }
                            });
                }
            }, true);
            conn.addEventListener(new StateEventListener(sw.stateFeedback(), desc.getAddress()));
            BooleanResource ledDisable = sw.getSubResource("LED_DISABLE_CHANNELSTATE", BooleanResource.class);
            ledDisable.create().activate(false);
            ledDisable.addValueListener((BooleanResource br) -> {
                logger.debug("setting LED_DISABLE_CHANNELSTATE on {} to {}", desc.getAddress(), br.getValue());
                conn.performPutParamset(desc.getAddress(), "MASTER",
                        Collections.singletonMap("LED_DISABLE_CHANNELSTATE", br.getValue()));
            }, true);
            sw.stateControl().activate(false);
            sw.stateFeedback().activate(false);
            sw.activate(false);
        }

        private void setupSwitchVirtualReceiver(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
            logger.debug("setup SWITCH_VIRTUAL_RECEIVER handler for address {}", desc.getAddress());
            String sensName = ResourceUtils.getValidResourceName("VIRTUAL_SWITCH_FEEDBACK_" + desc.getAddress());
            String base = baseAddress(desc.getAddress());
            GenericBinarySensor sens = parent.addDecorator(sensName, GenericBinarySensor.class);
            sens.reading().create();
            sens.reading().activate(false);
            sens.activate(false);
            conn.addEventListener(new StateEventListener(sens.reading(), desc.getAddress()));
            fsmVirtualReceivers.computeIfAbsent(base, _s -> new ConcurrentSkipListMap<>())
                    .put(desc.getAddress(), sens);
            logger.trace("FSM virtual receivers: {}", fsmVirtualReceivers);
        }

    }

    @Override
    public DeviceHandler createHandler(HomeMaticConnection connection) {
        return new FsmChannels(connection);
    }

}
