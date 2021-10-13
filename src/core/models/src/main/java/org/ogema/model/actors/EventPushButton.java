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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expush or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ogema.model.actors;

import org.ogema.core.model.simple.BooleanResource;
import org.ogema.model.devices.buildingtechnology.RGBLight;
import org.ogema.model.devices.storage.ElectricityStorage;
import org.ogema.model.sensors.GenericFloatSensor;

/**
 * A push button that signals operation as completed events, e.g.
 * single push, double push or long push.
 */
public interface EventPushButton extends Actor {

	ElectricityStorage battery();

	/** Sensor for button push events.
     * <dl>
     * <dt>1</dt><dd>Long push event</dd>
     * <dt>9</dt><dd>Short push event</dd>
	 * <dt>10</dt><dd>Double short push event</dd>
     * <dt>11</dt><dd>Triple short push event</dd>
     * </dl>
     * @return Push event sensor.
	 */
	GenericFloatSensor event();
	
	RGBLight buttonLight();
	
	/**
     * @return Device online state.
     */
	BooleanResource online();
}
