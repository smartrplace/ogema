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
package org.ogema.resourcemanager.impl.model.simple;

import org.ogema.core.model.schedule.AbsoluteSchedule;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.recordeddata.RecordedData;
import org.ogema.core.resourcemanager.AccessMode;
import org.ogema.core.resourcemanager.ResourceAccessException;
import org.ogema.core.resourcemanager.VirtualResourceException;
import org.ogema.resourcemanager.impl.ApplicationResourceManager;
import org.ogema.resourcemanager.impl.model.schedule.HistoricalSchedule;
import org.ogema.resourcemanager.virtual.VirtualTreeElement;
import org.ogema.resourcetree.SimpleResourceData;

/**
 * 
 * @author jlapp
 */
public class DefaultBooleanResource extends SingleValueResourceBase implements BooleanResource {

	public DefaultBooleanResource(VirtualTreeElement el, String path, ApplicationResourceManager resMan) {
		super(el, path, resMan);
	}

	@Override
	public boolean getValue() {
		checkReadPermission();
		return getEl().getData().getBoolean();
	}
	
	@Override
	public boolean setValue(boolean value) {
		return setValue(value, -1);
	}
	
	private static final String RES_TO_TEST2 = System.getProperty("org.ogema.resourcemanager.impl.model.simple.writeToConsole.boolean");
	private static final Integer VAL_TO_TEST2 = Integer.getInteger("org.ogema.resourcemanager.impl.model.simple.writeToConsole.value.boolean");
	
	@Override
	public boolean setValue(boolean value, long timestamp) {
		resMan.lockRead();
		try {
			final VirtualTreeElement el = getElInternal();
			
			// /*
			final String location;
			if(el.getLocation() == null)
				location = el.getPath();
			else
				location = el.getLocation();
			if(location == null) {
				final String ID;
				if(el.getName() != null)
					ID = el.getName();
				else 
					ID = "ResID::"+el.getResID();
				String text = "el.getLocation() is null for "+ID;
				DefaultFloatResource.LOG.warn(text, new IllegalStateException(text));
				logErrorCode(-19);				
			}
			else {
				if(RES_TO_TEST2 != null && location.contains(RES_TO_TEST2)
						&& (VAL_TO_TEST2 == null || (value == (VAL_TO_TEST2 >= 1)))) {
					DefaultFloatResource.LOG.info("Writing boolean "+value+" to "+location, 
							new IllegalStateException("Writing "+value+" to "+location));
				}
			}
			
			
			if (el.isVirtual() || getAccessModeInternal() == AccessMode.READ_ONLY) {
				return false;
			}
			checkWritePermission();
			final SimpleResourceData data = el.getData();
			boolean changed = value != data.getBoolean();
			data.setBoolean(value);
			handleResourceUpdateInternal(changed, timestamp);
		} finally {
			resMan.unlockRead();
		}
		return true;
	}

	@Override
	public RecordedData getHistoricalData() {
		checkReadPermission();
		return getResourceDB().getRecordedData(getEl());
	}

	@Override
	public AbsoluteSchedule forecast() {
		return getSubResource("forecast", AbsoluteSchedule.class);
	}

	@Override
	public AbsoluteSchedule program() {
		return getSubResource("program", AbsoluteSchedule.class);
	}

	@Override
	public AbsoluteSchedule historicalData() {
		return getSubResource(HistoricalSchedule.PATH_IDENTIFIER, AbsoluteSchedule.class);
	}
	
	@Override
	public boolean getAndSet(final boolean value) throws VirtualResourceException, SecurityException, ResourceAccessException {
		if (!exists())
			throw new VirtualResourceException("Resource " + path + " is virtual, cannot set value");
		checkWriteAccess();
		resMan.lockWrite(); 
		try {
			final boolean val = getValue();
			setValue(value);
			return val;
		} finally {
			resMan.unlockWrite();
		}
	}

}
