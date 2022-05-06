package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import static org.ogema.drivers.homematic.xmlrpc.hl.channels.IpThermostatBChannel.CONTROL_MODE_DECORATOR;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.actors.MultiSwitch;
import org.ogema.model.devices.buildingtechnology.Thermostat;
import org.ogema.model.devices.buildingtechnology.ThermostatProgram;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.slf4j.Logger;

/**
 *
 * @author jlapp
 */
abstract class ThermostatUtils {

	private final static Map<String, Class<? extends SingleValueResource>> PARAMETERS;

	static {
		PARAMETERS = new LinkedHashMap<>();
		PARAMETERS.put("TEMPERATUREFALL_MODUS", IntegerResource.class);
		PARAMETERS.put("TEMPERATUREFALL_WINDOW_OPEN_TIME_PERIOD", IntegerResource.class);
		PARAMETERS.put("TEMPERATURE_WINDOW_OPEN", FloatResource.class);
		PARAMETERS.put("VALVE_MAXIMUM_POSITION", FloatResource.class);
	}

	private ThermostatUtils() {
	}

	static class ParameterListener implements ResourceValueListener<SingleValueResource> {

		final HomeMaticConnection conn;
		final String address;
		final String set;
		//final String param;
		final Logger logger;

		public ParameterListener(HomeMaticConnection conn, String address, String set, Logger logger) {
			this.conn = conn;
			this.address = address;
			this.set = set;
			this.logger = logger;
		}

		@Override
		public void resourceChanged(SingleValueResource resource) {
			String paramName = resource.getName();
			Object resourceValue = null;
			if (resource instanceof IntegerResource) {
				resourceValue = ((IntegerResource) resource).getValue();
			} else if (resource instanceof BooleanResource) {
				resourceValue = ((BooleanResource) resource).getValue();
			} else if (resource instanceof FloatResource) {
				resourceValue = ((FloatResource) resource).getValue();
			} else {
				logger.warn("unsupported parameter type: " + resource);
			}
			Map<String, Object> parameterSet = new HashMap<>();
			parameterSet.put(paramName, resourceValue);
			conn.performPutParamset(address, "MASTER", parameterSet);
			logger.info("Parameter set 'MASTER' updated for {}: {}", address, parameterSet);
		}

	};

	/*
	support for:
		VALVE_ERROR_RUN_POSITION
	 */
	static void setupThermostatDecorators(HmDevice parent, DeviceDescription desc,
			Map<String, Map<String, ParameterDescription<?>>> paramSets,
			HomeMaticConnection conn, Thermostat model, Logger logger) {
		Map<String, ParameterDescription<?>> masterParams
				= paramSets.get(ParameterDescription.SET_TYPES.MASTER.name());
		if (masterParams == null) {
			logger.debug("no MASTER params for {}", desc.getAddress());
			return;
		}
		if (masterParams.containsKey("VALVE_ERROR_RUN_POSITION")) {
			logger.debug("setup VALVE_ERROR_RUN_POSITION decorator on {}", model.valve().getPath());
			model.valve().create().activate(false);
			MultiSwitch errorRunPosition = model.valve().getSubResource("errorRunPosition", MultiSwitch.class);
			errorRunPosition.stateControl().create().activate(false);
			errorRunPosition.activate(false);
			Runnable readValue = () -> {
				try {
					//double val = conn.getValue(desc.getAddress(), "VALVE_ERROR_RUN_POSITION");
					Double d = (Double) conn.getParamset(desc.getAddress(), "MASTER").get("VALVE_ERROR_RUN_POSITION");
					if (d == null) {
						return;
					}
					errorRunPosition.stateFeedback().create();
					errorRunPosition.stateFeedback().setValue(d.floatValue());
					errorRunPosition.stateFeedback().activate(false);
					logger.debug("read VALVE_ERROR_RUN_POSITION@{}: {}", desc.getAddress(), d);
				} catch (IOException | RuntimeException ex) {
					logger.warn("could not read VALVE_ERROR_RUN_POSITION from {}: {}", desc.getAddress(), ex.getMessage());
				}
			};
			errorRunPosition.stateControl().addValueListener((FloatResource f) -> {
				logger.debug("setting VALVE_ERROR_RUN_POSITION@{} = {}", desc.getAddress(), f.getValue());
				conn.performPutParamset(desc.getAddress(), "MASTER",
						Collections.singletonMap("VALVE_ERROR_RUN_POSITION", (double) f.getValue()));
				CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(readValue);
			}, true);
			readValue.run();
		}
	}

	static void setupParameterResources(HmDevice parent, DeviceDescription desc,
			Map<String, Map<String, ParameterDescription<?>>> paramSets,
			HomeMaticConnection conn, Resource model, Logger logger) {
		final String address = desc.getAddress();
		Map<String, SingleValueResource> params = new LinkedHashMap<>();
		ParameterDescription.SET_TYPES set = ParameterDescription.SET_TYPES.MASTER;
		Map<String, ParameterDescription<?>> allParams = paramSets.get(set.name());
		if (allParams == null) {
			logger.debug("no parameter set '{}' for {}", set, parent.address());
			return;
		}
		@SuppressWarnings("unchecked")
		ResourceList<SingleValueResource> paramList = model.addDecorator("HmParametersMaster", ResourceList.class);
		if (!paramList.exists()) {
			paramList.setElementType(SingleValueResource.class);
			paramList.create();
		}
		ParameterListener l = new ParameterListener(conn, address, set.name(), logger);
		Runnable updateValues = () -> {
			Thread.currentThread().setName("HomeMatic Thermostats Parameter Update");
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
					SingleValueResource fb = paramList.getSubResource(fbName, PARAMETERS.get(paramName));
					fb.create();
					ValueResourceUtils.setValue(fb, value);
					fb.activate(false);
					count++;
				}
				logger.debug("{} parameters updated on {}", count, paramList);
			} catch (IOException | RuntimeException ex) {
				logger.debug("updating parameter values failed for {}: {}", address, ex.getMessage());
			}
		};
		ResourceValueListener<BooleanResource> updateListener = (BooleanResource b) -> {
			if (!b.getValue()) {
				return;
			}
			CompletableFuture.runAsync(updateValues);
		};
		PARAMETERS.forEach((p, t) -> {
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

	static void setupProgramListener(String address, HomeMaticConnection conn,
			Resource model, Logger logger) {
		//XXX might need aditional program resources or maybe a StructureListener for new HmThermostatPrograms
		ThermostatProgram prog = model.getSubResource("program", ThermostatProgram.class);
		prog.update().addValueListener(_res -> {
			logger.debug("program update triggered for {} / {}", model.getPath(), address);
			transmitProgram(conn, address, prog, logger);
		}, true);
	}

	static float toCelsius(float k) {
		return k - 273.15f;
	}

	static void transmitProgram(HomeMaticConnection conn, String address, ThermostatProgram prg, Logger logger) {
		int updateVal = prg.update().getValue();
		int prgNum = prg.programNumber().isActive()
				? prg.programNumber().getValue()
				: 1;
		Map<String, Object> programParams = new HashMap<>();
		String ENDTIME_PATTERN = "P%d_ENDTIME_%s_%d";
		String TEMPERATURE_PATTERN = "P%d_TEMPERATURE_%s_%d";
		for (DayOfWeek day : DayOfWeek.values()) {
			String dayString = day.name().toUpperCase();
			// DayOfWeek values are 1 (Monday) ... 7 (Sunday)
			if ((updateVal & (1 << day.getValue() - 1)) != 0) {
				Optional<int[]> endTimes = prg.endTimesDay(day);
				endTimes.ifPresent(timesArray -> {
					Optional<float[]> temperatures = prg.temperaturesDay(day);
					temperatures.ifPresent(tempArray -> {
						if (tempArray.length != timesArray.length) {
							logger.debug("skipping {}/{}, temperatures array has different size!", prg.getPath(), day);
							return;
						}
						for (int i = 0; i < timesArray.length; i++) {
							int t = timesArray[i];
							String paramNameTime = String.format(ENDTIME_PATTERN, prgNum, dayString, i + 1);
							String paramNameTemp = String.format(TEMPERATURE_PATTERN, prgNum, dayString, i + 1);
							programParams.put(paramNameTime, t);
							programParams.put(paramNameTemp, toCelsius(tempArray[i]));
							if (t >= 1440) {
								break;
							}
						}
					});
				});
			}
		}
		if (programParams.isEmpty()) {
			logger.debug("built empty program for {}, update value {}", prg.getPath(), updateVal);
		} else {
			logger.debug("built program parameters for {}: {}", prg.getPath(), programParams);
			logger.debug("transmitting new program to {}", address);
			conn.performPutParamset(address, "MASTER", programParams);
		}
	}

	static void setupControlModeResource(Thermostat thermos, HomeMaticConnection conn, final String deviceAddress) {
		IntegerResource controlMode = thermos.addDecorator(CONTROL_MODE_DECORATOR, IntegerResource.class);
		controlMode.create().activate(false);
		controlMode.addValueListener(new ResourceValueListener<IntegerResource>() {
			@Override
			public void resourceChanged(IntegerResource resource) {
				Map<String, Object> params = new HashMap<>();
				// 0: automatic, 1: manual
				// cannot be read, but will be available as VALUES/SET_POINT_MODE
				params.put("CONTROL_MODE", resource.getValue());
				params.put("SET_POINT_TEMPERATURE", thermos.temperatureSensor().settings().setpoint().getCelsius());
				conn.performPutParamset(deviceAddress, "VALUES", params);
			}
		}, true);
	}

}
