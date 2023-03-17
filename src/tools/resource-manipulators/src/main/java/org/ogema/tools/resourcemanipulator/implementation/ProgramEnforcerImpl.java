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
package org.ogema.tools.resourcemanipulator.implementation;

import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resourcemanipulator.ResourceManipulatorImpl;

import java.util.Objects;

import org.ogema.core.model.Resource;
import org.ogema.core.model.ValueResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.resourcemanager.AccessPriority;
import org.ogema.tools.resourcemanipulator.configurations.ProgramEnforcer;
import org.ogema.tools.resourcemanipulator.model.ProgramEnforcerModel;
import org.ogema.tools.resourcemanipulator.model.RangeFilter;

/**
 * @author Timo Fischer, Fraunhofer IWES
 */
public class ProgramEnforcerImpl implements ProgramEnforcer {

	private final ResourceManipulatorImpl m_base;
	private Resource m_targetResource;
	private long m_updateInterval;
	private AccessPriority m_priority;
	private boolean m_exclusiveAccessRequired;
	private boolean m_deactivate;
	private String m_scheduleName;
	private long m_maxScheduleValueLifetime;
	
	private Float[] m_range = null;
	private int m_rangeMode = 0;
	

	// Configuration this is connected to (null if not connected)
	private ProgramEnforcerModel m_config;

	/**
	 * Creates an instance of the configuration object from an existing
	 * configuration.
	 */
	public ProgramEnforcerImpl(ResourceManipulatorImpl base, ProgramEnforcerModel configResource) {
		m_base = base;
		m_targetResource = configResource.targetResource();
		m_updateInterval = configResource.updateInterval().getValue();
		m_priority = AccessPriority.valueOf(configResource.priority().getValue());
		m_exclusiveAccessRequired = configResource.exclusiveAccessRequired().getValue();
		m_config = configResource;
		m_deactivate = m_config.deactivateIfValueMissing().getValue();
		m_scheduleName = m_config.scheduleResourceName().isActive() ? m_config.scheduleResourceName().getValue() : null;
		m_maxScheduleValueLifetime = m_config.maxScheduleValueLifetime().isActive() ? m_config.maxScheduleValueLifetime().getValue() : -1;
		final RangeFilter filter = m_config.range();
		if (filter.isActive() && filter.range().isActive()) {
			final Float upper = filter.range().upperLimit().isActive() ? filter.range().upperLimit().getValue() : null;
			final Float lower = filter.range().lowerLimit().isActive() ? filter.range().lowerLimit().getValue() : null;
			if (upper != null || lower != null) {
				m_range = new Float[] {upper, lower};
				m_rangeMode = filter.mode().isActive() ? filter.mode().getValue() : 0;
			}
		}
	}

	public ProgramEnforcerImpl(ResourceManipulatorImpl base) {
		m_base = base;
		m_targetResource = null;
		m_updateInterval = 10000l;
		m_priority = AccessPriority.PRIO_LOWEST;
		m_exclusiveAccessRequired = false;
		m_deactivate = true;
		m_config = null;
		m_scheduleName = null;
		m_maxScheduleValueLifetime = -1;
	}

	@Override
	public boolean commit() {
		if (m_targetResource == null) {
			return false;
		}
		// delete the old configuration if it exsited.
		if (m_config != null) {
			m_config.delete();
		}
		m_config = m_base.createResource(ProgramEnforcerModel.class);

		m_config.targetResource().setAsReference(m_targetResource);
		m_config.updateInterval().create();
		m_config.updateInterval().setValue(m_updateInterval);
		m_config.exclusiveAccessRequired().create();
		m_config.exclusiveAccessRequired().setValue(m_exclusiveAccessRequired);
		m_config.priority().create();
		m_config.priority().setValue(m_priority.toString());
		m_config.deactivateIfValueMissing().create();
		m_config.deactivateIfValueMissing().setValue(m_deactivate);
		if (m_scheduleName != null) {
			m_config.scheduleResourceName().<StringResource> create().setValue(m_scheduleName);
		}
		if (m_maxScheduleValueLifetime > 0)
			m_config.maxScheduleValueLifetime().<TimeResource> create().setValue(m_maxScheduleValueLifetime);
		if (m_range != null) {
			final RangeFilter filter = m_config.range();
			if (m_range[0] != null && Float.isFinite(m_range[0]))
				filter.range().lowerLimit().<FloatResource> create().setValue(m_range[0]);
			if (m_range[1] != null && Float.isFinite(m_range[1]))
				filter.range().upperLimit().<FloatResource> create().setValue(m_range[1]);
			if (m_rangeMode != 0)
				filter.mode().<IntegerResource> create().setValue(m_rangeMode);
		}
		m_config.activate(true);
		return true;
	}

	@Override
	public void remove() {
		if (m_config != null && m_config.exists()) {
			m_config.delete();
		}
	}

	@Override
	public void enforceProgram(ValueResource resource, long updateInterval, AccessPriority priority) {
		m_targetResource = resource;
		m_updateInterval = updateInterval;
		if (priority == null) {
			m_priority = AccessPriority.PRIO_LOWEST;
			m_exclusiveAccessRequired = false;
		}
		else {
			m_priority = priority;
			m_exclusiveAccessRequired = true;
		}
	}

	@Override
	public void enforceProgram(ValueResource resource, long updateInterval) {
		m_targetResource = resource;
		m_updateInterval = updateInterval;
		m_priority = AccessPriority.PRIO_LOWEST;
		m_exclusiveAccessRequired = false;
	}

	@Override
	public AccessPriority getAccessPriority() {
		return (m_exclusiveAccessRequired) ? m_priority : null;
	}

	@Override
	public long getUpdateInterval() {
		return m_updateInterval;
	}

	@Override
	public void setRangeFilter(float lowerBoundary, float upperBoundary) throws RuntimeException {
		setRangeFilter(lowerBoundary, upperBoundary, 0);
	}
	
	@Override
	public void setTargetScheduleName(String scheduleName) {
		Objects.requireNonNull(scheduleName);
		if (scheduleName.contains("/") || !ResourceUtils.isValidResourcePath(scheduleName))
			throw new IllegalArgumentException("Illegal schedule name " +scheduleName);
		m_scheduleName = scheduleName;
		if (m_config != null && m_config.exists()) {
			final StringResource nameCfg = m_config.scheduleResourceName().create(); 
			nameCfg.setValue(m_scheduleName);
			if (m_config.isActive())
				nameCfg.activate(false);
		}
	}
	
	@Override
	public String getTargetScheduleName() {
		return m_scheduleName != null ? m_scheduleName : "program";
	}

	@Override
	public void setRangeFilter(float lowerBoundary, float upperBoundary, int mode) throws RuntimeException {
		final boolean configExists = m_config != null && m_config.exists();
		boolean lowerNaN = Float.isNaN(lowerBoundary);
		boolean upperNaN = Float.isNaN(upperBoundary);
		if (lowerNaN && upperNaN) {
			if (configExists)
				m_config.range().delete();
			m_range = null;
			m_rangeMode = 0;
			return;
		}
		RangeFilter filter = configExists ? m_config.range() : null;
		m_range = new Float[] {null, null};
		m_rangeMode = mode;
		if (!lowerNaN) {
			if (configExists) {
				filter.range().lowerLimit().create();
				filter.range().lowerLimit().setValue(lowerBoundary);
			}
			m_range[0] = lowerBoundary;
		}
		if (!upperNaN) {
			if (configExists) {
				filter.range().upperLimit().create();
				filter.range().upperLimit().setValue(upperBoundary);
			}
			m_range[1] = upperBoundary;
		}
		if (configExists) {
			filter.mode().create();
			filter.mode().setValue(mode);
			filter.activate(true);
		}

	}

	@Override
	public ValueResource getResource() {
		return (ValueResource) m_targetResource;
	}

	@Override
	public void deactivateTargetIfProgramMissing(boolean deactivate) throws RuntimeException {
		this.m_deactivate = deactivate;
		if (m_config != null && m_config.exists() && m_config.deactivateIfValueMissing().exists()) {
			m_config.deactivateIfValueMissing().setValue(deactivate);
		}
	}
	
	@Override
	public void setMaxScheduleValueLifetime(long millis) {
		this.m_maxScheduleValueLifetime = millis > 0 ? millis : -1;
		final boolean configExists = m_config != null && m_config.exists();
		if (configExists) {
			if (millis > 0) {
				final TimeResource lifetimeRes = m_config.maxScheduleValueLifetime().create();
				lifetimeRes.setValue(millis);
				if (m_config.isActive())
					lifetimeRes.activate(false);
			} else {
				m_config.maxScheduleValueLifetime().delete();
			}
		}
	}
	
	/**
	 * Maximum schedule value lifetime in milliseconds.
	 * Returns -1 if the lifetime is infinite.
	 * @return
	 */
	@Override
	public long getMaxScheduleValueLifetime() {
		return m_maxScheduleValueLifetime;
	}

	@Override
	public void deactivate() {
		if (m_config != null)
			m_config.deactivate(true);
	}

	@Override
	public void activate() {
		if (m_config != null)
			m_config.activate(true);
	}

}
