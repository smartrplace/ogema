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

	public WeatherDataController(ApplicationManager appMan, WeatherDataModel rad) {
		scheduleUpdateTime = Long.getLong(OpenWeatherMapApplication.UPDATE_INTERVAL,
				OpenWeatherMapApplication.UPDATE_INTERVAL_DEFAULT);
		this.appMan = appMan;
		this.device = rad;
	}

	public void start() {

		if (device.getIrradSensor().reading() != null || device.getTempSens().reading() != null || device.getCountry() != null
				|| device.getCity() != null) {

			final ResourceUtil util = new ResourceUtil(appMan, device);

			task = new TimerTask() {

				@Override
				public void run() {
					if(Boolean.getBoolean("org.ogema.apps.openweathermap.testwithoutconnection"))
						return;
					appMan.getLogger().info(
							"update weather info for location " + device.getModel().getName() + " next update in "
									+ scheduleUpdateTime + "ms");

                    OpenWeatherMapREST owmremote = OpenWeatherMapREST.getInstance();
                    ForecastData data;
                    CurrentData current;
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
                    if(data == null) {
    					appMan.getLogger().warn("No OPEN_WEATHERMAP Data !! Will not start !!");
                    	return;
                    }
                    util.store(data, current);
				}
			};

			Timer t = new Timer();
			t.schedule(task, 0, scheduleUpdateTime);

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
