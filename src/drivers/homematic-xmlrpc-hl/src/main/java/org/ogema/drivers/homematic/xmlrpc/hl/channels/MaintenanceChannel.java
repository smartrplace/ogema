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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmMaintenance;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmLogicInterface;
import org.ogema.model.devices.sensoractordevices.SensorDevice;
import org.ogema.model.sensors.GenericBinarySensor;
import org.ogema.model.sensors.GenericFloatSensor;
import org.ogema.model.sensors.Sensor;
import org.ogema.tools.resource.util.ResourceUtils;

/**
 * Handler for {@code MAINTENANCE} channels on BidCos and HmIP devices. See
 * {@link PARAMS} for supported parameters.
 *
 * @author jlapp
 */
public final class MaintenanceChannel extends AbstractDeviceHandler {

    static final long RSSI_MAX_AGE = 15 * 60 * 1000L;
	
	public static final float BATTERY_CHARGE_LOW = 0.1f;
	public static final float BATTERY_CHARGE_OK = 1.0f;

    public static enum PARAMS {

        CARRIER_SENSE_LEVEL, // 0..100% (HAP)
        CONFIG_PENDING,
        DUTY_CYCLE, // boolean (HAP)
        DUTY_CYCLE_LEVEL, // 0..100% (HAP)
        ERROR_CODE,
        LOWBAT,
        OPERATING_VOLTAGE,
        RSSI_DEVICE,
        RSSI_PEER,
        UNREACH

    }
	
	private final static Map<String, Class<? extends SingleValueResource>> PARAMETERS;

	static {
		PARAMETERS = new LinkedHashMap<>();
		PARAMETERS.put("CYCLIC_INFO_MSG_OVERDUE_THRESHOLD", IntegerResource.class);
		PARAMETERS.put("CYCLIC_INFO_MSG", IntegerResource.class);
		PARAMETERS.put("CYCLIC_INFO_MSG_DIS_UNCHANGED", IntegerResource.class);
		PARAMETERS.put("CYCLIC_INFO_MSG_DIS", IntegerResource.class);
		
		// for FALMOT and FAL230
		PARAMETERS.put("HEATING_EMERGENCY_PWM_SIGNAL", FloatResource.class);
		// FALMOT only
		PARAMETERS.put("DECALCIFICATION_TIME", IntegerResource.class);
		PARAMETERS.put("DECALCIFICATION_WEEKDAY", IntegerResource.class);
	}

    Logger logger = LoggerFactory.getLogger(getClass());
    Collection<KnownDevice> knownDevices = Collections.newSetFromMap(new ConcurrentHashMap<>());
    ScheduledExecutorService exec;

    private class KnownDevice {

        final HmMaintenance resource;
        final DeviceDescription desc;
        //final Map<String, ParameterDescription<?>> values;
        volatile long lastRead;

        public KnownDevice(HmMaintenance resource, DeviceDescription desc, Map<String, ParameterDescription<?>> values) {
            this.resource = resource;
            this.desc = desc;
            //this.values = values;
        }

        void checkRssiUpdate(long maxAge) {
            //XXX system vs framework time.
            long now = System.currentTimeMillis();
            boolean updateRequired = false;
            updateRequired
                    |= resource.rssiDevice().exists() && resource.rssiDevice().getLastUpdateTime() + maxAge < now;
            updateRequired
                    |= resource.rssiPeer().exists() && resource.rssiPeer().getLastUpdateTime() + maxAge < now;
            if (updateRequired && lastRead + maxAge < now) { // limit retries in case read returns 0
                update(resource, desc.getAddress());
                lastRead = now;
            }
        }
		
    }

    public MaintenanceChannel(HomeMaticConnection conn) {
        super(conn);
        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleWithFixedDelay(this::updateRssi, 60, 60, TimeUnit.SECONDS);
    }

	@Override
	public void close() {
		exec.shutdownNow();
	}

    void updateRssi() {
        knownDevices.forEach(d -> {
            try {
                d.checkRssiUpdate(RSSI_MAX_AGE);
            } catch (RuntimeException re) {
                logger.debug("bug!", re);
            }
        });
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
                } else if (PARAMS.CONFIG_PENDING.name().equals(e.getValueKey())) {
                    if (!mnt.configPending().isActive()) {
                        mnt.configPending().create();
                    }
                    mnt.configPending().setValue(e.getValueBoolean());
                    mnt.configPending().activate(false);
                } else if (PARAMS.DUTY_CYCLE.name().equals(e.getValueKey())) {
                    if (!mnt.dutyCycle().isActive()) {
                        mnt.dutyCycle().reading().create().activate(false);
                        mnt.dutyCycle().activate(false);
                    }
                    mnt.dutyCycle().reading().setValue(e.getValueBoolean());
                    //maintenanceSensorReading(parent, PARAMS.DUTY_CYCLE, e);
                } else if (PARAMS.DUTY_CYCLE_LEVEL.name().equals(e.getValueKey())) {
                    if (!mnt.dutyCycleLevel().isActive()) {
                        mnt.dutyCycleLevel().reading().create().activate(false);
                        mnt.dutyCycleLevel().activate(false);
                    }
                    mnt.dutyCycleLevel().reading().setValue(e.getValueFloat() / 100f);
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
					// battery resources created in setup
					mnt.battery().chargeSensor().reading()
							.setValue(e.getValueBoolean() ? BATTERY_CHARGE_LOW : BATTERY_CHARGE_OK);
					mnt.battery().chargeSensor().reading().activate(false);
                } else if (PARAMS.RSSI_DEVICE.name().equals(e.getValueKey())) {
                    if (e.getValueInt() == 0) {
                        logger.debug("cowardly refusing to store RSSI_DEVICE=0 for {}", e.getAddress());
                    } else {
                        if (!mnt.rssiDevice().isActive()) {
                            mnt.rssiDevice().create().activate(false);
                        }
                        mnt.rssiDevice().setValue(e.getValueInt());
                    }
                } else if (PARAMS.RSSI_PEER.name().equals(e.getValueKey())) {
                    if (e.getValueInt() == 0) {
                        logger.debug("cowardly refusing to store RSSI_PEER=0 for {}", e.getAddress());
                    } else {
                        if (!mnt.rssiPeer().isActive()) {
                            mnt.rssiPeer().create().activate(false);
                        }
                        mnt.rssiPeer().setValue(e.getValueInt());
                    }
                } else if (PARAMS.OPERATING_VOLTAGE.name().equals(e.getValueKey())) {
					// battery resources created in setup
                    mnt.battery().internalVoltage().reading().setValue(e.getValueFloat());
					mnt.battery().internalVoltage().reading().activate(false);
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

	private boolean isCcu(HmDevice parent) {
		String parentType = parent.type().getValue();
		return parentType != null && parentType.contains("CCU3");
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
        /*
        if (values.containsKey(PARAMS.DUTY_CYCLE.name())) {
            logger.debug("adding separate sensor for maintenance channel reading {}", PARAMS.DUTY_CYCLE);
            maintenanceSensorReading(parent, PARAMS.DUTY_CYCLE, null);
        }
         */
        if (values.containsKey(PARAMS.CARRIER_SENSE_LEVEL.name())) {
            logger.debug("adding separate sensor for maintenance channel reading {}", PARAMS.CARRIER_SENSE_LEVEL);
            Sensor sens = maintenanceSensorReading(parent, PARAMS.CARRIER_SENSE_LEVEL, null);
			if (isCcu(parent)) {
				HmLogicInterface ccu = ResourceUtils.getFirstParentOfType(parent, HmLogicInterface.class);
				ccu.interfaceInfo().addDecorator("carrierSenseLevelMnt", sens);
			}
        }
        if (values.containsKey(PARAMS.DUTY_CYCLE_LEVEL.name())) {
            logger.debug("adding separate sensor for maintenance channel reading {}", PARAMS.DUTY_CYCLE_LEVEL);
            Sensor sens = maintenanceSensorReading(parent, PARAMS.DUTY_CYCLE_LEVEL, null);
			if (isCcu(parent)) {
				HmLogicInterface ccu = ResourceUtils.getFirstParentOfType(parent, HmLogicInterface.class);
				ccu.interfaceInfo().addDecorator("dutyCycleLevelMnt", sens);
			}
        }
        HmMaintenance mnt = parent.addDecorator(swName, HmMaintenance.class).create();
		mnt.activate(true);
		if (values.containsKey(PARAMS.LOWBAT.name())) {
			// create the battery field as it will be probably be linked into higher level models
		    mnt.batteryLow().create();
		}
        mnt.communicationStatus().communicationDisturbed().create();
		Object unreachValue = null;
        try {
			unreachValue = conn.getValue(desc.getAddress(), PARAMS.UNREACH.name());
            mnt.communicationStatus().communicationDisturbed().setValue((Boolean) unreachValue);
        } catch (IOException | ClassCastException ioex) {
            logger.warn("could not read UNREACH state of device {}: {} ({})", desc.getAddress(), ioex.getMessage(), unreachValue);
        }
        mnt.communicationStatus().activate(true);

        if (values.containsKey(PARAMS.RSSI_DEVICE.name())) {
            mnt.rssiDevice().create();
        }
        if (values.containsKey(PARAMS.RSSI_PEER.name())) {
            mnt.rssiPeer().create();
        }
		//boolean hasBattery = false;
		if (values.containsKey(PARAMS.OPERATING_VOLTAGE.name())) {
			mnt.battery().internalVoltage().reading().create();
			mnt.battery().internalVoltage().activate(false);
			mnt.battery().activate(false);
			//hasBattery = true;
		}
		if (values.containsKey(PARAMS.LOWBAT.name())) {
			mnt.battery().chargeSensor().reading().create();
			mnt.battery().chargeSensor().activate(false);
			mnt.battery().activate(false);
			//hasBattery = true;
		}
		/*
		if (!hasBattery && mnt.battery().exists() && !mnt.battery().isReference(true)) {
			mnt.battery().delete();
		}
        */
        Map<String, ParameterDescription<?>> master =
                paramSets.get(ParameterDescription.SET_TYPES.MASTER.name());
        if (master != null) {
            if (master.containsKey("GLOBAL_BUTTON_LOCK")) {
                BooleanResource globalButtonLock =
                        mnt.addDecorator("globalButtonLock", BooleanResource.class);
                globalButtonLock.create().activate(false);
                globalButtonLock.addValueListener((BooleanResource br) -> {
                    boolean val = br.getValue();
                    logger.debug("setting GLOBAL_BUTTON_LOCK on {} to {}",
                            desc.getAddress(), val);
                    conn.performPutParamset(desc.getAddress(),"MASTER",
                            Collections.singletonMap("GLOBAL_BUTTON_LOCK", val));
                }, true);
            }
        }
		
		ChannelUtils.setupParameterResources(parent, desc, paramSets,
				ParameterDescription.SET_TYPES.MASTER, PARAMETERS, conn, mnt, logger);
        
        conn.addEventListener(new MaintenanceEventListener(parent, mnt, desc.getAddress()));
        knownDevices.add(new KnownDevice(mnt, desc, values));
        update(mnt, desc.getAddress());
    }
	
    private Sensor maintenanceSensorReading(HmDevice parent, PARAMS param, HmEvent e) {
        SensorDevice sd = parent.getSubResource("maintenanceChannelReadings", SensorDevice.class);
        if (!sd.isActive()) {
            sd.sensors().create();
            sd.sensors().activate(false);
            sd.activate(false);
        }
		Sensor rval = null;
        switch (param) {
            case CARRIER_SENSE_LEVEL: {
                GenericFloatSensor sens = sd.sensors().getSubResource("carrierSensLevel", GenericFloatSensor.class);
                if (!sens.isActive()) {
                    sens.reading().create();
                    sens.reading().activate(false);
                    sens.activate(false);
                }
                if (e != null) {
                    sens.reading().setValue(e.getValueFloat() / 100f);
                }
				rval = sens;
                break;
            }
            case DUTY_CYCLE: {
                GenericBinarySensor sens = sd.sensors().getSubResource("dutyCycle", GenericBinarySensor.class);
                if (!sens.isActive()) {
                    sens.reading().create();
                    sens.reading().activate(false);
                    sens.activate(false);
                }
                if (e != null) {
                    sens.reading().setValue(e.getValueBoolean());
                }
				rval = sens;
                break;
            }
            case DUTY_CYCLE_LEVEL: {
                GenericFloatSensor sens = sd.sensors().getSubResource("dutyCycleLevel", GenericFloatSensor.class);
                if (!sens.isActive()) {
                    sens.reading().create();
                    sens.reading().activate(false);
                    sens.activate(false);
                }
                if (e != null) {
                    sens.reading().setValue(e.getValueFloat() / 100f);
                }
				rval = sens;
                break;
            }
        }
		return rval;
    }

    @Override
    public boolean update(HmDevice device) {
        HmDevice top = conn.getToplevelDevice(device);
        return findChannels(top, "MAINTENANCE").findAny()
                .map(d -> update(top, d.address().getValue())).orElse(Boolean.FALSE);
    }

    private boolean update(HmDevice toplevel, String channelAddress) {
        List<HmMaintenance> mnt = toplevel.getSubResources(HmMaintenance.class, false);
        if (!mnt.isEmpty()) {
            logger.debug("trying to update RSSI for {}", mnt.get(0));
            return update(mnt.get(0), channelAddress);
        }
        return false;
    }
	
	boolean isCyclicInfoDisabled(HmMaintenance resource) {
		try {
			Resource hmParametersMaster = resource.getSubResource("HmParametersMaster");
			if (hmParametersMaster == null) {
				return false;
			}
			IntegerResource cyclicMsg = hmParametersMaster.getSubResource("CYCLIC_INFO_MSG");
			IntegerResource cyclicMsgFb = hmParametersMaster.getSubResource("CYCLIC_INFO_MSG_FEEDBACK");
			return (cyclicMsg != null && cyclicMsg.isActive() && cyclicMsg.getValue() == 0)
					|| (cyclicMsgFb != null && cyclicMsgFb.isActive() && cyclicMsgFb.getValue() == 0);
		} catch (RuntimeException re) {
			logger.debug("bug!", re);
			return false;
		}
	}

    private boolean update(HmMaintenance m, String channelAddress) {
		if (isCyclicInfoDisabled(m)) {
			logger.trace("not updating RSSI on {}: cyclic updates are disabled.", m.getPath());
			return false;
		}
        boolean updated = false;
        try {
            if (m.rssiDevice().exists()) { // available resources should be created in setup()
                logger.trace("reading RSSI_DEVICE for {} / {}", m, channelAddress);
                Object rssiVal = conn.getValue(channelAddress, PARAMS.RSSI_DEVICE.name());
                if (!(rssiVal instanceof Number)) {
                    //XXX the CCU/Homematic receiver channel will just return crap here...
                    logger.warn("unexpected return value for {} on {}: {}", PARAMS.RSSI_DEVICE, channelAddress, rssiVal);
                } else {
                    int rssiDevice = ((Number) rssiVal).intValue();
                    if (rssiDevice == 0) {
                        logger.debug("read returned RSSI_DEVICE=0 for {}", channelAddress);
                    } else {
                        m.rssiDevice().setValue(rssiDevice);
                        m.rssiDevice().activate(false);
                        updated = true;
                    }
                }
            }
            if (m.rssiPeer().exists()) {
                logger.trace("reading RSSI_PEER for {} / {}", m, channelAddress);
                Object rssiVal = conn.getValue(channelAddress, PARAMS.RSSI_PEER.name());
                if (!(rssiVal instanceof Number)) {
                    //XXX the CCU/Homematic receiver channel will just return crap here...
                    logger.warn("unexpected return value for {} on {}: {}", PARAMS.RSSI_PEER, channelAddress, rssiVal);
                } else {
                    int rssiPeer = ((Number) rssiVal).intValue();
                    if (rssiPeer == 0) {
                        logger.debug("read returned RSSI_PEER=0 for {}", channelAddress);
                    } else {
                        m.rssiPeer().setValue(rssiPeer);
                        m.rssiPeer().activate(false);
                        updated = true;
                    }
                }
            }
            return updated;
        } catch (IOException ex) {
            String msg = ex.getMessage();
            if (msg.startsWith("Unknown Parameter value")) {
                // happens for RSSI_DEVICE on a HM_HmIP_SWDM even though it's listed as supported parameter
                logger.debug("read mysteriously failed for {}: {}", m, msg);
            } else {
                logger.warn("read failed for {}: {} ({})", m, ex.getMessage(), ex.getClass().getSimpleName());
            }
        }
        return updated;
    }

    static Stream<HmDevice> findChannels(HmDevice toplevelDevice, String type) {
        return toplevelDevice.channels().getAllElements().stream()
                .filter(d -> type.equalsIgnoreCase(d.type().getValue()));
    }

}
