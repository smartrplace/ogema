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

import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.resourcemanager.pattern.ResourcePattern;
import org.ogema.core.resourcemanager.pattern.ResourcePattern.CreateMode;
import org.ogema.core.resourcemanager.pattern.ResourcePattern.Existence;
import org.ogema.model.devices.sensoractordevices.WindSensor;
import org.ogema.model.locations.GeographicLocation;
import org.ogema.model.locations.Room;
import org.ogema.model.sensors.GenericFloatSensor;
import org.ogema.model.sensors.HumiditySensor;
import org.ogema.model.sensors.SolarIrradiationSensor;
import org.ogema.model.sensors.TemperatureSensor;

/**
 * OGEMA rad for outside room.
 * 
 * @author brequardt
 * 
 */
// TODO add identifier for OpenWeatherMap connector - do not use every room in the system with matching subresources
public class RoomRad extends ResourcePattern<Room> implements WeatherDataModel {
    
    @Existence(required = CreateMode.MUST_EXIST)
    protected GeographicLocation location = model.location().geographicLocation();
    @Existence(required = CreateMode.MUST_EXIST)
    protected TemperatureSensor tempSens = model.temperatureSensor();
    @Existence(required = CreateMode.MUST_EXIST)
    protected HumiditySensor humiditySens = model.humiditySensor();
    @Existence(required = CreateMode.OPTIONAL)
    protected WindSensor windSens = model.getSubResource("windSensor", WindSensor.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected GenericFloatSensor cloudCoverage = model.getSubResource("cloudCoverage", GenericFloatSensor.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected StringResource city = model.location().geographicLocation().getSubResource("city", StringResource.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected StringResource country = model.location().geographicLocation().getSubResource("country", StringResource.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected SolarIrradiationSensor irradSensor = model.getSubResource("solarIrradiationSensor", SolarIrradiationSensor.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected StringResource postalCode = model.location().getSubResource("postalCode", StringResource.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected FloatResource longitude = model.location().getSubResource("longitude", FloatResource.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected FloatResource latitude = model.location().getSubResource("latitude", FloatResource.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected StringResource currentDataUpdateInterval = model.location().getSubResource("currentDataUpdateInterval", StringResource.class);
    @Existence(required = CreateMode.OPTIONAL)
    protected StringResource forecastDataUpdateInterval = model.location().getSubResource("forecastDataUpdateInterval", StringResource.class);
    
	public RoomRad(Resource match) {
		super(match);
	}

    @Override
    public Room getModel() {
        return model;
    }

    /**
     * @return the location
     */
    @Override
    public GeographicLocation getLocation() {
        return location;
    }

    /**
     * @return the tempSens
     */
    @Override
    public TemperatureSensor getTempSens() {
        return tempSens;
    }

    /**
     * @return the humiditySens
     */
    @Override
    public HumiditySensor getHumiditySens() {
        return humiditySens;
    }

    /**
     * @return the windSens
     */
    @Override
    public WindSensor getWindSens() {
        return windSens;
    }

    /**
     * @return the city
     */
    @Override
    public StringResource getCity() {
        return city;
    }

    /**
     * @return the country
     */
    @Override
    public StringResource getCountry() {
        return country;
    }

    /**
     * @return the irradSensor
     */
    @Override
    public SolarIrradiationSensor getIrradSensor() {
        return irradSensor;
    }

    /**
     * @return the postalCode
     */
    @Override
    public StringResource getPostalCode() {
        return postalCode;
    }

    /**
     * @return the longitude
     */
    @Override
    public FloatResource getLongitude() {
        return longitude;
    }

    /**
     * @return the latitude
     */
    @Override
    public FloatResource getLatitude() {
        return latitude;
    }
    
    @Override
    public StringResource getCurrentDataUpdateInterval() {
        return currentDataUpdateInterval;
    }

    @Override
    public StringResource getForecastDataUpdateInterval() {
        return forecastDataUpdateInterval;
    }

    public GenericFloatSensor getCloudCoverage() {
        return cloudCoverage;
    }
    
}
