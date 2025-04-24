/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ogema.core.model.ValueResource;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.units.TemperatureResource;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.devices.sensoractordevices.SensorDeviceLabelled;
import org.ogema.model.sensors.TemperatureSensor;
import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=1"})
public class IpSte2Channel extends AbstractDeviceHandler implements DeviceHandlerFactory {
	
	private final static Map<String, Class<? extends SingleValueResource>> STE2_PARAMETERS;

	static {
		STE2_PARAMETERS = new LinkedHashMap<>();
		STE2_PARAMETERS.put("COND_TX_CYCLIC_ABOVE", BooleanResource.class);
		STE2_PARAMETERS.put("COND_TX_CYCLIC_BELOW", BooleanResource.class);
		STE2_PARAMETERS.put("COND_TX_FALLING", BooleanResource.class);
		STE2_PARAMETERS.put("COND_TX_RISING", BooleanResource.class);
		STE2_PARAMETERS.put("COND_TX_THRESHOLD_HI", TemperatureResource.class);
		STE2_PARAMETERS.put("COND_TX_THRESHOLD_LO", TemperatureResource.class);
		STE2_PARAMETERS.put("COND_TX_THRESHOLD_HI", TemperatureResource.class);
		STE2_PARAMETERS.put("TEMPERATURE_OFFSET", TemperatureResource.class);
		// PARAMETERS.put("TX_MINDELAY_UNIT", IntegerResource.class); //enum: 100MS, S, M, H
		// PARAMETERS.put("TX_MINDELAY_VALUE", IntegerResource.class);
	}

	Logger logger = LoggerFactory.getLogger(getClass());
	
	static interface HmEventParameter {
		
		String name();
		
		default String paramName() {
			return name();
		}
		
		default void setValue(HmEvent e, ValueResource res) {
			ValueResourceUtils.setValue(res, e.getValue());
		}
		
	}

	static enum PARAMS implements HmEventParameter {
		ACTUAL_TEMPERATURE() {
			@Override
			public void setValue(HmEvent e, ValueResource res) {
				((TemperatureResource) res).setCelsius(e.getValueFloat());
			}
		},
		ACTUAL_TEMPERATURE_STATUS;
	}
	
	static enum TEMPERATURE_STATUS {
		NORMAL, UNKNOWN, OVERFLOW, UNDERFLOW
	}
	
	static class EventListener implements HmEventListener {
		
		final String address;
		final Logger logger;
		final Map<HmEventParameter, ValueResource> params;

		public EventListener(String address, Map<HmEventParameter, ValueResource> params, Logger logger) {
			this.address = address;
			this.logger = logger;
			this.params = params;
		}

		@Override
		public void event(List<HmEvent> events) {
			for (HmEvent e: events) {
				if (!address.equalsIgnoreCase(e.getAddress())) {
					continue;
				}
				String key = e.getValueKey();
				for (Map.Entry<HmEventParameter, ValueResource> param2res: params.entrySet()) {
					if (key.equalsIgnoreCase(param2res.getKey().paramName())) {
						ValueResource r = param2res.getValue();
						r.create();
						param2res.getKey().setValue(e, r);
						r.activate(false);
						logger.trace("updated {} = {} ({})", r.getPath(), ValueResourceUtils.getValue(r), e);
					}
				}
			}
		}
	}
	
	public IpSte2Channel() {
		super(null);
	}
	
	public IpSte2Channel(HomeMaticConnection conn) {
		super(conn);
	}

	@Override
	public DeviceHandler createHandler(HomeMaticConnection connection) {
		return new IpSte2Channel(connection);
	}

	@Override
	public boolean accept(DeviceDescription desc) {
		return "COND_SWITCH_TRANSMITTER_TEMPERATURE".equals(desc.getType())
				&& desc.getParentType().startsWith("HmIP-STE2");
	}

	@Override
	public void setup(HmDevice device, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
		try {
		
		String addr = desc.getAddress();
		logger.debug("performing HmIP-STE2 channel setup for {}", addr);
		String sensorName = ResourceUtils.getValidResourceName(addr);
		if (addr.endsWith(":1")) {
			sensorName = "temperature1";
		} else if (addr.endsWith(":2")) {
			sensorName = "temperature2";
		} else if (addr.endsWith(":3")) {
			sensorName = "temperatureDifference";
		}
		SensorDeviceLabelled dev = device.getSubResource("sensors", SensorDeviceLabelled.class);
		dev.sensors().create().activate(false);
		dev.activate(false);
		TemperatureSensor ts = dev.sensors().getSubResource(sensorName, TemperatureSensor.class);
		ts.reading().create();
		IntegerResource status = ts.getSubResource("HmTemperatureStatus", IntegerResource.class);
		status.create();
		ts.activate(false);
		Map<HmEventParameter, ValueResource> resMap = new HashMap<>();
		resMap.put(PARAMS.ACTUAL_TEMPERATURE, ts.reading());
		resMap.put(PARAMS.ACTUAL_TEMPERATURE_STATUS, status);
		conn.addEventListener(new EventListener(addr, resMap, logger));
		ThermostatUtils.setupParameterResources(device, desc, paramSets, STE2_PARAMETERS, conn, ts, logger);
		conn.registerControlledResource(device, ts);
		ChannelUtils.linkMaintenanceWhenAvailable(device, dev.electricityStorage());
		if (addr.endsWith(":1")) {
			dev.mainSensor().setAsReference(ts);
			if (!dev.mainSensorTitle().exists()) {
				dev.mainSensorTitle().create();
				dev.mainSensorTitle().setValue("Temperature");
				dev.mainSensorTitle().activate(false);
			}
			if (!dev.deviceTypeName().exists()) {
				dev.deviceTypeName().create();
				dev.deviceTypeName().setValue("Temperature Sensor");
				dev.deviceTypeName().activate(false);
			}
		}
		logger.debug("channel setup complete for {}: {}", addr, ts.getPath());
		
		} catch (Throwable t) {
			System.out.println(t);
			t.printStackTrace();
			logger.error("fail!", t);
		}
	}
	
}
