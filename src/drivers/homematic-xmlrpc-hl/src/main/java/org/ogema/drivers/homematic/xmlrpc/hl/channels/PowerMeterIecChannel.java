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

import java.util.List;
import java.util.Map;

import org.ogema.core.model.units.EnergyResource;
import org.ogema.core.model.units.PhysicalUnit;
import org.ogema.core.model.units.PowerResource;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.connections.ElectricityConnection;
import org.ogema.tools.resource.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
public class PowerMeterIecChannel extends AbstractDeviceHandler {

    Logger logger = LoggerFactory.getLogger(getClass());
    
    final static String LAST_READING_DECORATOR = "lastReading";

    public PowerMeterIecChannel(HomeMaticConnection conn) {
        super(conn);
    }
    
    class PowerMeterEventListener implements HmEventListener {

        final ElectricityConnection elconn;
        final String address;
        final EnergyResource lastReading;

        public PowerMeterEventListener(ElectricityConnection elconn, String address) {
            this.elconn = elconn;
            this.address = address;
            this.lastReading = elconn.energySensor().getSubResource(LAST_READING_DECORATOR, EnergyResource.class);
            if (!elconn.energySensor().reading().isActive()) {
                elconn.energySensor().reading().create();
                elconn.energySensor().reading().setUnit(PhysicalUnit.KILOWATT_HOURS);
                elconn.energySensor().reading().activate(false);
            }
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                switch (e.getValueKey()) {
                    case "POWER":
                        PowerResource pwr = elconn.powerSensor().reading();
                        if (!pwr.exists()) {
                            pwr.create();
                            elconn.powerSensor().activate(true);
                        }
                        pwr.setValue(e.getValueFloat());
                        logger.debug("power reading updated: {} = {}", pwr.getPath(), e.getValueFloat());
                        break;
                    case "ENERGY_COUNTER": {
                        EnergyResource reading = elconn.energySensor().reading();
                        if (!reading.exists()) {
                            reading.create();
                            elconn.energySensor().activate(true);
                        }
                        float readingKWh = e.getValueFloat() / 1000;
                        if (readingKWh < reading.getValue()) {
                            //overflow, battery changed / whatever
                            lastReading.create();
                            lastReading.setValue(reading.getValue());
                            lastReading.activate(false);
                        }
                        if (lastReading.isActive()) {
                            readingKWh += lastReading.getValue();
                        }
                        reading.setValue(readingKWh);
                        logger.debug("energy reading updated: {} = {} (device reading: {})",
                                reading.getPath(), readingKWh, e.getValueFloat());
                        break;
                    }
                }
            }
        }

    }

    @Override
    /** Note: The main detection is performed in {@link PMSwitchDevice}. Here
     * you should only enter the sub channel relevant for the power meter
     */
    public boolean accept(DeviceDescription desc) {
        return "POWERMETER_IEC1".equalsIgnoreCase(desc.getType()); //XXX POWERMETER_IEC2?
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        LoggerFactory.getLogger(getClass()).debug("setup POWERMETER_IEC1 handler for address {}", desc.getAddress());
        String swName = ResourceUtils.getValidResourceName("POWERMETER_IEC1_" + desc.getAddress());
        ElectricityConnection elconn = parent.addDecorator(swName, ElectricityConnection.class);
        conn.addEventListener(new PowerMeterEventListener(elconn, desc.getAddress()));
        elconn.create();
        elconn.activate(true);
    }

}
