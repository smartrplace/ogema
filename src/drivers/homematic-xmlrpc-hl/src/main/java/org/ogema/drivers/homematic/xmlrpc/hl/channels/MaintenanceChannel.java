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

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmMaintenance;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.model.devices.sensoractordevices.SensorDevice;
import org.ogema.model.sensors.GenericBinarySensor;
import org.ogema.model.sensors.GenericFloatSensor;
import org.ogema.tools.resource.util.ResourceUtils;

/**
 * Handler for {@code MAINTENANCE} channels on BidCos and HmIP devices.
 * See {@link PARAMS} for supported parameters.
 *
 * @author jlapp
 */
public class MaintenanceChannel extends AbstractDeviceHandler {

    Logger logger = LoggerFactory.getLogger(getClass());
    public enum PARAMS {

        CARRIER_SENSE_LEVEL, // 0..100% (HAP)
        DUTY_CYCLE, // boolean (HAP)
        DUTY_CYCLE_LEVEL, // 0..100% (HAP)
        ERROR_CODE,
        LOWBAT,
        OPERATING_VOLTAGE,
        RSSI_DEVICE,
        RSSI_PEER,
        UNREACH

    }

    public MaintenanceChannel(HomeMaticConnection conn) {
        super(conn);
    }
    
    class MaintenanceEventListener implements HmEventListener {

        final HmMaintenance mnt;
        final String address;
        final HmDevice parent;

        public MaintenanceEventListener(HmDevice parent, HmMaintenance mnt, String address) {
            this.mnt = mnt;
            this.address = address;
            this.parent = parent;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
                if (!address.equals(e.getAddress())) {
                    continue;
                }
                if (PARAMS.CARRIER_SENSE_LEVEL.name().equals(e.getValueKey())) {
                    maintenanceSensorReading(parent, PARAMS.CARRIER_SENSE_LEVEL, e);
                } else if (PARAMS.DUTY_CYCLE.name().equals(e.getValueKey())) {
                    if (!mnt.dutyCycle().isActive()) {
                        mnt.dutyCycle().reading().create().activate(false);
                        mnt.dutyCycle().activate(false);
                    }
                    mnt.dutyCycle().reading().setValue(e.getValueBoolean());
                    maintenanceSensorReading(parent, PARAMS.DUTY_CYCLE, e);
                } else if (PARAMS.DUTY_CYCLE_LEVEL.name().equals(e.getValueKey())) {
                    if (!mnt.dutyCycleLevel().isActive()) {
                        mnt.dutyCycleLevel().reading().create().activate(false);
                        mnt.dutyCycleLevel().activate(false);
                    }
                    mnt.dutyCycleLevel().reading().setValue(e.getValueFloat()/100f);
                    maintenanceSensorReading(parent, PARAMS.DUTY_CYCLE_LEVEL, e);
                } else if (PARAMS.ERROR_CODE.name().equals(e.getValueKey())) {
                    if (!mnt.errorCode().isActive()) {
                        mnt.errorCode().create().activate(false);
                    }
                    mnt.errorCode().setValue(e.getValueInt());
                } else if (PARAMS.LOWBAT.name().equals(e.getValueKey())) {
                    if (!mnt.batteryLow().isActive()) {
                        mnt.batteryLow().create().activate(false);
                    }
                    mnt.batteryLow().setValue(e.getValueBoolean());
                } else if (PARAMS.RSSI_DEVICE.name().equals(e.getValueKey())) {
                    if (!mnt.rssiDevice().isActive()) {
                        mnt.rssiDevice().create().activate(false);
                    }
                    mnt.rssiDevice().setValue(e.getValueInt());
                } else if (PARAMS.RSSI_PEER.name().equals(e.getValueKey())) {
                    if (!mnt.rssiPeer().isActive()) {
                        mnt.rssiPeer().create().activate(false);
                    }
                    mnt.rssiPeer().setValue(e.getValueInt());
                } else if (PARAMS.OPERATING_VOLTAGE.name().equals(e.getValueKey())) {
                    if (!mnt.battery().internalVoltage().reading().isActive()) {
                        mnt.battery().internalVoltage().reading().create().activate(false);
                        mnt.battery().internalVoltage().activate(false);
                        mnt.battery().activate(false);
                    }
                    mnt.battery().internalVoltage().reading().setValue(e.getValueFloat());
                } else if (PARAMS.UNREACH.name().equals(e.getValueKey())) {
                    mnt.communicationStatus().communicationDisturbed().setValue(e.getValueBoolean());
                }
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        return "MAINTENANCE".equalsIgnoreCase(desc.getType());
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        logger.debug("setup MAINTENANCE handler for address {}", desc.getAddress());
        String swName = ResourceUtils.getValidResourceName("MAINTENANCE" + desc.getAddress());
        Map<String, ParameterDescription<?>> values = paramSets.get(ParameterDescription.SET_TYPES.VALUES.name());
        if (values == null) {
            logger.warn("received no VALUES parameters for device {}", desc.getAddress());
            return;
        }
        if (values.containsKey(PARAMS.DUTY_CYCLE.name())) {
            logger.debug("adding separate sensor for maintenance channel reading {}", PARAMS.DUTY_CYCLE);
            maintenanceSensorReading(parent, PARAMS.DUTY_CYCLE, null);
        }
        if (values.containsKey(PARAMS.CARRIER_SENSE_LEVEL.name())) {
            logger.debug("adding separate sensor for maintenance channel reading {}", PARAMS.CARRIER_SENSE_LEVEL);
            maintenanceSensorReading(parent, PARAMS.CARRIER_SENSE_LEVEL, null);
        }
        if (values.containsKey(PARAMS.DUTY_CYCLE_LEVEL.name())) {
            logger.debug("adding separate sensor for maintenance channel reading {}", PARAMS.DUTY_CYCLE_LEVEL);
            maintenanceSensorReading(parent, PARAMS.DUTY_CYCLE_LEVEL, null);
        }
        HmMaintenance mnt = parent.addDecorator(swName, HmMaintenance.class);
        // create the battery field as it will be probably be linked into higher level models
        mnt.batteryLow().create();
        mnt.activate(true);
        
        mnt.communicationStatus().communicationDisturbed().create();
        try {
            mnt.communicationStatus().communicationDisturbed().setValue(conn.<Boolean>getValue(desc.getAddress(), PARAMS.UNREACH.name()));
        } catch (IOException | ClassCastException ioex) {
            logger.warn("could not read UNREACH state of device {}: {}", desc.getAddress(), ioex.getMessage());
        }
        mnt.communicationStatus().activate(true);
        
        conn.addEventListener(new MaintenanceEventListener(parent, mnt, desc.getAddress()));
    }
    
    private void maintenanceSensorReading(HmDevice parent, PARAMS param, HmEvent e) {
        SensorDevice sd = parent.getSubResource("maintenanceChannelReadings", SensorDevice.class);
        if (!sd.isActive()) {
            sd.sensors().create();
            sd.sensors().activate(false);
            sd.activate(false);
        }
        switch (param) {
            case CARRIER_SENSE_LEVEL : {
                GenericFloatSensor sens = sd.sensors().getSubResource("carrierSensLevel", GenericFloatSensor.class);
                if (!sens.isActive()) {
                    sens.reading().create();
                    sens.reading().activate(false);
                    sens.activate(false);
                }
                if (e != null) {
                    sens.reading().setValue(e.getValueFloat() / 100f);
                }
                break;
            }
            case DUTY_CYCLE : {
                GenericBinarySensor sens = sd.sensors().getSubResource("dutyCycle", GenericBinarySensor.class);
                if (!sens.isActive()) {
                    sens.reading().create();
                    sens.reading().activate(false);
                    sens.activate(false);
                }
                if (e != null) {
                    sens.reading().setValue(e.getValueBoolean());
                }
                break;
            }
            case DUTY_CYCLE_LEVEL : {
                GenericFloatSensor sens = sd.sensors().getSubResource("dutyCycleLevel", GenericFloatSensor.class);
                if (!sens.isActive()) {
                    sens.reading().create();
                    sens.reading().activate(false);
                    sens.activate(false);
                }
                if (e != null) {
                    sens.reading().setValue(e.getValueFloat() / 100f);
                }
                break;
            }
        }
    }
    
}
