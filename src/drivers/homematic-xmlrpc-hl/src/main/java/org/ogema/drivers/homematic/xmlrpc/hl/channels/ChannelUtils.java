package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription.SET_TYPES;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.slf4j.Logger;

/**
 *
 * @author jlapp
 */
public class ChannelUtils {
	
	public static void setupParameterResources(HmDevice parent, DeviceDescription desc,
			Map<String, Map<String, ParameterDescription<?>>> paramSets,
			SET_TYPES set, Map<String, Class<? extends SingleValueResource>> parameters,
			HomeMaticConnection conn, Resource model, Logger logger) {
		final String address = desc.getAddress();
		Map<String, SingleValueResource> params = new LinkedHashMap<>();
		//ParameterDescription.SET_TYPES set;
		Map<String, ParameterDescription<?>> allParams = paramSets.get(set.name());
		if (allParams == null) {
			logger.debug("no parameter set '{}' for {}", set, parent.address());
			return;
		}
		String decName;
		switch (set) {
			case LINK: decName = "HmParametersLink"; break;
			case MASTER: decName = "HmParametersMaster"; break;
			case VALUES: decName = "HmParametersValues"; break;
			default: decName = "HmParameters" + set.name(); break;
		}
		@SuppressWarnings("unchecked")
		ResourceList<SingleValueResource> paramList = model.addDecorator(decName, ResourceList.class);
		if (!paramList.exists()) {
			paramList.setElementType(SingleValueResource.class);
			paramList.create();
		}
		ThermostatUtils.ParameterListener l = new ThermostatUtils.ParameterListener(conn, address, set.name(), logger);
		Runnable updateValues = () -> {
			String oldThreadName = Thread.currentThread().getName();
			Thread.currentThread().setName("HMpr " + desc.getAddress());
			try {
				Map<String, Object> values = conn.getParamset(address, set.name());
				int count = 0;
				for (String paramName : params.keySet()) {
					Object value = values.get(paramName);
					if (value == null) {
						logger.debug("missing value for {} in getParamset response on {}", paramName, address);
						continue;
					}
					String fbName = paramName + "_FEEDBACK";
					SingleValueResource fb = paramList.getSubResource(fbName, parameters.get(paramName));
					fb.create();
					ValueResourceUtils.setValue(fb, value);
					fb.activate(false);
					count++;
				}
				logger.debug("{} parameters updated on {}", count, paramList);
			} catch (IOException | RuntimeException ex) {
				logger.debug("updating parameter values failed for {}: {}", address, ex.getMessage());
			} finally {
				Thread.currentThread().setName(oldThreadName);
			}
		};
		ResourceValueListener<BooleanResource> updateListener = (BooleanResource b) -> {
			if (!b.getValue()) {
				return;
			}
			CompletableFuture.runAsync(updateValues);
		};
		parameters.forEach((p, t) -> {
			if (allParams.containsKey(p)) {
				SingleValueResource r = paramList.getSubResource(p, t);
				if (!r.exists()) {
					r.create();
					try {
						ValueResourceUtils.setValue(r, allParams.get(p).getDefault());
					} catch (RuntimeException re) {
						logger.debug("failed to set default for {} on {}: {}", p, address, re.getMessage());
					}
				}
				r.activate(false);
				r.addValueListener(l, true);
				r.addValueListener(_r -> {
					Executors.newSingleThreadScheduledExecutor().schedule(updateValues, 3, TimeUnit.SECONDS);
				}, true);
				params.put(p, r);
				logger.debug("set up parameter {} on {}", p, address);
			}
		});
		BooleanResource update = paramList.getSubResource("update", BooleanResource.class).create();
		update.create();
		update.addValueListener(updateListener, true);
		update.activate(false);
		CompletableFuture.runAsync(updateValues);
	}

	
}
