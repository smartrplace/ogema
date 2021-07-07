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
import org.ogema.model.prototypes.PhysicalElement;
import org.ogema.model.ranges.PowerRange;

/**
 * Ventilator / mechanical fan
 */
public interface MechanicalFan extends PhysicalElement {

	/**
	 * Switch to control when the air conditioning draws electrical power to
	 * generate cold
	 */
	OnOffSwitch onOffSwitch();

	/**
	 * Power setting relative to maximum powers.
	 * If this controls the fan speed the following standard values are defined:
	 * 0: auto<br>
	 * 1: low<br>
	 * 2: medium<br>
	 * 3: max<br>
	 * If more fan speeds are supported they shall be mapped to these values by the driver.
	 * If only two speeds are supported medium may be mapped to may or a better decision
	 * may be made by the driver if possible. A more detailed representation of the
	 * fan speed may be given in a decorator.
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
}
