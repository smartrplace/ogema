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
package org.ogema.model.locations;

import org.ogema.core.model.array.StringArrayResource;
import org.ogema.core.model.array.TimeArrayResource;
import org.ogema.model.prototypes.Data;
import org.ogema.model.prototypes.PhysicalElement;

/**
 * Defines information about the physical location of a device or a sensor reading.
 */
public interface Location extends Data {

	/**
	 * Reference to the device that this object is attached to. Note that although rooms are also PhysicalElements, 
	 * the element "room" should be used if this information is given. If a hierarchy of objects exists that all
	 * would be suitable (room-workplace-device on workplace) this should reference the smallest unit in the
	 * hierarchy (in the example: the device on the workplace).
	 */
	PhysicalElement device();

	/** Room the device is located in. Also the location "outside a building" should be modeled as a room.
	 * Note that the room information might only be given by a higher level in the device hierarchy (see "device")
	 */
	Room room();

	/** The length of roomLocationHistory and roomLocationStart shall be equal. The resources contain information
	 * to which rooms the device was assigned in the past and currently. This may also be used to provide information
	 * on real rooms without adding the room information for applications if the device shall just be used for
	 * monitoring. So this information shall not be obtained automatically from the location()#room() field.<br>
	 * The last entry in the list is the current room to which the device is assigned. If the room is not assigned
	 * to any room at a certain interval or currently then for the respective interval the entry in roomLocationHistory
	 * shall be an empty String. Otherwise it shall contain the room location as String.*/
	StringArrayResource roomHistory();
	/** See roomHistory */
	TimeArrayResource roomStart();
	
	/** Geographical position */
	GeographicLocation geographicLocation();
}
