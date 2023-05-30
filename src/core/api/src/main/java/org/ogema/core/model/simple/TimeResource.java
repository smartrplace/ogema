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
package org.ogema.core.model.simple;

import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.model.schedule.AbsoluteSchedule;
import org.ogema.core.recordeddata.RecordedData;
import org.ogema.core.resourcemanager.ResourceAccessException;
import org.ogema.core.resourcemanager.VirtualResourceException;
import org.ogema.core.timeseries.TimeSeries;

/**
 * Resource representing a time difference or a date. Time differences are given in milliseconds. Dates are
 * represented as "milliseconds since begin of 1970". Time differences always refer to physical time, not calendar time.
 * Dates are usually read from some system, so for dates the exact behavior with respect to leap seconds is not defined.
 */
public interface TimeResource extends SingleValueResource {

	/**
	 * Gets the time difference (in ms).
	 */
	long getValue();

	/**
	 * Sets the time difference
	 * 
	 * @param value
	 *            new value for the time difference (in ms).
	 * @return returns true if the value could be written, false if not (e.g. if access mode is read-only).
	 */
	boolean setValue(long value);
	
	/**
	 * Sets the resource value to value with the given timestamp. Timestamp should
	 * not be greater than current framework time. If the resource is recorded,
	 * the timeseries database may only record values that are newer than the last
	 * recorded value. The timestamp will also be returned as {@link #getLastUpdateTime()}
	 * 
	 * @param value
	 * @param timestamp framework time, -1 to use current framework time.
	 * @return returns true if the value could be written, false if not (e.g. if access mode is read-only).	 
	 */
	boolean setValue(long value, long timestamp);
	
	/**
	 * Atomically sets to the given value and returns the previous value.
	 * 
	 * @param value
	 * 		the new value to be set
	 * @return
	 * 		the previous value
	 * @throws VirtualResourceException
	 * 		if the resource is virtual
	 * @throws SecurityException
	 * 		if the caller does not have the read and write permission for this resource
	 * @throws ResourceAccessException
	 * 		if access mode is read-only
	 */
	long getAndSet(long value) throws VirtualResourceException, SecurityException, ResourceAccessException;
	
	/**
	 * Atomically adds the given value and returns the previous value.
	 * 
	 * @param value
	 * 		the value to be added
	 * @return
	 * 		the previous value
	 * @throws VirtualResourceException
	 * 		if the resource is virtual
	 * @throws SecurityException
	 * 		if the caller does not have the read and write permission for this resource
	 * @throws ResourceAccessException
	 * 		if access mode is read-only
	 */
	long getAndAdd(long value) throws VirtualResourceException, SecurityException, ResourceAccessException;
	
	/**
	 * Gets recorded past values.
	 * 
	 * @return returns the handler for the recorded data.
	 */
	RecordedData getHistoricalData();

	/**
	 * Future prognosis for this value. The data type, unit and interpretation of
	 * the values in the schedule are the same as the value in this. If multiple
	 * forecasts are available for some reason, this shall reflect the best-guess
	 * (either the best individual forecast or the best combined forecast). The
	 * other forecasts can be added as decorators.
	 */
	AbsoluteSchedule forecast();

	/**
	 * Future behavior of this value that shall be reached through management
	 * operations. 
	 * The data type, unit and interpretation of
	 * the values in the schedule are the same as the value in this.
	 */
	AbsoluteSchedule program();

	/**
	 * Historical data, including data obtained through the OGEMA logging service and
	 * explicitly added historical data. <br>
	 * In order to access only the data logged by the framework use the method
	 * {@link #getHistoricalData()} instead. <br>
	 * Note that the {@link TimeSeries#deleteValues} methods only remove the explicitly added data here, 
	 * not the framework log data. You can override a logged data point by adding a {@link SampledValue} at 
	 * the same timestamp. The logged value will still be accessible via {@link #getHistoricalData()}.
	 */
	AbsoluteSchedule historicalData();
}
