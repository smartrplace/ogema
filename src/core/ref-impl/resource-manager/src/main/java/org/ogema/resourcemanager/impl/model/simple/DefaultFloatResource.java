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
import org.ogema.core.model.simple.FloatResource;
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
public class DefaultFloatResource extends SingleValueResourceBase implements FloatResource {

	public DefaultFloatResource(VirtualTreeElement el, String path, ApplicationResourceManager resMan) {
		super(el, path, resMan);
	}

	@Override
	public float getValue() {
		checkReadPermission();
		return getEl().getData().getFloat();
	}

	private static long lastMinInterval = -1;
	
	@Override
	public boolean setValue(float value) {
		resMan.lockRead();
		try {
			final VirtualTreeElement el = getElInternal();
			String resToTest = System.getProperty("org.ogema.resourcemanager.impl.model.simple.testForNaN");
			if(resToTest != null && Float.isNaN(value) && el.getLocation().contains(resToTest))
				System.out.println("Writing NaN to "+el.getLocation());
			String resToTest2 = System.getProperty("org.ogema.resourcemanager.impl.model.simple.writeToConsole");
			if(resToTest2 != null && el.getLocation().contains(resToTest2))
				System.out.println("Writing "+value+" to "+el.getLocation());
			Long minIntreval = Long.getLong("org.ogema.resourcemanager.impl.model.simple.minWriteInterval");
			if(minIntreval != null && resToTest != null && el.getLocation().contains(resToTest)) {
				long now = System.currentTimeMillis();
				if((now - lastMinInterval) < minIntreval) {
					new IllegalStateException("Written too quickly:"+resToTest+":"+value+" after "+(now - lastMinInterval)+" msec").printStackTrace();
				}
				lastMinInterval = now;
			}
			if (el.isVirtual() || getAccessModeInternal() == AccessMode.READ_ONLY) {
				return false;
			}
			checkWritePermission();
			final SimpleResourceData data = el.getData();
			boolean changed = value != data.getFloat();
			data.setFloat(value);
			handleResourceUpdateInternal(changed);
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
	public float getAndSet(final float value) throws VirtualResourceException, SecurityException, ResourceAccessException {
		return getAndWriteInternal(value, false);
	}

	@Override
	public float getAndAdd(final float value) throws VirtualResourceException, SecurityException, ResourceAccessException {
		return getAndWriteInternal(value, true);
	}
	
	private final float getAndWriteInternal(final float value, final boolean addOrSet) {
		if (!exists())
			throw new VirtualResourceException("Resource " + path + " is virtual, cannot set value");
		checkWriteAccess();
		resMan.lockWrite(); 
		try {
			final float val = getValue();
			setValue(addOrSet ? (val + value) : value);
			return val;
		} finally {
			resMan.unlockWrite();
		}
	}
	
}
