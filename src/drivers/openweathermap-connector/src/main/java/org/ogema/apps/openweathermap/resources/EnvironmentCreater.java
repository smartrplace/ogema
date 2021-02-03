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
package org.ogema.apps.openweathermap.resources;

import java.util.HashMap;
import java.util.Map;

import org.ogema.apps.openweathermap.RoomRad;
import org.ogema.apps.openweathermap.WeatherDataModel;
import org.ogema.apps.openweathermap.WeatherSensorDeviceRad;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.logging.OgemaLogger;
import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.model.units.LengthResource;
import org.ogema.model.locations.Room;
import org.ogema.model.sensors.SolarIrradiationSensor;

/**
 * 
 * Create,handle OGEMA resource (outside room).
 * 
 * @author brequardt
 */
public class EnvironmentCreater {

	private final OgemaLogger logger;
	private final ApplicationManager appMan;

	private static final int SOLAR_LIMIT_LOWER = 0;
	private static final int SOLAR_LIMIT_UPPER = 1500;

	public EnvironmentCreater(ApplicationManager appMan) {
		this.logger = appMan.getLogger();
		this.appMan = appMan;
	}

	public RoomRad createResource(String name, final String city, final String country) {

		logger.info("create new resource with name: " + name);

		Room environment = appMan.getResourceManagement().createResource(name, Room.class);
		RoomRad pattern = new RoomRad(environment);
		environment.type().<IntegerResource> create().setValue(0);

		pattern.getCity().<StringResource> create().setValue(city);
		pattern.getCountry().<StringResource> create().setValue(country);
		pattern.getTempSens().reading().forecast().create();
		pattern.getHumiditySens().reading().forecast().create();
		pattern.getWindSens().direction().reading().forecast().create();
		pattern.getWindSens().speed().reading().forecast().create();
		pattern.getWindSens().altitude().<LengthResource> create().setValue(0);
		
		SolarIrradiationSensor irradSens = pattern.getIrradSensor();
		irradSens.reading().forecast().create();
		irradSens.ratedValues().upperLimit().<FloatResource> create().setValue(SOLAR_LIMIT_UPPER);
		irradSens.ratedValues().lowerLimit().<FloatResource> create().setValue(SOLAR_LIMIT_LOWER);

		appMan.getResourcePatternAccess().activatePattern(pattern);
		pattern.getTempSens().reading().forecast().activate(false);
		
		return pattern;
	}
    
    public WeatherDataModel createSensorDeviceModel(String name, final String city, final String country) {
        logger.info("create new SensorDevice with name: " + name);
        WeatherDataModel model = appMan.getResourcePatternAccess().createResource(name, WeatherSensorDeviceRad.class);
        
        if (!model.getWindSens().altitude().exists()) {
            model.getWindSens().altitude().<LengthResource> create().setValue(0);
        }
        if (city != null && !city.isEmpty()) {
            model.getCity().<StringResource> create().setValue(city);
        }
        if (country != null && !country.isEmpty()) {
            model.getCountry().<StringResource> create().setValue(city);
        }
        SolarIrradiationSensor irradSens = model.getIrradSensor();
		irradSens.reading().forecast().create();
		irradSens.ratedValues().upperLimit().<FloatResource> create().setValue(SOLAR_LIMIT_UPPER);
		irradSens.ratedValues().lowerLimit().<FloatResource> create().setValue(SOLAR_LIMIT_LOWER);
        
        return model;
    }

	public Map<String, Object> getParameters(String name) {

		Map<String, Object> map = new HashMap<String, Object>();

		Room environment = appMan.getResourceAccess().getResource(name);

		map.put("country", environment.location().geographicLocation().getSubResource("country", StringResource.class)
				.getValue());

		map.put("city", environment.location().geographicLocation().getSubResource("city", StringResource.class)
				.getValue());

		return map;

	}
}
