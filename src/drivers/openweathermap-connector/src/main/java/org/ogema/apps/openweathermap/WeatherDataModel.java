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
import org.ogema.model.devices.sensoractordevices.WindSensor;
import org.ogema.model.locations.GeographicLocation;
import org.ogema.model.sensors.HumiditySensor;
import org.ogema.model.sensors.SolarIrradiationSensor;
import org.ogema.model.sensors.TemperatureSensor;

/**
 *
 * @author jlapp
 */
public interface WeatherDataModel {
    
    Resource getModel();
    
    /**
     * @return the location
     */
    GeographicLocation getLocation();

    /**
     * @return the tempSens
     */
    TemperatureSensor getTempSens();

    /**
     * @return the humiditySens
     */
    HumiditySensor getHumiditySens();

    /**
     * @return the windSens
     */
    WindSensor getWindSens();

    /**
     * @return the city
     */
    StringResource getCity();

    /**
     * @return the country
     */
    StringResource getCountry();

    /**
     * @return the irradSensor
     */
    SolarIrradiationSensor getIrradSensor();

    /**
     * @return the postalCode
     */
    StringResource getPostalCode();
    /**
     * @return the longitude
     */
    FloatResource getLongitude();

    /**
     * @return the latitude
     */
    FloatResource getLatitude();
    
    StringResource getForecastDataUpdateInterval();
    
    StringResource getCurrentDataUpdateInterval();
    
}
