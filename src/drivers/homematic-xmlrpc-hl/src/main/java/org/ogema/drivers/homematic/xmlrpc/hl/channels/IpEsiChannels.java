package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import java.util.List;
import java.util.Map;
import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.FloatResource;

import org.ogema.core.model.units.ElectricCurrentResource;
import org.ogema.core.model.units.EnergyResource;
import org.ogema.core.model.units.FrequencyResource;
import org.ogema.core.model.units.PhysicalUnit;
import org.ogema.core.model.units.PowerResource;
import org.ogema.core.model.units.VoltageResource;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.devices.sensoractordevices.SensorDeviceLabelled;
import org.ogema.model.sensors.ElectricCurrentSensor;
import org.ogema.model.sensors.ElectricFrequencySensor;
import org.ogema.model.sensors.ElectricVoltageSensor;
import org.ogema.model.sensors.EnergyAccumulatedSensor;
import org.ogema.model.sensors.FlowSensor;
import org.ogema.model.sensors.GenericFloatSensor;
import org.ogema.model.sensors.PowerSensor;
import org.ogema.model.sensors.Sensor;
import org.ogema.model.sensors.VolumeAccumulatedSensor;
import org.ogema.tools.resource.util.ResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the HmIP-ESI energy meter reader.
 *
 * @author jlapp
 */
// increased ranking because there are more generic handlers that will pick up ENERGIE_METER_TRANSMITTER
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=100"})
public class IpEsiChannels extends AbstractDeviceHandler implements DeviceHandlerFactory {

	Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public DeviceHandler createHandler(HomeMaticConnection connection) {
		return new IpEsiChannels(connection);
	}

	public IpEsiChannels() {
		super(null);
	}

	public IpEsiChannels(HomeMaticConnection conn) {
		super(conn);
	}

	class EnergyMeterEventListener implements HmEventListener {

		final SensorDeviceLabelled sdl;
		final String address;

		public EnergyMeterEventListener(SensorDeviceLabelled sdl, String address) {
			this.sdl = sdl;
			this.address = address;
		}

		@Override
		public void event(List<HmEvent> events) {
			GenericFloatSensor mainSensor = null;
			String mainSensorLabel = null;
			for (HmEvent e : events) {
				if (!address.equals(e.getAddress())) {
					continue;
				}
				switch (e.getValueKey()) {
					case "POWER":
						PowerResource pwr = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Power_" + address), PowerSensor.class).reading();
						setValueAndActivate(pwr, e.getValueFloat(), sdl);
						logger.debug("power reading updated: {} = {}", pwr.getPath(), e.getValueFloat());
						mainSensor = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Power_" + address), PowerSensor.class);
						mainSensorLabel = "Power";
						break;
					case "CURRENT": {
						ElectricCurrentResource reading = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Current_" + address), ElectricCurrentSensor.class).reading();
						float a = e.getValueFloat() / 1000.0f;
						setValueAndActivate(reading, a, sdl);
						logger.debug("current reading updated: {} = {}", reading.getPath(), a);
						mainSensor = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Current_" + address), ElectricCurrentSensor.class);
						mainSensorLabel = "Current";
						break;
					}
					case "VOLTAGE": {
						VoltageResource reading = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Voltage_" + address), ElectricVoltageSensor.class).reading();
						setValueAndActivate(reading, e.getValueFloat(), sdl);
						logger.debug("voltage reading updated: {} = {}", reading.getPath(), e.getValueFloat());
						mainSensor = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Voltage_" + address), ElectricVoltageSensor.class);
						mainSensorLabel = "Voltage";
						break;
					}
					case "FREQUENCY": {
						FrequencyResource reading = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Frequency_" + address), ElectricFrequencySensor.class).reading();
						setValueAndActivate(reading, e.getValueFloat(), sdl);
						logger.debug("frequency reading updated: {} = {}", reading.getPath(), e.getValueFloat());
						mainSensor = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Frequency_" + address), ElectricFrequencySensor.class);
						mainSensorLabel = "Frequency";
						break;
					}
					case "ENERGY_COUNTER": {
						EnergyResource reading = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Energy_" + address), EnergyAccumulatedSensor.class).reading();
						//ESI returns energy as Wh
						if (reading.getUnit() != PhysicalUnit.KILOWATT_HOURS) {
							reading.setUnit(PhysicalUnit.KILOWATT_HOURS);
						}
						setValueAndActivate(reading, e.getValueFloat() / 1000f, sdl);
						logger.debug("energy reading updated: {} = {}", reading.getPath(), e.getValueFloat());
						mainSensor = sdl.sensors()
								.getSubResource(ResourceUtils.getValidResourceName("Energy_" + address), EnergyAccumulatedSensor.class);
						mainSensorLabel = "Energy";
						break;
					}
					case "GAS_FLOW": {
						Sensor sens = sdl.sensors()
							.getSubResource(ResourceUtils.getValidResourceName("GasFlow_" + address), FlowSensor.class);
						//XXX conversion?
						setValueAndActivate((FloatResource) sens.reading(), e.getValueFloat(), sdl);
						sdl.mainSensor().setAsReference(sens);
						if (!sdl.mainSensorTitle().isActive()) {
							sdl.mainSensorTitle().create();
							sdl.mainSensorTitle().setValue("Gas Flow");
							sdl.mainSensorTitle().activate(false);
						}
						logger.debug("gas flow reading updated: {} = {}", sens.reading().getPath(), e.getValueFloat());
						break;
					}
					case "GAS_VOLUME": {
						Sensor sens = sdl.sensors()
							.getSubResource(ResourceUtils.getValidResourceName("GasVolume_" + address), VolumeAccumulatedSensor.class);
						//XXX conversion?
						setValueAndActivate((FloatResource) sens.reading(), e.getValueFloat(), sdl);
						sdl.mainSensor().setAsReference(sens);
						if (!sdl.mainSensorTitle().isActive()) {
							sdl.mainSensorTitle().create();
							sdl.mainSensorTitle().setValue("Gas Volume");
							sdl.mainSensorTitle().activate(false);
						}
						logger.debug("gas volume reading updated: {} = {}", sens.reading().getPath(), e.getValueFloat());
						break;
					}
				}
			}
			if (sdl.exists()) {
				sdl.activate(false);
			}
			if (!sdl.mainSensorTitle().isActive() && mainSensorLabel != null) {
				sdl.mainSensorTitle().create();
				sdl.mainSensorTitle().setValue(mainSensorLabel);
				sdl.mainSensorTitle().activate(false);
				sdl.mainSensor().setAsReference(mainSensor);
			}
		}

	}
	
	void setValueAndActivate(FloatResource r, float v, Resource top) {
		if (!r.exists()) {
			r.create();
		}
		r.setValue(v);
		if (!r.isActive()) {
			Resource a = r;
			do {
				a.activate(false);
				a = a.getParent();
			} while (a != null && !a.equals(top));
			top.activate(false);
		}
	}

	@Override
	/**
	 * Note: There are other handlers for ENERGIE_METER_TRANSMITTER but the
	 * driver will use this one for HmIP-ESI because of its higher service ranking.
	 */
	public boolean accept(DeviceDescription desc) {
		return "HmIP-ESI".equalsIgnoreCase(desc.getParentType())
				&& "ENERGIE_METER_TRANSMITTER".equalsIgnoreCase(desc.getType());
	}

	@Override
	public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
		LoggerFactory.getLogger(getClass()).debug("setup HmIP-ESI handler for address {}", desc.getAddress());
		String sdName = ResourceUtils.getValidResourceName("SENSORS_" + desc.getAddress());
		SensorDeviceLabelled sdl = parent.getSubResource(sdName, SensorDeviceLabelled.class);
		conn.addEventListener(new EnergyMeterEventListener(sdl, desc.getAddress()));
		ChannelUtils.linkMaintenanceWhenAvailable(parent, sdl.electricityStorage());
		//sdl.activate(true);
	}

}
