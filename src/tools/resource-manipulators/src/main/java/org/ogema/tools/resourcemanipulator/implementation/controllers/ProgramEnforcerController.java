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
package org.ogema.tools.resourcemanipulator.implementation.controllers;

import org.ogema.core.application.ApplicationManager;
import org.ogema.core.application.Timer;
import org.ogema.core.application.TimerListener;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.logging.OgemaLogger;
import org.ogema.core.model.Resource;
import org.ogema.core.model.schedule.AbsoluteSchedule;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.resourcemanager.AccessMode;
import org.ogema.core.resourcemanager.AccessModeListener;
import org.ogema.core.resourcemanager.AccessPriority;
import org.ogema.core.resourcemanager.ResourceStructureEvent;
import org.ogema.core.resourcemanager.ResourceStructureListener;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.core.timeseries.InterpolationMode;
import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resourcemanipulator.configurations.ManipulatorConfiguration;
import org.ogema.tools.resourcemanipulator.configurations.ProgramEnforcer;
import org.ogema.tools.resourcemanipulator.model.Filter;
import org.ogema.tools.resourcemanipulator.model.ProgramEnforcerModel;
import org.ogema.tools.resourcemanipulator.model.RangeFilter;
import org.ogema.tools.resourcemanipulator.model.ResourceManipulatorModel;

/**
 * Enforces that a FloatResource always has the value configured in its program
 * or, in case of linear interpolation of the program, at least updates its
 * value to the one dictated by the program periodically.
 *
 * FIXME must react to changes of the target resource.(??)
 *
 * @author Timo Fischer, Fraunhofer IWES
 */
public class ProgramEnforcerController implements Controller, ResourceValueListener<Schedule>,
		ResourceStructureListener, AccessModeListener, TimerListener {

	private final ApplicationManager appMan;
	private final OgemaLogger logger;
	private final Resource target;
	private final Class<? extends Resource> resourceType;
	private final AbsoluteSchedule program;
	private final long updateInterval;
	private final boolean exclusiveAccess;
	private final AccessPriority priority;
	private boolean requiredWriteAccessGranted;
	private final RangeFilter rangeFilter;
	private final ProgramEnforcerModel config;
	private final BooleanResource deactivateIfValueMissing;
	private volatile Long lastExecutionTime = null;

	private Timer timer;

	public ProgramEnforcerController(ApplicationManager applicationManager, ProgramEnforcerModel configuration) {
		this.config = configuration;
		this.appMan = applicationManager;
		this.logger = applicationManager.getLogger();
		this.target = configuration.targetResource();
		if (!target.isReference(false)) {
			throw new IllegalStateException("Target resource is not set");
		}
		final Class<? extends Resource> clazz = configuration.targetResource().getResourceType();
		String targetSchedule = configuration.scheduleResourceName().isActive() ? configuration.scheduleResourceName().getValue() : "program";
		if (targetSchedule == null || !ResourceUtils.isValidResourcePath(targetSchedule) || targetSchedule.contains("/")) {
			appMan.getLogger().error("Invalid target schedule {} in ProrgamEnforcer model {}",targetSchedule, configuration);
			targetSchedule = "program";
		}
		program = target.getSubResource(targetSchedule, AbsoluteSchedule.class);
		if (BooleanResource.class.isAssignableFrom(clazz)) {
			resourceType = BooleanResource.class;
		}
		else if (IntegerResource.class.isAssignableFrom(clazz)) {
			resourceType = IntegerResource.class;
		}
		else if (TimeResource.class.isAssignableFrom(clazz)) {
			resourceType = TimeResource.class;
		}
		else if (FloatResource.class.isAssignableFrom(clazz)) {
			resourceType = FloatResource.class;
		}
		else if (StringResource.class.isAssignableFrom(clazz)) {
			resourceType = StringResource.class;
		}
		else {
			resourceType = null; // not a valid resource type.
			appMan.getLogger().error(
					"ProgramEnforcer found unexpected resource type " + clazz.getSimpleName()
							+ ". Must be a value resource type with optional schedule program().");
		}
		this.updateInterval = configuration.updateInterval().getValue();
		this.exclusiveAccess = configuration.exclusiveAccessRequired().getValue();
		AccessPriority prio = null;
		try {
			prio = AccessPriority.valueOf(configuration.priority().getValue().trim().toUpperCase());
		} catch (NullPointerException | IllegalArgumentException e) {
			logger.warn("Invalid access priority {} in {}",configuration.priority().getValue(), configuration.priority());
		}
		this.priority = prio != null ? prio : AccessPriority.PRIO_LOWEST;
		this.rangeFilter = configuration.range(); // may be virtual or inactive
		this.deactivateIfValueMissing = configuration.deactivateIfValueMissing();
	}

	@Override
	public void start() {
		if (resourceType == null || program == null) {
			logger.error("Cannot start enforcing the program on resource " + target.getLocation()
					+ " which is of type " + target.getResourceType().getSimpleName()
					+ ". Probably not a suitable resource type. Start of controller will be ignored.");
			return;
		}
		// request write access to the target resource.
		requiredWriteAccessGranted = false;
		target.addStructureListener(this);
		target.addAccessModeListener(this);
		if (exclusiveAccess) {
			target.requestAccessMode(AccessMode.EXCLUSIVE, priority);
		}
		else {
			target.requestAccessMode(AccessMode.SHARED, priority);
		}

		// keep informed about changes in the program (incl. structure events)
		program.addStructureListener(this);
		program.addValueListener(this);

		// Create a timer and initialize the target value for the first time.
		if (timer != null)
			timer.destroy();
		timer = appMan.createTimer(10 * 60 * 1000l, this); // this is just a random timer value, timer is re-scheduled after every invocation.
		timerElapsed(timer); // check if there is something to do now.
		logger.debug("New ProgramEnforcerController started for resource at " + target.getPath());
	}

	@Override
	public void stop() {
		if (timer != null) { // meaning: if the controller had been started.
			timer.destroy();
			program.removeValueListener(this);
			program.removeStructureListener(this);
			target.removeAccessModeListener(this);
			target.removeStructureListener(this);
		}
	}
	
	@Override
	public Class<? extends ManipulatorConfiguration> getType() {
		return ProgramEnforcer.class;
	}

	/**
	 * Note: this is not only called because of the timer having elapsed. It is
	 * also called explicitly by other methods in this controller.
	 */
	@Override
	public void timerElapsed(Timer timer) {
		timer.stop();

		// only do something if you actually can actually write to the target.
		final boolean targetWriteable = requiredWriteAccessGranted && target.exists();
		if (!targetWriteable) {
			return;
		}

		final long t0 = appMan.getFrameworkTime();
		final boolean hasGoodProgramValue = hasGoodProgramValue(t0);
		if (hasGoodProgramValue) {
			final SampledValue currentProgramValue = program.getValue(t0);
			setProgramValue(currentProgramValue, rangeFilter);
			//			target.activate(false);  // moved to setProgramValue method, since it may happen that target needs
			// to be deactived, if filter condition not satisfied
		}
		else { // no good program: de-activate.
			if (deactivateIfValueMissing.getValue())
				target.deactivate(false);
		}
		// if there is a valid program, estimate the next required call to this.
		if (program.isActive()) {
			restartTimer(t0);
		}
	}
	
	private boolean hasGoodProgramValue(long t0) {
		if (!program.isActive())
			return false;
		final SampledValue value = program.getValue(t0);
		if (value == null || value.getQuality() == Quality.BAD)
			return false;
		final TimeResource maxLifetimeRes = this.config.maxScheduleValueLifetime(); 
		final long maxLifetime = maxLifetimeRes.isActive() ? maxLifetimeRes.getValue() : -1;
		if (maxLifetime < 0)
			return true;
		final SampledValue last = program.getPreviousValue(t0);
		return last == null || t0-last.getTimestamp() < maxLifetime;
	}
	

	/**
	 * Set the timer to the next expected change event and re-start it.
	 *
	 * @param t0 time that shall be considered "now" for the purpose of
	 * re-scheduling (i.e. the time of the entry last written).
	 */
	@SuppressWarnings("fallthrough")
	private void restartTimer(long t0) {
		final long maxLifetime = config.maxScheduleValueLifetime().isActive() ? config.maxScheduleValueLifetime().getValue() : -1;
		long lifetimeExpires = -1;
		if (maxLifetime > 0 && program.isActive()) {
			final SampledValue previous = program.getPreviousValue(t0);
			if (previous != null) {
				lifetimeExpires = previous.getTimestamp() + maxLifetime;
				if (lifetimeExpires <= t0)
					lifetimeExpires = -1;
			}
		}
		long interval = lifetimeExpires > 0 ? lifetimeExpires - t0 : -1;
		final InterpolationMode interpolation = program.getInterpolationMode();
		
		switch (interpolation) {
		case LINEAR: {
			if (updateInterval > 0) {
				interval = interval > 0 ? Math.min(interval, updateInterval) : updateInterval;
				timer.setTimingInterval(interval);
				timer.resume();
				return;
			}
			// else: intentional fallthrough
		}
		case STEPS: {
			final SampledValue nextProgramValue = program.getNextValue(t0 + 1);
			if (nextProgramValue != null) {
				final long t1 = nextProgramValue.getTimestamp();
				interval = interval > 0 ? Math.min(interval, t1-t0) : t1-t0; 
				timer.setTimingInterval(interval);
				timer.resume();
			} else if (interval > 0) {
				timer.setTimingInterval(interval);
				timer.resume();
			}
			// note: timer may not have resumed here because schedule ended. An update of the schedule will re-create the timing sequence.
			return;
		}
		case NEAREST: {
			final SampledValue nextProgramValue = program.getNextValue(t0 + 1);
			if (nextProgramValue == null) {
				if (interval > 0) {
					timer.setTimingInterval(interval);
					timer.resume();
				}
				return;
			}
			final long t1 = nextProgramValue.getTimestamp();
			final SampledValue nextToNextProgramValue = program.getNextValue(t1 + 1);
			if (nextToNextProgramValue == null) {
				if (interval > 0) {
					timer.setTimingInterval(interval);
					timer.resume();
				}
				return;
			}
			final long t2 = nextToNextProgramValue.getTimestamp();
			final long tMid = ((t1 + t2) / 2) + ((t1 + t2) % 2);
			interval = interval > 0 ? Math.min(interval, tMid - t0) : tMid - t0;
			timer.setTimingInterval(interval);
			timer.resume();
			return;
		}
		case NONE: {
			final String resLocation = target.getLocation();
			logger.warn("Resource at " + resLocation
					+ " has been configured for automatic program enforcement, but interpolation mode "
					+ interpolation.toString() + " is not suitable. Will ignore this.");
			break;
		}
		default:
			throw new UnsupportedOperationException("Encountered unknown/unexpected interpolation mode "
					+ interpolation);
		}
	}

	@Override
	public void resourceChanged(Schedule resource) {
		timerElapsed(timer);
	}

	/**
	 * Structure of the schedule has been changed.
	 *
	 * @param eventType
	 */
	@SuppressWarnings("fallthrough")
	private void scheduleStructureChanged(ResourceStructureEvent.EventType eventType) {
		switch (eventType) { // react to changes that may effect the target resource.
		case RESOURCE_ACTIVATED:
		case RESOURCE_DEACTIVATED:
		case RESOURCE_CREATED:
		case RESOURCE_DELETED:
			timerElapsed(timer);
		default: // no need to react to anything else.
		}
	}

	/**
	 * Structure of the target resource has been changed.
	 */
	@SuppressWarnings("fallthrough")
	private void targetStructureChanged(ResourceStructureEvent.EventType eventType) {
		switch (eventType) { // react to changes that may effect the target resource.
		case RESOURCE_CREATED:
		case RESOURCE_DELETED:
			timerElapsed(timer);
		default: // no need to react to anything else (activation and de-activation likely caused by myself, anyways).
		}
	}

	@Override
	public void resourceStructureChanged(ResourceStructureEvent event) {
		if (event.getSource().equalsLocation(program)) {
			scheduleStructureChanged(event.getType());
			return;
		}
		if (event.getSource().equalsLocation(target)) {
			targetStructureChanged(event.getType());
			return;
		}
		logger.error("Got structure event for resource at " + event.getSource().getLocation()
				+ " which is not listened to. Ignoring the callback.");
	}

	/**
	 * Listener invoked whenever the required access mode to the target resource
	 * changes.
	 */
	@Override
	public void accessModeChanged(Resource resource) {
		final boolean previousAccess = requiredWriteAccessGranted;

		final AccessMode access = resource.getAccessMode();
		if (exclusiveAccess) {
			requiredWriteAccessGranted = access == AccessMode.EXCLUSIVE;
		}
		else {
			requiredWriteAccessGranted = (access == AccessMode.EXCLUSIVE) || (access == AccessMode.SHARED);
		}

		// Re-calculate everything relevant if the effective access mode changed.
		if (requiredWriteAccessGranted != previousAccess)
			timerElapsed(timer);
	}

	/**
	 * Sets the value of the target to the program's value if the program value
	 * differs from the current value. Checks for the program quality and the
	 * activity of the schedule are assumed to have passed successfully at this
	 * point.
	 */
	private void setProgramValue(SampledValue newValue, Filter filter) {
		if (resourceType == BooleanResource.class) {
			final BooleanResource resource = (BooleanResource) target;
			final boolean currentValue = resource.getValue();
			final boolean targetValue = newValue.getValue().getBooleanValue();
			if (currentValue != targetValue) {
				resource.setValue(targetValue);
			}
		}
		else if (resourceType == IntegerResource.class) {
			final IntegerResource resource = (IntegerResource) target;
			final int currentValue = resource.getValue();
			final int targetValue = newValue.getValue().getIntegerValue();
			if (!handleFilter(targetValue, filter))
				return;
			if (currentValue != targetValue) {
				resource.setValue(targetValue);
			}
		}
		else if (resourceType == TimeResource.class) {
			final TimeResource resource = (TimeResource) target;
			final long currentValue = resource.getValue();
			final long targetValue = newValue.getValue().getLongValue();
			if (!handleFilter(targetValue, filter))
				return;
			if (currentValue != targetValue) {
				resource.setValue(targetValue);
			}
		}
		else if (resourceType == FloatResource.class) {
			final FloatResource resource = (FloatResource) target;
			final float currentValue = resource.getValue();
			final float targetValue = newValue.getValue().getFloatValue();
			if (!handleFilter(targetValue, filter))
				return;
			if (currentValue != targetValue) {
				resource.setValue(targetValue);
			}
		}
		else if (resourceType == StringResource.class) {
			final StringResource resource = (StringResource) target;
			final String currentValue = resource.getValue();
			final String targetValue = newValue.getValue().getStringValue();
			if (currentValue != targetValue) {
				resource.setValue(targetValue);
			}
			
		}
		else {
			throw new UnsupportedOperationException("Cannot set the value for unsupported resource type "
					+ resourceType.getCanonicalName()
					+ ". You should never see this message. Please report this to the OGEMA developers.");
		}
		target.activate(false);
		lastExecutionTime = appMan.getFrameworkTime();
	}

	/**
	 * returns true iff value of target resource remains to be set
	 */
	@SuppressWarnings("fallthrough")
	private boolean handleFilter(float targetValue, Filter filter) {
		if (filterSatisfied(targetValue, filter)) {
			return true;
		}
		int mode = 0;
		if (filter.mode().isActive())
			mode = filter.mode().getValue();
		switch (mode) {
		case 0:
			target.deactivate(false);
		default: // includes case 1
			return false;
		}
	}

	private boolean filterSatisfied(float value, Filter filter) {
		if (filter == null || !filter.isActive())
			return true;
		if (filter instanceof RangeFilter) {
			RangeFilter rfilter = (RangeFilter) filter;
			FloatResource ll = rfilter.range().lowerLimit();
			FloatResource ul = rfilter.range().upperLimit();
			if ((ll.isActive() && ll.getValue() > value) || (ul.isActive() && ul.getValue() < value)) {
				logger.debug("Filter not satisfied for resource {}. Boundaries: {} - {}, actual value: {}", target, ll
						.getValue(), ul.getValue(), value);
				return false;
			}
		}
		return true;
	}
	
	@Override
	public ResourceManipulatorModel getConfigurationResource() {
		return config;
	}
	
	@Override
	public Long getLastExecutionTime() {
		return lastExecutionTime;
	}

	@Override
	public String toString() {
		return "ProgramEnforcerController for target " + target.getLocation() + ", configuration " + getConfigurationResource().getName();
	}
	
}
