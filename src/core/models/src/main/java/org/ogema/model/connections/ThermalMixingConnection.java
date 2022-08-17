package org.ogema.model.connections;

import org.ogema.model.connections.ThermalConnection;
import org.ogema.model.devices.buildingtechnology.Pump;
import org.ogema.model.devices.connectiondevices.ThermalValve;
import org.ogema.model.sensors.TemperatureSensor;

/**
 * A thermal connection on a heat generator which can additionally mix
 * the return and input flows and has a separate pump.
 * 
 * @author jlapp
 */
public interface ThermalMixingConnection extends ThermalConnection {
	
	TemperatureSensor returnTemperature();

	/**
	 * Mixing valve controlling the return / input ratio of the connection.
	 * @return mixing valve.
	 */
	ThermalValve valve();
	
	Pump pump();
	
}
