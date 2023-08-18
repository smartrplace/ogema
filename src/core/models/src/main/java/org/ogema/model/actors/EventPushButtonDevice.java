package org.ogema.model.actors;

import org.ogema.core.model.ResourceList;
import org.ogema.model.devices.storage.ElectricityStorage;

public interface EventPushButtonDevice extends Actor {

	ElectricityStorage battery();

	ResourceList<EventPushButton> buttons();
}
