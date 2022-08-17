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
package org.ogema.model.devices.buildingtechnology;

import org.ogema.model.actors.OnOffSwitch;
import org.ogema.model.actors.MultiSwitch;
import org.ogema.model.connections.ElectricityConnection;
import org.ogema.model.prototypes.Connection;
import org.ogema.model.prototypes.PhysicalElement;
import org.ogema.model.ranges.PowerRange;

/**
 * Pump
 */
public interface Pump extends PhysicalElement {

	OnOffSwitch onOffSwitch();

	/**
	 * @return relative operating power setting (0..1).
	 */
	MultiSwitch setting();

	/**
	 * electrical connection and measurement
	 */
	ElectricityConnection electricityConnection();

	/**
	 * Rated power of the device.
	 */
	PowerRange ratedPower();
	
	/**
	 * The connection (thermal or fluid) the pump operates on. Should only
	 * be used for stand-alone pumps (if at all), where the connection can not
	 * be infered from an enclosing device.
	 * 
	 * @return (optional) connection
	 */
	Connection connection();
	
}
