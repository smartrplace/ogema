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

import java.util.HashMap;
import java.util.Map;

import org.ogema.core.model.schedule.AbsoluteSchedule;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.recordeddata.RecordedData;
import org.ogema.core.resourcemanager.AccessMode;
import org.ogema.core.resourcemanager.ResourceAccessException;
import org.ogema.core.resourcemanager.ResourceNotFoundException;
import org.ogema.core.resourcemanager.VirtualResourceException;
import org.ogema.persistence.impl.faketree.ScheduleTreeElement;
import org.ogema.resourcemanager.impl.ApplicationResourceManager;
import org.ogema.resourcemanager.impl.model.schedule.HistoricalSchedule;
import org.ogema.resourcemanager.virtual.VirtualTreeElement;
import org.ogema.resourcetree.SimpleResourceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		try {
			return getEl().getData().getFloat();
		} catch(ResourceNotFoundException e) {
			return Float.NaN;
		}
	}

	//private static long lastMinInterval = -1;
	
	@Override
	public boolean setValue(float value) {
		return setValue(value, -1);
	}
	
	static final Logger LOG = LoggerFactory.getLogger(ScheduleTreeElement.class);
	private static final String RES_TO_TEST = System.getProperty("org.ogema.resourcemanager.impl.model.simple.testForNaN");
	private static final String RES_TO_TEST2 = System.getProperty("org.ogema.resourcemanager.impl.model.simple.writeToConsole");
	private static final Long MIN_INTERVAL = Long.getLong("org.ogema.resourcemanager.impl.model.simple.minWriteInterval");
	private static final Integer LOG_ERROR_CODE = Integer.getInteger("org.ogema.resourcemanager.impl.model.simple.logErrorCode");
	private static final Integer SKIP_BEFORELOG_ERROR_CODE = Integer.getInteger("org.ogema.resourcemanager.impl.model.simple.skipErrorsBeforeLogCode");
	private static final Map<String, Integer> COUNT_ERROR = new HashMap<>();

	private static final String RESSTART_TO_TEST = System.getProperty("org.ogema.resourcemanager.impl.model.simple.testResStart");
	private static final Float VAL_TO_TEST = getFloatProperty("org.ogema.resourcemanager.impl.model.simple.testForValue");
	private static Float getFloatProperty(String propertyName) {
		String prop = System.getProperty(propertyName);
		if(prop == null)
			return null;
		try {
			return Float.parseFloat(prop);
		} catch(NumberFormatException e) {
			return null;
		}
	}
	
	@Override
	public boolean setValue(float value, long timestamp) {
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
				LOG.warn(text, new IllegalStateException(text));
				logErrorCode(-18);				
			}
			else {
				if(RES_TO_TEST != null && Float.isNaN(value) && location.contains(RES_TO_TEST))
					LOG.warn("Writing NaN to "+location);
				if(RES_TO_TEST2 != null && location.contains(RES_TO_TEST2)) {
					LOG.info("Writing "+value+" to "+location, 
							new IllegalStateException("Writing "+value+" to "+location));
				}
				if((VAL_TO_TEST != null) && (VAL_TO_TEST == value)
						&& (RES_TO_TEST != null) && location.contains(RES_TO_TEST) 
						&& ((RESSTART_TO_TEST == null) || location.contains(RESSTART_TO_TEST))) {
					String text = "Wrote critical value:"+location+":"+value;
					LOG.warn(text, new IllegalStateException(text));					
					logErrorCode(LOG_ERROR_CODE+1);
				}
				if((MIN_INTERVAL != null) && (RES_TO_TEST != null) && location.contains(RES_TO_TEST)) {
					long now = getFrameworkTime();
					long last = getLastUpdateTime();
					if((now - last) < MIN_INTERVAL) {
						String text = "Written too quickly:"+location+":"+value+" after "+(now - last)+" msec";
						LOG.warn(text, new IllegalStateException(text));
						if(LOG_ERROR_CODE != null) {
							Integer count = null;
							if(SKIP_BEFORELOG_ERROR_CODE != null) {
								count = COUNT_ERROR.get(location);
								if(count == null)
									count = 0;
								COUNT_ERROR.put(location, count+1);
							}
							if(count == null || (count >= SKIP_BEFORELOG_ERROR_CODE)) {
								logErrorCode(LOG_ERROR_CODE);
								LOG.warn("Wrote ERROR_CODE "+LOG_ERROR_CODE+" to logFileCheck!");
							}
						}
					} else
						COUNT_ERROR.remove(location);
				}
			}
			//*/
			if (el.isVirtual() || getAccessModeInternal() == AccessMode.READ_ONLY) {
				return false;
			}
			checkWritePermission();
			final SimpleResourceData data = el.getData();
			boolean changed = value != data.getFloat();
			data.setFloat(value);
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
