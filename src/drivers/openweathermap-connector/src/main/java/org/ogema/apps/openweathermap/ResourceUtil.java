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
import org.ogema.apps.openweathermap.dao.CurrentData;

import org.ogema.apps.openweathermap.dao.ForecastData;
import org.ogema.apps.openweathermap.dao.OpenWeatherMapREST;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.channelmanager.measurements.DoubleValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.units.LengthResource;
import org.ogema.core.timeseries.InterpolationMode;

/**
 * 
 * Access to the OGEMA resources (forecasts).
 * 
 * @author brequardt
 */
public class ResourceUtil {

	private final Schedule irradiationForecast;
	private final Schedule humidityForecast;
	private final Schedule temperatureForecast;
	private Schedule windSpeedForecast = null;
	private Schedule windDirectionForecast = null;
	private final ApplicationManager appMan;
    private final WeatherDataModel pattern;

	public ResourceUtil(ApplicationManager appMan, WeatherDataModel pattern) {
        this.pattern = pattern;
		temperatureForecast = pattern.getTempSens().reading().forecast().create();
		humidityForecast = pattern.getHumiditySens().reading().forecast().create();
		irradiationForecast = pattern.getIrradSensor().reading().forecast().create();
		temperatureForecast.setInterpolationMode(InterpolationMode.LINEAR);
		humidityForecast.setInterpolationMode(InterpolationMode.LINEAR);
		irradiationForecast.setInterpolationMode(InterpolationMode.LINEAR);
		temperatureForecast.activate(true);
		humidityForecast.activate(true);
		irradiationForecast.activate(true);
		
		if(pattern.getWindSens().isActive()) {
			windSpeedForecast = pattern.getWindSens().speed().reading().forecast().create();
			windDirectionForecast = pattern.getWindSens().direction().reading().forecast().create();
			windSpeedForecast.setInterpolationMode(InterpolationMode.LINEAR);
			windDirectionForecast.setInterpolationMode(InterpolationMode.LINEAR);
			pattern.getWindSens().altitude().<LengthResource> create().setValue(0);
			pattern.getWindSens().activate(true);
		}
		
		this.appMan = appMan;
	}
    
    private void storeCurrent(CurrentData current) {
        pattern.getTempSens().reading().create();
        pattern.getTempSens().reading().setKelvin(current.getMain().getTemp().floatValue());
        pattern.getTempSens().reading().activate(false);
        pattern.getTempSens().activate(false);
        
        pattern.getHumiditySens().reading().create();
        pattern.getHumiditySens().reading().setValue(current.getMain().getHumidity() / 100f);
        pattern.getHumiditySens().reading().activate(false);
        pattern.getHumiditySens().activate(false);
        
        float irrad = (float) WeatherUtil.getInstance().calculateCurrentIrradiance(current);
        pattern.getIrradSensor().reading().create();
        pattern.getIrradSensor().reading().setValue(irrad);
        pattern.getIrradSensor().reading().activate(false);
        pattern.getIrradSensor().activate(false);
        
        if(pattern.getWindSens().isActive()) {
            pattern.getWindSens().speed().reading().create();
            pattern.getWindSens().speed().reading().setValue(current.getWind().getSpeed());
            pattern.getWindSens().speed().reading().activate(false);
            pattern.getWindSens().speed().activate(false);
            
            pattern.getWindSens().direction().reading().create();
            pattern.getWindSens().direction().reading().setValue(current.getWind().getDeg());
            pattern.getWindSens().direction().reading().activate(false);
            pattern.getWindSens().direction().activate(false);
            
            pattern.getWindSens().activate(false);
        }
    }
    
    public void store(ForecastData data, CurrentData current) {
        WeatherUtil.getInstance().calculateIrradiation(data, current);
        storeCurrent(current);
		boolean ignoreWind = (windDirectionForecast == null || windSpeedForecast == null);

		List<SampledValue> tempList = new ArrayList<>();
		List<SampledValue> humidityList = new ArrayList<>();
		List<SampledValue> irradiationList = new ArrayList<>();
		List<SampledValue> windSpeedList = null;
		List<SampledValue> windDirectionList = null;
		if(!ignoreWind) {
			windSpeedList = new ArrayList<>();
			windDirectionList = new ArrayList<>();
		}

		for (org.ogema.apps.openweathermap.dao.List entry : data.getList()) {

			SampledValue temp = newSampledDouble(entry.getMain().getTemp(), entry.getDt() * 1000l);
			SampledValue humidity = newSampledDouble(((double) entry.getMain().getHumidity()) / 100.0, entry.getDt() * 1000l);
			SampledValue irrad = newSampledDouble(entry.getIrradiation(), entry.getDt() * 1000l);
			
			tempList.add(temp);
			humidityList.add(humidity);
			irradiationList.add(irrad);

			if(!ignoreWind) {
				SampledValue windSpeed = newSampledDouble((double)entry.getWind().getSpeed(), entry.getDt() * 1000l);
				SampledValue windDirection = newSampledDouble((double)entry.getWind().getDeg(), entry.getDt() * 1000l);
				windSpeedList.add(windSpeed);
				windDirectionList.add(windDirection);
			}
		}

		temperatureForecast.addValues(tempList);
		humidityForecast.addValues(humidityList);
		irradiationForecast.addValues(irradiationList);
		
		if(!ignoreWind) {
			windSpeedForecast.addValues(windSpeedList);
			windDirectionForecast.addValues(windDirectionList);
			appMan.getLogger().debug("wrote {} values to {}", windSpeedList.size(), windSpeedForecast.getPath());
			appMan.getLogger().debug("wrote {} values to {}", windDirectionList.size(), windDirectionForecast.getPath());
		}

		appMan.getLogger().debug("wrote {} values to {}", tempList.size(), temperatureForecast.getPath());
		appMan.getLogger().debug("wrote {} values to {}", humidityList.size(), humidityForecast.getPath());
		appMan.getLogger().debug("wrote {} values to {}", irradiationList.size(), irradiationForecast.getPath());
    }

	/**
	 * calculate/interpolate weather information (temperature, solarirradiation)
	 * 
	 * @param city
	 *            name of the city
	 * @param county
	 *            shortcut of the country
	 */
	public void update(String city, String county) {
		ForecastData data = OpenWeatherMapREST.getInstance().getWeatherForcast(city, county);
		if (data == null)
			return;
		appMan.getLogger().debug("got {} values for {}/{}", data.getList().size(), county, city);
        store(data, OpenWeatherMapREST.getInstance().getWeatherCurrent(city, county));
	}
    
	private SampledValue newSampledDouble(Double value, long timestamp) {
		DoubleValue c = new DoubleValue(value);
		SampledValue e = new SampledValue(c, timestamp, Quality.GOOD);
		return e;
	}
}
