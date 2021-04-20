package org.ogema.model.metering.special;

import org.ogema.model.prototypes.PhysicalElement;
import org.ogema.model.sensors.FlowSensor;
import org.ogema.model.sensors.GenericFloatSensor;
import org.ogema.model.sensors.TemperatureSensor;
import org.ogema.model.sensors.VelocitySensor;
import org.ogema.model.sensors.VolumeAccumulatedSensor;

public interface FlowProbe extends PhysicalElement {
	VolumeAccumulatedSensor volumeCounter();
	VelocitySensor velocity();
	FlowSensor flow();
	/** Pressure in Pa*/
	GenericFloatSensor pressure();
	TemperatureSensor temperature();
}
