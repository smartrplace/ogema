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

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Timer;
import java.util.TimerTask;
import org.ogema.apps.openweathermap.dao.CurrentData;
import org.ogema.apps.openweathermap.dao.ForecastData;
import org.ogema.apps.openweathermap.dao.OpenWeatherMapREST;

import org.ogema.core.application.ApplicationManager;
import org.ogema.core.model.Resource;

/**
 * 
 * Control the environment resource (OGEMA room resource/outside room).
 * 
 * @author brequardt
 */
public class WeatherDataController {

	private final ApplicationManager appMan;
	private final WeatherDataModel device;
	private final long scheduleUpdateTime;
	private TimerTask task;
    private final long currentDataUpdateInterval;

	public WeatherDataController(ApplicationManager appMan, WeatherDataModel rad) {
        long forecastDataUpdateInterval = -1;
        if (rad.getForecastDataUpdateInterval().isActive()) {
            try {
                Duration d = Duration.parse(rad.getForecastDataUpdateInterval().getValue());
                forecastDataUpdateInterval = d.toMillis();
            } catch (DateTimeParseException dtpe) {
                appMan.getLogger().warn("invalid forecast data update interval '{}': {}",
                        rad.getForecastDataUpdateInterval().getValue(), dtpe.getMessage());
            }
        }
		scheduleUpdateTime = forecastDataUpdateInterval != -1
                ? forecastDataUpdateInterval
                : Long.getLong(OpenWeatherMapApplication.UPDATE_INTERVAL, OpenWeatherMapApplication.UPDATE_INTERVAL_DEFAULT);
        long updateInterval2 = -1;
        if (rad.getCurrentDataUpdateInterval().isActive()) {
            try {
                Duration d = Duration.parse(rad.getCurrentDataUpdateInterval().getValue());
                updateInterval2 = d.toMillis();
            } catch (DateTimeParseException dtpe) {
                appMan.getLogger().warn("invalid current data update interval '{}': {}",
                        rad.getCurrentDataUpdateInterval().getValue(), dtpe.getMessage());
            }
        }
        currentDataUpdateInterval = updateInterval2;
        appMan.getLogger().debug("update interval: {}ms", scheduleUpdateTime);
        if (currentDataUpdateInterval != -1) {
            appMan.getLogger().debug("update interval for current data: {}ms", currentDataUpdateInterval);
        }
		this.appMan = appMan;
		this.device = rad;
	}

	public void start() {

		if (device.getIrradSensor().reading() != null || device.getTempSens().reading() != null || device.getCountry() != null
				|| device.getCity() != null) {

			final ResourceUtil util = new ResourceUtil(appMan, device);
            if (OpenWeatherMapApplication.OFFLINE_MODE) {
                return;
            }
			task = new TimerTask() {

				@Override
				public void run() {
                    try {
                    	if(Boolean.getBoolean("org.ogema.apps.openweathermap.testwithoutconnection"))
                    		return;
                        appMan.getLogger().info(
							"updating weather info for location " + device.getModel().getName() + " next update in "
									+ scheduleUpdateTime + "ms");
                        OpenWeatherMapREST owmremote = OpenWeatherMapREST.getInstance();
                        ForecastData data;
                        CurrentData current = null;
                        if (device.getLatitude().isActive()) {
                            data = owmremote.getWeatherForcastCoord(device.getLatitude().getValue(), device.getLongitude().getValue());
                            current = owmremote.getWeatherCurrentCoord(device.getLatitude().getValue(), device.getLongitude().getValue());
                        } else if (device.getPostalCode().isActive()) {
                            data = owmremote.getWeatherForcastZip(device.getPostalCode().getValue(), device.getCountry().getValue());
                            current = owmremote.getWeatherCurrentZip(device.getPostalCode().getValue(), device.getCountry().getValue());
                        } else {
                            data = owmremote.getWeatherForcast(device.getCity().getValue(), device.getCountry().getValue());
                            current = owmremote.getWeatherCurrent(device.getCity().getValue(), device.getCountry().getValue());
                        }
                        if (data == null) {
                            appMan.getLogger().warn("No OPEN_WEATHERMAP Data !! Will not start !!");
                            return;
                        }
                        util.store(data, current);
                        appMan.getLogger().info("update complete for location {}", device.getModel().getName());
                    } catch (RuntimeException e) {
                        appMan.getLogger().warn("update failed: {}", e.getMessage());
                        appMan.getLogger().debug("update failed", e);
                    }
				}
			};

			Timer t = new Timer();
			t.schedule(task, 0, scheduleUpdateTime);
            
            if (currentDataUpdateInterval != -1) {
                TimerTask updateCurrentData = new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            appMan.getLogger().info(
                                    "updating current weather info for location " + device.getModel().getName() + " next update in "
                                    + currentDataUpdateInterval + "ms");
                            OpenWeatherMapREST owmremote = OpenWeatherMapREST.getInstance();
                            CurrentData current = null;
                            if (device.getLatitude().isActive()) {
                                current = owmremote.getWeatherCurrentCoord(device.getLatitude().getValue(), device.getLongitude().getValue());
                            } else if (device.getPostalCode().isActive()) {
                                current = owmremote.getWeatherCurrentZip(device.getPostalCode().getValue(), device.getCountry().getValue());
                            } else {
                                current = owmremote.getWeatherCurrent(device.getCity().getValue(), device.getCountry().getValue());
                            }
                            util.storeCurrent(current);
                            appMan.getLogger().info("update complete for location {}", device.getModel().getName());
                        } catch (RuntimeException re) {
                            appMan.getLogger().warn("current data update failed: {}", re.getMessage());
                            appMan.getLogger().debug("update failed", re);
                        }
                    }
                };
                t.scheduleAtFixedRate(updateCurrentData, 0, currentDataUpdateInterval);
            }

		}
	}

	public boolean isControllingDevice(Resource model) {
		return device.getModel().equalsLocation(model);
	}

	public void stop() {
		if (task != null) {
			task.cancel();
		}
	}

}
