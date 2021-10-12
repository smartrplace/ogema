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
package org.ogema.model.actors;

import org.ogema.core.model.simple.BooleanResource;
import org.ogema.model.devices.buildingtechnology.RGBLight;
import org.ogema.model.devices.storage.ElectricityStorage;
import org.ogema.model.sensors.GenericFloatSensor;

/**
 * Models a remote control with a single button and an RGB light.
 */
public interface RemoteControlButton extends Actor {

	ElectricityStorage battery();

	/** Each time the button is operated an event is written. Several short press events are usually interpreted
	 * 	as a single event that represents a single action
	 *  1: Short pressed event<br>
	 *  2: Long pressed event<br>
	 *  3: Double short pressed event<br>
	 *  4: Triple short pressed event
	 */
	GenericFloatSensor event();
	
	RGBLight buttonLight();
	
	/**If false the device is offline*/
	BooleanResource online();
}
