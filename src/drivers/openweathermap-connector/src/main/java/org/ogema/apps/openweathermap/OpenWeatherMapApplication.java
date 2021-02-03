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
package org.ogema.apps.openweathermap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.ogema.apps.openweathermap.dao.OpenWeatherMapREST;

import org.ogema.apps.openweathermap.resources.EnvironmentCreater;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.logging.OgemaLogger;
import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.resourcemanager.AccessPriority;
import org.ogema.core.resourcemanager.ResourceManagement;
import org.ogema.core.resourcemanager.pattern.PatternListener;
import org.ogema.core.resourcemanager.pattern.ResourcePatternAccess;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

//import org.osgi.service.cm.ConfigurationAdmin;
/**
 *
 * Application main class. Application connect to the openweathermap services
 * (www.openweathermap.com) and store weather information
 * (temperature,cloudiness, humidity, rain) into OGEMA resources. Also calculate
 * solar irradiation.
 *
 * @author brequardt
 */
@Designate(ocd = OpenWeatherMapApplication.Config.class)
@Component(service = {Application.class, OpenWeatherMapApplicationI.class},
        configurationPolicy = ConfigurationPolicy.OPTIONAL)
public class OpenWeatherMapApplication implements OpenWeatherMapApplicationI {

    public static final String PID = "org.ogema.apps.OpenWeatherMap";

    @ObjectClassDefinition(
            localization = "OSGI-INF/l10n/OpenWeatherMapConfig",
            name = "%name", description = "%description")
    public static @interface Config {

        @AttributeDefinition(name = "%apikey", description = "%apikey_desc")
        String apikey();

        @AttributeDefinition(name = "%city", description = "%city_desc")
        String city();

        @AttributeDefinition(name = "%country", description = "%country_desc")
        String country();

        @AttributeDefinition(name = "%postal", description = "%postal_desc")
        String postalCode();

        @AttributeDefinition(name = "%lat", description = "%lat_desc")
        double latitude() default Double.NaN;

        @AttributeDefinition(name = "%lon", description = "%lon_desc")
        double longitude() default Double.NaN;
        
        @AttributeDefinition(name = "%resourceName", description = "%resourceName_desc")
        String resourceName() default "OpenWeatherMapData";
        
        @AttributeDefinition(name = "%useSensorDevice", description = "%useSensorDevice_desc")
        boolean useSensorDevice() default false;

    }

    /**
     * System property ({@value} ) holding the interval in ms at which weather
     * data will be retrieved.
     */
    public static final String UPDATE_INTERVAL = "org.ogema.drivers.openweathermap.getWeatherInfoRepeatTime";

    /**
     * Default value ({@value} ) for {@link #UPDATE_INTERVAL}.
     */
    public static final long UPDATE_INTERVAL_DEFAULT = 3 * 60 * 60 * 1000L;

    public OgemaLogger logger;
    protected ApplicationManager appMan;
    protected ResourceManagement resMan;
    // @Reference
    // private ConfigurationAdmin configurationAdmin;
    public static OpenWeatherMapApplication instance;
    EnvironmentCreater envCreater;
    private List<WeatherDataController> roomControllers = new ArrayList<>();
    private ResourcePatternAccess advAcc;
    private Config config;

    @Activate
    protected void activate(Config cfg) {
        if (cfg != null && isUsableConfig(cfg)) {
            this.config = cfg;
        }
    }

    boolean isUsableConfig(Config cfg) {
        return (cfg.apikey() != null || !System.getProperty(OpenWeatherMapREST.API_KEY_PROPERTY, "").isEmpty())
                && ((cfg.city() != null && cfg.country() != null)
                    || (cfg.postalCode() != null && cfg.country() != null)
                    || (!Double.isNaN(cfg.latitude()) && !Double.isNaN(cfg.latitude())));
    }

    @Override
    public void start(ApplicationManager appManager) {

        instance = this;
        this.appMan = appManager;
        this.logger = appManager.getLogger();
        this.resMan = appManager.getResourceManagement();
        envCreater = new EnvironmentCreater(appManager);
        String stdCity = null;
        String stdCountry = null;
        try {
            stdCity = System.getProperty("org.ogema.drivers.openweathermap.stdCity");
            stdCountry = System.getProperty("org.ogema.drivers.openweathermap.stdCountry");
        } catch (SecurityException e) {
            logger.warn("Permission denied to access init properties", e);
        }
        if ((stdCity != null) && (stdCountry != null)) {
            envCreater.createResource("OpenWeatherMapData", stdCity, stdCountry);
        }
        if (config != null) {
            logger.debug("using configuration from OSGi ConfigAdmin");
            WeatherDataModel r = config.useSensorDevice()
                    ? envCreater.createSensorDeviceModel(config.resourceName(), "", "")
                    : envCreater.createResource(config.resourceName(), "", "");
            storeConfig(config, r);
            if (config.apikey() != null) {
                OpenWeatherMapREST.getInstance().setAPI_KEY(config.apikey());
            }
        }
        advAcc = appManager.getResourcePatternAccess();
        advAcc.addPatternDemand(RoomRad.class, roomListener, AccessPriority.PRIO_DEVICEGROUPMAN);
        advAcc.addPatternDemand(WeatherSensorDeviceRad.class, sensorDeviceListener, AccessPriority.PRIO_DEVICEGROUPMAN);
    }

    @Override
    public void stop(AppStopReason reason) {
        for (WeatherDataController controller : roomControllers) {
            controller.stop();
        }
        roomControllers.clear();
        advAcc.removePatternDemand(RoomRad.class, roomListener);
        advAcc.removePatternDemand(WeatherSensorDeviceRad.class, sensorDeviceListener);
    }

    void storeConfig(Config cfg, WeatherDataModel model) {
        if (Double.isNaN(cfg.latitude())) {
            model.getLatitude().delete();
            model.getLongitude().delete();
        } else {
            model.getLatitude().<FloatResource>create().setValue((float) cfg.latitude());
            model.getLongitude().<FloatResource>create().setValue((float) cfg.longitude());
        }
        if (cfg.city() != null && !cfg.city().isEmpty()) {
            model.getCity().<StringResource>create().setValue(cfg.city());
        } else {
            model.getCity().delete();
        }
        if (cfg.country() != null && !cfg.country().isEmpty()) {
            model.getCountry().<StringResource>create().setValue(cfg.country());
        } else {
            model.getCountry().delete();
        }
        if (cfg.postalCode() != null && !cfg.postalCode().isEmpty()) {
            model.getPostalCode().<StringResource>create().setValue(cfg.postalCode());
        } else {
            model.getPostalCode().delete();
        }
        model.getModel().activate(true);
    }

    /**
     * Create an environment OGEMA resource for saving weather information.
     *
     * @param name name of the environment
     * @param city name of the city
     * @param country name of country (shortcuts) example: de for germany
     * @return OGEMA resource
     */
    @Override
    public Resource createEnvironment(String name, String city, String country) {
        return envCreater.createResource(name, city, country).model;
    }

    final PatternListener<RoomRad> roomListener = new PatternListener<RoomRad>() {

        @Override
        public void patternAvailable(RoomRad rad) {
            final WeatherDataController newController = new WeatherDataController(appMan, rad);
            roomControllers.add(newController);
            newController.start();
        }

        @Override
        public void patternUnavailable(RoomRad rad) {
            WeatherDataController controller = null;
            for (WeatherDataController existingController : roomControllers) {
                if (existingController.isControllingDevice(rad.model)) {
                    controller = existingController;
                    break;
                }
            }
            if (controller == null) {
                logger.warn("Got a resource unavailable callback for a RAD that has no controller.");
                return;
            }
            controller.stop();
            roomControllers.remove(controller);
        }
    };
    
    final PatternListener<WeatherSensorDeviceRad> sensorDeviceListener = new PatternListener<WeatherSensorDeviceRad>() {

        @Override
        public void patternAvailable(WeatherSensorDeviceRad rad) {
            final WeatherDataController newController = new WeatherDataController(appMan, rad);
            roomControllers.add(newController);
            newController.start();
            logger.info("started new WeatherDataController for model {}", rad.model.getPath());
        }

        @Override
        public void patternUnavailable(WeatherSensorDeviceRad rad) {
            WeatherDataController controller = null;
            for (WeatherDataController existingController : roomControllers) {
                if (existingController.isControllingDevice(rad.model)) {
                    controller = existingController;
                    break;
                }
            }
            if (controller == null) {
                logger.warn("Got a resource unavailable callback for a RAD that has no controller.");
                return;
            }
            controller.stop();
            roomControllers.remove(controller);
        }
    };

    /**
     * return environment parameters
     *
     * @param name name of the environment information
     * @return return information inside a map
     */
    @Override
    public Map<String, Object> getEnviromentParameter(String name) {
        // TODO Auto-generated method stub
        return envCreater.getParameters(name);
    }

}
