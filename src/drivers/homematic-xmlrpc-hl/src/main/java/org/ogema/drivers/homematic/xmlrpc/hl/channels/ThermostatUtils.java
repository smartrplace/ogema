package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import static org.ogema.drivers.homematic.xmlrpc.hl.channels.IpThermostatBChannel.CONTROL_MODE_DECORATOR;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.model.units.TemperatureResource;
import org.ogema.core.resourcemanager.ResourceStructureEvent;
import org.ogema.core.resourcemanager.ResourceStructureListener;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.actors.MultiSwitch;
import org.ogema.model.devices.buildingtechnology.Thermostat;
import org.ogema.model.devices.buildingtechnology.ThermostatProgram;
import org.ogema.model.sensors.DoorWindowSensor;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.slf4j.Logger;

/**
 *
 * @author jlapp
 */
public abstract class ThermostatUtils {
	
	public final static String SHUTTER_CONTACT_DECORATOR = "linkedShutterContact";
	//public final static String SHUTTER_CONTACT_LIST_DECORATOR = "linkedShutterContacts";
	
	static final int THREADS_PER_CONNECTION = 16;
	static final Map<HomeMaticConnection, ScheduledExecutorService> PARAMETER_UPDATES_EXECUTORS = new ConcurrentHashMap<>();

	private final static Map<String, Class<? extends SingleValueResource>> PARAMETERS;

	static {
		PARAMETERS = new LinkedHashMap<>();
		PARAMETERS.put("TEMPERATUREFALL_MODUS", IntegerResource.class);
		PARAMETERS.put("TEMPERATUREFALL_VALUE", TemperatureResource.class);
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
			Object resourceValue = getResourceValue(resource, logger);
			Map<String, Object> parameterSet = new HashMap<>();
			parameterSet.put(paramName, resourceValue);
			conn.performPutParamset(address, set, parameterSet);
			logger.info("Parameter set '{}' updated for {}: {}", set, address, parameterSet);
		}
		
		static Object getResourceValue(SingleValueResource resource, Logger logger) {
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
			return resourceValue;
		}

	};

	private static void setupDecalcDecorators(HmDevice parent, DeviceDescription desc,
			Map<String, Map<String, ParameterDescription<?>>> paramSets,
			HomeMaticConnection conn, Thermostat model, Logger logger, Map<String, Object> masterValues) {
		Map<String, ParameterDescription<?>> masterParams
				= paramSets.get(ParameterDescription.SET_TYPES.MASTER.name());
		if (masterParams == null || !masterParams.containsKey("DECALCIFICATION_WEEKDAY")) {
			logger.debug("no DECALCIFICATION params for {}", desc.getAddress());
			return;
		}
		logger.debug("setup DECALCIFICATION decorator on {}", model.valve().getPath());
		model.valve().create().activate(false);
		StringResource setting = model.valve().getSubResource("DECALCIFICATION", StringResource.class);
		StringResource feedback = model.valve().getSubResource("DECALCIFICATION_FEEDBACK", StringResource.class);
		setting.create().activate(false);
		feedback.create().activate(false);
		Function<Map<String, Object>, String> decalcStringFromParams = masterValueReadings -> {
			//note: HM weekday enum is Sunday(0) ... Saturday(6)
			Integer weekday = (Integer) masterValueReadings.get("DECALCIFICATION_WEEKDAY");
			Integer halfhour = (Integer) masterValueReadings.get("DECALCIFICATION_TIME");
			if (weekday == null || halfhour == null) {
				return null;
			}
			DayOfWeek dow = weekday == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(weekday);
			LocalTime time = LocalTime.ofSecondOfDay(halfhour * 30 * 60);
			String fb = dow + " " + time;
			return fb;
		};
		Runnable readValue = () -> {
			try {
				Map<String, Object> masterValueReadings = conn.getParamset(desc.getAddress(), "MASTER");
				String fb = decalcStringFromParams.apply(masterValueReadings);
				if (fb == null) {
					return;
				}
				feedback.create();
				feedback.setValue(fb);
				feedback.activate(false);
				logger.debug("read DECALCIFICATION@{}: {}", desc.getAddress(), fb);
			} catch (IOException | RuntimeException ex) {
				logger.warn("could not read VALVE_ERROR_RUN_POSITION from {}: {}", desc.getAddress(), ex.getMessage());
			}
		};
		setting.addValueListener((StringResource v) -> {
			String s = v.getValue();
			try {
				String[] a = s.split(" ");
				DayOfWeek dow = DayOfWeek.valueOf(a[0]);
				LocalTime t = LocalTime.parse(a[1]);
				int hmWeekDay = dow.getValue() % 7; //DayOfWeek is Mon(1)...Sun(7) (ISO-8601)
				int hmHalfHour = t.toSecondOfDay() / 60 / 30;
				Map<String, Object> values = new HashMap<>();
				values.put("DECALCIFICATION_TIME", hmHalfHour);
				values.put("DECALCIFICATION_WEEKDAY", hmWeekDay);
				conn.performPutParamset(desc.getAddress(), "MASTER", values);
				logger.debug("setting decalcification time for {}: {}={}", desc.getAddress(), s, values);
				CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(readValue);
			} catch (RuntimeException re) {
				logger.warn("illegal value (or bug) on {}: {} ({})", setting.getPath(), s, re.getMessage());
			}
		}, true);
	}

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
		setupDecalcDecorators(parent, desc, paramSets, conn, model, logger, null);
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
	
	static void sendParameters(String address, ParameterDescription.SET_TYPES set,
			Map<String, ParameterDescription<?>> allParams, Collection<SingleValueResource> resources,
			HomeMaticConnection conn, Logger logger) {
		Map<String, Object> values = new HashMap<>();
		resources.forEach(svr -> {
			ParameterDescription<?> desc = allParams.get(svr.getName());
			if (desc != null && desc.isWritable()) {
				values.put(svr.getName(), ParameterListener.getResourceValue(svr, logger));
			}
		});
		if (!values.isEmpty()) {
			logger.debug("sending parameters for {}/{}: {}", address, set, values);
			conn.performPutParamset(address, set.name(), values);
		} else {
			logger.debug("no parameters set on for {}/{}", address, set);
		}
	}
	
	static Map<Long, Future<?>> updateTasks = new ConcurrentSkipListMap<>();
	
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
			//exec.execute(updateValues);
			long now = System.currentTimeMillis();
			Iterator<Entry<Long,Future<?>>> it = updateTasks.entrySet().iterator();
			while (it.hasNext()) {
				Entry<Long,Future<?>> e = it.next();
				if (e.getValue().isDone()) {
					it.remove();
				}
				else if (now - e.getKey() > 60_000) {
					logger.warn("cancelling stalled update task for {}", model.getPath());
					e.getValue().cancel(true);
					it.remove();
				} else if (now - e.getKey() < 10_000) {
					break;
				}
			}
			Future<?> f = PARAMETER_UPDATES_EXECUTORS
					.computeIfAbsent(conn, _c -> Executors.newScheduledThreadPool(THREADS_PER_CONNECTION))
					.submit(updateValues);
			updateTasks.put(now, f);
			logger.trace("number of pending parameter updates (all thermostat types): {}", updateTasks.size());
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
					PARAMETER_UPDATES_EXECUTORS
					.computeIfAbsent(conn, _c -> Executors.newScheduledThreadPool(THREADS_PER_CONNECTION))
					.schedule(updateValues, 3, TimeUnit.SECONDS);
				}, true);
				params.put(p, r);
				logger.debug("set up parameter {} on {}", p, address);
			}
		});
		BooleanResource update = paramList.getSubResource("update", BooleanResource.class).create();
		update.create();
		update.addValueListener(updateListener, true);
		update.activate(false);
		PARAMETER_UPDATES_EXECUTORS
				.computeIfAbsent(conn, _c -> Executors.newScheduledThreadPool(THREADS_PER_CONNECTION))
				.submit(updateValues);
		//CompletableFuture.runAsync(updateValues);
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
	
	public static List<DayOfWeek> compareProgram(HomeMaticConnection conn, String address, ThermostatProgram prg, Logger logger) throws IOException {
		int updateVal = prg.update().getValue();
		int prgNum = prg.programNumber().isActive()
				? prg.programNumber().getValue()
				: 1;
		String ENDTIME_PATTERN = "P%d_ENDTIME_%s_%d";
		String TEMPERATURE_PATTERN = "P%d_TEMPERATURE_%s_%d";
		Map<String, Object> masterValues = null;// = new HashMap<>();
		List<DayOfWeek> daysWithErrors = new ArrayList<>();
		for (DayOfWeek day : DayOfWeek.values()) {
			String dayString = day.name().toUpperCase();
			// DayOfWeek values are 1 (Monday) ... 7 (Sunday)
			if ((updateVal & (1 << day.getValue() - 1)) != 0) {
				Optional<int[]> endTimes = prg.endTimesDay(day);
				if (!endTimes.isPresent()) {
					continue;
				}
				int[] timesArray = endTimes.get();
				Optional<float[]> temperatures = prg.temperaturesDay(day);
				if (!temperatures.isPresent() || temperatures.get().length != timesArray.length) {
					logger.debug("skipping {}/{}, temperatures array has different size!", prg.getPath(), day);
					continue;
				}
				float[] tempArray = temperatures.get();
				if (masterValues == null) {
					masterValues = conn.getParamset(address, "MASTER");
				}
				for (int i = 0; i < timesArray.length; i++) {
					int t = timesArray[i];
					String paramNameTime = String.format(ENDTIME_PATTERN, prgNum, dayString, i + 1);
					String paramNameTemp = String.format(TEMPERATURE_PATTERN, prgNum, dayString, i + 1);
					float tc = toCelsius(tempArray[i]);
					int mvTime = ((Number) masterValues.get(paramNameTime)).intValue();
					float mvTemp = ((Number) masterValues.get(paramNameTemp)).floatValue();
					if (t != mvTime || tc != mvTemp) {
						logger.debug("setting differ for {}, {}: ({}, {}) != ({}, {})",
								address, paramNameTime,
								t, tc, mvTime, mvTemp);
						daysWithErrors.add(day);
						break;
					}
				}
			}
		}
		if (daysWithErrors.isEmpty()) {
			logger.info("program setting transmitted correctly for {}", address);
			return daysWithErrors;
		}
		logger.warn("program settings differ for {} on days {}", address, daysWithErrors);
		return daysWithErrors;
	}

	public static void transmitProgram(HomeMaticConnection conn, String address, ThermostatProgram prg, Logger logger) {
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

	/* DoorWindowSensors that are linked on the thermostat as sub resource SHUTTER_CONTACT_DECORATOR
	 * or as sub resource on the resource called SHUTTER_CONTACT_LIST_DECORATOR
	 * are linked as shutter contacts (window open/close sensors) in Homematic.
	 */
    static void setupShutterContactLinking(final Thermostat thermos, HomeMaticConnection conn, Logger logger) {
		final String senderChannelType = "SHUTTER_CONTACT";
		final String receiverChannelType = "HEATING_SHUTTER_CONTACT_RECEIVER";
		//final Class<? extends Resource> decoratorType = DoorWindowSensor.class;
        DoorWindowSensor shutterContact = thermos.getSubResource(SHUTTER_CONTACT_DECORATOR, DoorWindowSensor.class);
        
        ResourceStructureListener l = new ResourceStructureListener() {

            @Override
            public void resourceStructureChanged(ResourceStructureEvent event) {
				logger.info(event.toString());
                Resource added = event.getChangedResource();
                if (event.getType() == ResourceStructureEvent.EventType.SUBRESOURCE_ADDED) {
                    if (added instanceof DoorWindowSensor) {
                        DeviceHandlers.linkChannels(conn, added, senderChannelType,
                                thermos, receiverChannelType, logger,
                                "Shutter Contact", "Window open sensor / thermostat link", false);
                    }
                } else if (event.getType() == ResourceStructureEvent.EventType.SUBRESOURCE_REMOVED
                		&& (added instanceof DoorWindowSensor)) {
                	// since we do not know which resource the link referenced before it got deleted
                	// we need to use the low level API to find out all links for the weather receiver channel
                    Optional<HmDevice> recChan = DeviceHandlers.findDeviceChannel(
                            conn, thermos, receiverChannelType, logger);
                    if (!recChan.isPresent()) {
                    	return;
                    }
                    String receiverChannelAddress = recChan.get().address().getValue();
                	for (Map<String, Object> link : conn.performGetLinks(receiverChannelAddress, 0)) {
                		if (!receiverChannelAddress.equals(link.get("RECEIVER"))) {
                			continue;
						}
                		final Object sender = link.get("SENDER");
                		if (!(sender instanceof String)) {
                			continue;
						}
                		conn.performRemoveLink((String) sender, receiverChannelAddress);
                		logger.info("Thermostat / shutter contact connection removed. Thermostat channel {}, shutter contact sensor {}",
                				receiverChannelAddress, sender);
                	}
                }
            }
        };
        thermos.addStructureListener(l);
        if (shutterContact.isActive()) {
            DeviceHandlers.linkChannels(conn, shutterContact, senderChannelType,
                    thermos, receiverChannelType, logger,
                    "Shutter Contact", "Window open sensor / thermostat link", false);
        }

	}

	/* call after temperature setpoint feedback was received and stored. */
	static void checkForAdaptionFailure(HomeMaticConnection conn, Thermostat th, String address, Logger logger) {
		long lastSetpointFb = th.temperatureSensor().deviceFeedback().setpoint().getLastUpdateTime();
		long lastTempFb = th.temperatureSensor().reading().getLastUpdateTime();
		long d = lastSetpointFb - lastTempFb;
		logger.trace("checking update time for setpoint feedback and temperature measurement: {} - {} = {}",
				lastSetpointFb, lastTempFb, d);
		if (lastSetpointFb == -1 && lastTempFb == -1) {
			return;
		}
		final int MAX_D = 180 * 60 * 1000;
		if (d > MAX_D) {
			logger.warn("Thermostat {} ({}) may be in a failed adaption state, trying to read error state and temperature", th.getPath(), address);
			try {
				/*
				Number err = conn.getValue(address, "VALVE_STATE");
				FloatResource valveState = th.valve().getSubResource("eq3state", FloatResource.class);
				if (!valveState.exists()) {
					valveState.create();
					valveState.activate(false);
				}
				logger.debug("received VALVE_STATE for Thermostat {} ({}): {}", th.getPath(), address, valveState);
				valveState.setValue(err.floatValue());
				*/
				Map<String, Object> values = conn.getParamset(address, "VALUES");
				Number err = (Number) values.get("VALVE_STATE");
				if (err != null) {
					FloatResource valveState = th.valve().getSubResource("eq3state", FloatResource.class);
					if (!valveState.exists()) {
						valveState.create();
						valveState.activate(false);
					}
					logger.debug("received VALVE_STATE for Thermostat {} ({}): {}", th.getPath(), address, valveState);
					valveState.setValue(err.floatValue());
				} else {
					logger.debug("no VALVE_STATE received for Thermostat {} ({})", th.getPath(), address);
				}
				Number actualTemp = (Number) values.get("ACTUAL_TEMPERATURE");
				if (actualTemp != null) {
					logger.debug("received ACTUAL_TEMPERATURE for Thermostat {} ({}): {}", th.getPath(), address, actualTemp);
					th.temperatureSensor().reading().setCelsius(actualTemp.floatValue());
				} else {
					logger.debug("no ACTUAL_TEMPERATURE received for Thermostat {} ({})", th.getPath(), address);
				}
			} catch (RuntimeException | IOException ex) {
				logger.warn("reading of VALVE_STATE failed for {} ({}): {}", th.getPath(), address, ex.getMessage());
				logger.debug("reading of VALVE_STATE failed for {} ({})", th.getPath(), address, ex);
			}
		}
	}
	
}
