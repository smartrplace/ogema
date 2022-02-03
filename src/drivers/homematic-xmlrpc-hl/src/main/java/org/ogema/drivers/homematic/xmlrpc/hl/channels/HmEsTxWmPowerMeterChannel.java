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
import org.ogema.model.devices.connectiondevices.ElectricityConnectionBox;
import org.ogema.model.metering.ElectricityMeter;
import org.ogema.model.metering.GasMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Setup electricity metering for HM-ES-TX-WM devices.
 *
 * @author jlapp
 */
public class HmEsTxWmPowerMeterChannel extends AbstractDeviceHandler {

    Logger logger = LoggerFactory.getLogger(getClass());

    final static String LAST_READING_DECORATOR = "lastReading";

    public HmEsTxWmPowerMeterChannel(HomeMaticConnection conn) {
        super(conn);
    }

    class PowerMeterEventListener implements HmEventListener {

        final Map<String, ElectricityMeter> meters;
        final HmDevice parent;
        final DeviceDescription desc;
        volatile boolean electricityResourcesInitialized = false;
        volatile boolean gasResourcesInitialized = false;

        public PowerMeterEventListener(Map<String, ElectricityMeter> meters,
                HmDevice parent, DeviceDescription desc) {
            this.meters = meters;
            this.parent = parent;
            this.desc = desc;
        }

        private EnergyResource getLastReading(ElectricityMeter meter) {
            return meter.getSubResource(LAST_READING_DECORATOR, EnergyResource.class);
        }
        
        private GasMeter getGasMeter() {
            String base = desc.getAddress().substring(0, desc.getAddress().length() - 2);
            GasMeter meter = parent.addDecorator("gasMeter_" + base, GasMeter.class);
            meter.reading().create();
            return meter;
        }

        private void initElectricityMetering() {
            if (electricityResourcesInitialized) {
                return;
            }
            String base = desc.getAddress().substring(0, desc.getAddress().length() - 2);
            ElectricityConnectionBox ecb
                    = parent.addDecorator("electricityMetering_" + base, ElectricityConnectionBox.class);
            ecb.meters().create().activate(false);
            //Map<String, ElectricityMeter> meters = new HashMap<>();
            ElectricityMeter meterCon = ecb.meters().getSubResource("consumption", ElectricityMeter.class);
            if (!meterCon.type().isActive()) {
                meterCon.type().create();
                meterCon.type().setValue(2);
                meterCon.type().activate(false);
            }
            ElectricityMeter meterGen = ecb.meters().getSubResource("generation", ElectricityMeter.class);
            if (!meterGen.type().isActive()) {
                meterGen.type().create();
                meterGen.type().setValue(3);
                meterGen.type().activate(false);
            }
            meters.put(base + ":1", meterCon);
            meters.put(base + ":2", meterGen);
            meters.values().forEach(m -> m.activate(false));
            ecb.activate(false);
            meters.values().forEach(m -> {
                if (!m.energyReading().isActive()) {
                    m.energyReading().create();
                    m.energyReading().setUnit(PhysicalUnit.KILOWATT_HOURS);
                    m.energyReading().activate(false);
                }
            });
            logger.debug("initialized electricity meters: {}", meters);
            electricityResourcesInitialized = true;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                switch (e.getValueKey()) {
                    case "IEC_POWER": {
                        initElectricityMetering();
                        ElectricityMeter meter = meters.get(e.getAddress());
                        if (meter == null) {
                            continue;
                        }
                        PowerResource pwr = meter.powerReading();
                        if (!pwr.exists()) {
                            pwr.create();
                        }
                        pwr.setValue(e.getValueFloat());
                        pwr.activate(false);
                        logger.debug("power reading updated: {} = {}", pwr.getPath(), e.getValueFloat());
                        break;
                    }
                    case "IEC_ENERGY_COUNTER": {
                        initElectricityMetering();
                        ElectricityMeter meter = meters.get(e.getAddress());
                        if (meter == null) {
                            continue;
                        }
                        EnergyResource reading = meter.energyReading();
                        if (!reading.exists()) {
                            reading.create();
                        }
                        float readingKWh = e.getValueFloat();
                        reading.setValue(readingKWh);
                        reading.activate(false);
                        logger.debug("energy reading updated: {} = {} (device reading: {})",
                                reading.getPath(), readingKWh, e.getValueFloat());
                        break;
                    }
                    case "GAS_ENERGY_COUNTER": {
                        GasMeter meter = getGasMeter();
                        meter.reading().setValue(e.getValueFloat());
                        meter.reading().activate(false);
                        meter.activate(false);
                        logger.debug("gas reading updated: {} = {})",
                                meter.reading().getPath(), e.getValueFloat());
                        break;
                    }
                }
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        return "HM-ES-TX-WM".equals(desc.getParentType())
                && desc.getType().toUpperCase().startsWith("POWERMETER_IEC");
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc,
            Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        if (desc.getAddress().endsWith(":1")) {
            LoggerFactory.getLogger(getClass())
                    .debug("setup HM-ES-TX-WM handler for address {}", desc.getAddress());
            String base = desc.getAddress().substring(0, desc.getAddress().length() - 2);
            Map<String, ElectricityMeter> meters = new HashMap<>();
            meters.put(base + ":1", null);
            meters.put(base + ":2", null);
            conn.addEventListener(new PowerMeterEventListener(meters, parent, desc));
        }
    }

}
