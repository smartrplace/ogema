/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ogema.driver.hmhl.pattern;

import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.units.TemperatureResource;
import org.ogema.core.model.units.VoltageResource;
import org.ogema.core.resourcemanager.AccessMode;
import org.ogema.core.resourcemanager.AccessPriority;
import org.ogema.core.resourcemanager.pattern.ResourcePattern;
import org.ogema.model.devices.buildingtechnology.Thermostat;

/**
 * Creation pattern for a thermostat supported by this driver.
 */
public class ThermostatPattern extends ResourcePattern<Thermostat> {

	/**
	 * Current temperature reading measured by the thermostat.
	 */
	public final TemperatureResource currentTemperature = model.temperatureSensor().reading();

	/**
	 * Desired temperature that is reported by the device (device feedback).
	 */
	public final TemperatureResource remoteDesiredTemperature = model.temperatureSensor().deviceFeedback().setpoint();

	/**
	 * Current battery voltage.
	 */
	public final VoltageResource batteryVoltage = model.battery().internalVoltage().reading();

	/**
	 * Battery charge status
	 */
	public FloatResource batteryCharge = model.battery().chargeSensor().reading();

	/**
	 * Current valve position in [0;1].
	 */
	public final FloatResource valvePosition = model.valve().setting().stateFeedback();

	/**
	 * Control setting defining if the switch is controllable or not.
	 */
	public final BooleanResource isSwitchControllable = model.valve().setting().controllable();

	/**
	 * Temperature setpoint set by the OGEMA management system.
	 */
	public final TemperatureResource localDesiredTemperature = model.temperatureSensor().settings().setpoint();

	/**
	 * Default constructor required by the framework. Do not change.
	 */
	public ThermostatPattern(Resource res) {
		super(res);
	}

	/**
	 * Initialize the field with defaults and request exclusive write access to all fields read from device.
	 */
	public void init() {
		currentTemperature.setKelvin(0.f);
		remoteDesiredTemperature.setCelsius(20.f);
		batteryVoltage.setValue(3.f);
		valvePosition.setValue(0.f);
		isSwitchControllable.setValue(false);
		localDesiredTemperature.setValue(20.f);

		currentTemperature.requestAccessMode(AccessMode.EXCLUSIVE, AccessPriority.PRIO_HIGHEST);
		remoteDesiredTemperature.requestAccessMode(AccessMode.EXCLUSIVE, AccessPriority.PRIO_HIGHEST);
		batteryVoltage.requestAccessMode(AccessMode.EXCLUSIVE, AccessPriority.PRIO_HIGHEST);
		valvePosition.requestAccessMode(AccessMode.EXCLUSIVE, AccessPriority.PRIO_HIGHEST);
		isSwitchControllable.requestAccessMode(AccessMode.EXCLUSIVE, AccessPriority.PRIO_HIGHEST);
	}
}
