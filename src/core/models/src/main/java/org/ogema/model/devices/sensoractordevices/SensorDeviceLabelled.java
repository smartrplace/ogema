package org.ogema.model.devices.sensoractordevices;

import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.model.connections.ElectricityConnection;
import org.ogema.model.devices.storage.ElectricityStorage;
import org.ogema.model.prototypes.PhysicalElement;
import org.ogema.model.sensors.Sensor;

/**
 * A generic single device containing one or multiple sensors. All sensors provided by sensor device shall be contained in the
 * sensor list as an instance of their actual sensor type. Models from package org.ogema.core.model.commonsensors shall be used.
 * This version of SensorDevice allows to specify some additional information for user display etc. of the device.
 */
public interface SensorDeviceLabelled extends PhysicalElement {

	/**
	 * Electricity connection in case the device is connected to an electricity circuit.
	 */
	ElectricityConnection electricityConnection();

	/**
	 * Battery for a sensor device operating on batteries.
	 */
	ElectricityStorage electricityStorage();

	/**
	 * The sensors contained in the device. The sensors contained in this list
	 * do not need to be all of the same sensor type, so a SensorDevice can 
	 * for example contain a light sensor and a temperature sensor.
	 */
	ResourceList<Sensor> sensors();
	
	/** Reference to the main sensor of the device. The value of this sensor shall be used for primary
	 * display in a list of devices and to determine if the connection to the device is still active.
	 * Note the {@link Sensor#reading()} must provide a {@link SingleValueResource} here that has a value that can be
	 * converted to a numeric.
	 */
	Sensor mainSensor();
	
	/** The title usually should contain the physical unit also */
	StringResource mainSensorTitle();
	
	/** Short human readable device type (English language)*/
	StringResource deviceTypeName();
	
	/** Add entry here for each sensor that shall have an alarming configuration*/
	ResourceList<SensorAlarmConfiguration> alarmConfigurations();
	
	/** Reference device representing network parent, e.g. router, controller, CCU etc.
	 * Note that the gateway as network parent usually is not returned explicitly.*/
	PhysicalElement networkParent();
}
