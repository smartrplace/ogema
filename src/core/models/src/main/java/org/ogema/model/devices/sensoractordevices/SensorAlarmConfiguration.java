package org.ogema.model.devices.sensoractordevices;

import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.model.prototypes.Data;

public interface SensorAlarmConfiguration extends Data {

	/** Reference to the resource containing the values to be supervised by the alarming configuration.
	 * @return
	 */
	SingleValueResource sensorVal();

	/** Lower limit of allowed sensor values. Any sensor value below this would trigger an alarm*/
	FloatResource lowerLimit();

	/** Upper limit of allowed sensor values. Any sensor value above this would trigger an alarm*/
	FloatResource upperLimit();
	
	/** If the values are outside the limits specified not more than the interval time given here
	 * then no alarm will be generated (minutes)
	 */
	FloatResource maxViolationTimeWithoutAlarm();
	
	/** Maximum time between new values (minutes). A negative value indicates that no alarm shall be generated if no more values are received.*/
	FloatResource maxIntervalBetweenNewValues();
	
	/** If active and false the alarm will not send messages, only the alarm configuration will be created. If not active it is
	 * used as true.*/
	BooleanResource sendAlarm();
	
}
