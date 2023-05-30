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
package org.ogema.resourcemanager.impl.transaction.actions;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;
import java.util.Queue;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.channelmanager.measurements.SampledValue;

import org.ogema.core.model.Resource;
import org.ogema.core.model.ValueResource;
import org.ogema.core.model.array.ArrayResource;
import org.ogema.core.model.array.BooleanArrayResource;
import org.ogema.core.model.array.ByteArrayResource;
import org.ogema.core.model.array.FloatArrayResource;
import org.ogema.core.model.array.IntegerArrayResource;
import org.ogema.core.model.array.StringArrayResource;
import org.ogema.core.model.array.TimeArrayResource;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.resourcemanager.ResourceOperationException.Type;
import org.ogema.core.resourcemanager.VirtualResourceException;
import org.ogema.core.resourcemanager.transaction.WriteConfiguration;
import org.ogema.resourcemanager.impl.transaction.AtomicAction;
import org.ogema.tools.resource.util.ValueResourceUtils;

/**
 * Write a value to a resource.
 * Write actions are composite actions in general (i.e. they have subactions),
 * since they may include creation and activation of the respective resource
 * (depending on the WriteConfiguration). 
 *
 * @param <T>
 * @param <V>
 */
public class ResourceWriteAction<T, V extends ValueResource> implements AtomicAction {
	
	private final V resource;
	// initial value is null, but null may also be returned, if the resource is virtual 
	// and the read operataion is configured to ignore this
	private final T value;
	private T oldValue = null; // for rollback
	private boolean doWrite = true;
	private final WriteConfiguration config;
	private boolean done = false;
	private boolean setBack = false;
	private final Queue<AtomicAction> subactions = new ArrayDeque<>();  // creation, activation
	private final Deque<AtomicAction> subactionsDone = new ArrayDeque<>();
	private final ApplicationManager appman;
	private final long timestamp;
	
	public ResourceWriteAction(V resource, T value, WriteConfiguration config, long timestamp, ApplicationManager appman) {
		Objects.requireNonNull(resource);
		Objects.requireNonNull(value);
		Objects.requireNonNull(config);
		this.resource = resource;
		this.config = config;
		this.value = value;
		this.timestamp = timestamp;
		this.appman = appman;
	}
	
	@Override
	public boolean requiresCommitWriteLock() {
		return true;
	}
	
	@Override
	public boolean requiresStructureWriteLock() {
		return true; // XXX ?
	}
	
	@SuppressWarnings("unchecked")
	protected T read(V resource) {
		return (T) ValueResourceUtils.getValue(resource);
	}
	
	@SuppressWarnings("deprecation")
	protected void write(V resource, T value) {
		//ValueResourceUtils.setValue(resource, value);
		long ts = timestamp == -1 ? appman.getFrameworkTime() : timestamp;
		if (resource instanceof ArrayResource) {
			if (resource instanceof BooleanArrayResource) {
				((BooleanArrayResource) resource).setValues((boolean[]) value, ts);
			} else if (resource instanceof ByteArrayResource) {
				((ByteArrayResource) resource).setValues((byte[]) value, ts);
			} else if (resource instanceof FloatArrayResource) {
				((FloatArrayResource) resource).setValues((float[]) value, ts);
			} else if (resource instanceof IntegerArrayResource) {
				((IntegerArrayResource) resource).setValues((int[]) value, ts);
			} else if (resource instanceof StringArrayResource) {
				((StringArrayResource) resource).setValues((String[]) value, ts);
			} else if (resource instanceof TimeArrayResource) {
				((TimeArrayResource) resource).setValues((long[]) value, ts);
			} else {
				throw new RuntimeException("unsupported array resource type: " + resource);
			}
		} else if (resource instanceof BooleanResource) {
			((BooleanResource) resource).setValue((boolean) value, ts);
		} else if (resource instanceof FloatResource) {
			((FloatResource) resource).setValue((float) value, ts);
		} else if (resource instanceof IntegerResource) {
			((IntegerResource) resource).setValue((int) value, ts);
		} else if (resource instanceof org.ogema.core.model.simple.OpaqueResource) {
			((org.ogema.core.model.simple.OpaqueResource) resource).setValue((byte[]) value, ts);
		} else if (resource instanceof StringResource) {
			((StringResource) resource).setValue((String) value, ts);
		} else if (resource instanceof TimeResource) {
			((TimeResource) resource).setValue((long) value, ts);
		} else if (resource instanceof Schedule) {
			((Schedule) resource).deleteValues();
			((Schedule) resource).addValues((Collection<SampledValue>)value);
		}
	}
	
	/**
	 * For most actions this is simply the same as writing, but there 
	 * are exception, such as some schedules write operations.
	 * @param resource
	 * @param value
	 */
	protected void undo(V resource, T value) {
		write(resource,value);
	}
	
	// only one subaction possible, which may itself be composite, however
    @SuppressWarnings("fallthrough")
	private void buildActionsTree() {
		boolean activate = false;
		boolean create = false;
		switch (config) {
		case CREATE_AND_ACTIVATE:
			create = true;
		case ACTIVATE:
			activate = true;
			break;
		default:
		}
		if (activate)
			subactions.add(new ActivationAction(resource, true, false, create));
	}
	
	private void executeSubActions() throws Exception {
		AtomicAction action;
		while ((action = subactions.poll()) != null) {
			subactionsDone.add(action); // add this immediately, so we'll try to rollback this action even if it fails
			action.execute();
		}
	}
	
	private void rollbackSubactions() throws IllegalStateException {
		AtomicAction action;
		while ((action = subactionsDone.pollLast()) != null) {
			try {
				action.rollback();
			} catch (Exception e) {
				continue;
			}
		}
	}
	
	@Override
	public void execute() throws Exception {
		if (done || setBack)
			throw new IllegalStateException("Transaction has been executed already");
		done = true; // we must set this immediately, so if an exception occurs, we can still rollback the action
		buildActionsTree(); // we cannot do this earlier, e.g. in the constructor, since at that time no resource lock is held
		executeSubActions();
		if (config == WriteConfiguration.FAIL && !resource.isActive())
			throw new VirtualResourceException("Resource " + resource + " found virtual");
		if (!resource.exists()) {
			if (config == WriteConfiguration.IGNORE || config == WriteConfiguration.ACTIVATE)
				doWrite = false;
		}
		oldValue = read(resource);
		if (doWrite)
			write(resource, value);
	}

	// FIXME we need to prevent listener callbacks in this case!
	@Override
	public void rollback() throws IllegalStateException {
		if (!done)
			throw new IllegalStateException("Transaction has not been executed yet, cannot set back");
		if (setBack)
			throw new IllegalStateException("Transaction has been rolled back already");
		setBack =true;
		if (doWrite && oldValue != null && !value.equals(oldValue) && resource.exists())
			undo(resource, oldValue);
		rollbackSubactions();
	}
	
	@Override
	public Type getType() {
		return Type.WRITE;
	}
	
	@Override
	public Resource getSource() {
		return resource;
	}
	
}
