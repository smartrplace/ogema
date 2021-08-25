package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.actors.MultiSwitch;
import org.ogema.model.devices.buildingtechnology.Thermostat;
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
            try {
                Map<String, Object> values = conn.getParamset(address, set.name());
                int count = 0;
                for (String paramName: params.keySet()) {
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
                    CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(updateValues);
                }, true);
                params.put(p, r);
                logger.debug("set up parameter {} on {}", p, address);
            }
        });
        BooleanResource update = paramList.getSubResource("update", BooleanResource.class).create();
        update.create();
        update.addValueListener(updateListener, true);
        update.activate(false);
        // automatic update on startup for all devices seems to cause problems...
        //CompletableFuture.runAsync(updateValues);
    }

    /*
    static void setupValveMaximumPosition(HmDevice parent, DeviceDescription desc,
            Map<String, Map<String, ParameterDescription<?>>> paramSets,
            HomeMaticConnection conn, Thermostat thermos, Logger logger) {
        String parameterName = "VALVE_MAXIMUM_POSITION";
        ParameterDescription.SET_TYPES set = ParameterDescription.SET_TYPES.MASTER;
        Map<String, ParameterDescription<?>> masterParams = paramSets.get(set.name());
        if (masterParams == null) {
            logger.debug("no parameter set 'MASTER' for {}", parent.address());
            return;
        }
        ParameterDescription<?> pdesc = masterParams.get(parameterName);
        if (pdesc == null) {
            logger.debug("no parameter '{}' for {}", parameterName, parent.address());
            return;
        }
        MultiSwitch vmp = thermos.getSubResource("valveMaximumPosition", MultiSwitch.class);
        vmp.stateControl().create();
        vmp.stateFeedback().create();

        Runnable getStateFeedback = () -> {
            try {
                Object val = conn.getParamset(desc.getAddress(), set.name()).get(parameterName);
                if (!(val instanceof Number)) {
                    logger.debug("received null or unsupported type for {} on {}: {}", parameterName, desc.getAddress(), val);
                    return;
                }
                vmp.stateFeedback().setValue(((Number) val).floatValue());
                vmp.stateFeedback().activate(false);
            } catch (IOException | RuntimeException ex) {
                logger.debug("could not read value '{}' on {}: {}", parameterName, desc.getAddress(), ex.getMessage());
            }
        };

        vmp.stateControl().addValueListener((FloatResource r) -> {
            float v = r.getValue();
            conn.performPutParamset(desc.getAddress(), set.name(), Collections.singletonMap(parameterName, v));
            CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(getStateFeedback);
        }, true);
        vmp.stateControl().activate(false);
        vmp.activate(false);
    }
    */

}
