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
package org.ogema.apps.openweathermap.dao;

import org.ogema.apps.openweathermap.OpenWeatherMapApplication;
import org.ogema.apps.openweathermap.WeatherUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author skarge
 */
public class OpenWeatherMapREST {

	private String API_KEY = "";
    public static final String API_KEY_PROPERTY = "org.ogema.drivers.openweathermap.key";
	private final String BASE_URL = "http://api.openweathermap.org/";
	private final WeatherUtil util = WeatherUtil.getInstance();
	private static OpenWeatherMapREST instance;
    
    private final static Logger LOGGER = LoggerFactory.getLogger(OpenWeatherMapREST.class);

	public static void main(String[] args) {
        //System.setProperty(API_KEY_PROPERTY, "");
		OpenWeatherMapREST rest = OpenWeatherMapREST.getInstance();
		//ForecastData data = rest.getWeatherForcast("München", "de");
        ForecastData data = rest.getWeatherForcastZip("10200", "th");
        //ForecastData data = rest.getWeatherForcastCoord(48.85, 2.35); //Paris
		
		rest.util.calculateIrradiation(data);
		
		System.out.println(rest.util.toString(data) + "--------------------\n");
		//CurrentData current = rest.getWeatherCurrent(city, country);
        CurrentData current = rest.getWeatherCurrentCoord(50.97, 8.97);
		System.out.println(rest.util.toString(current));
	}

	public void setAPI_KEY(String API_KEY) {
		this.API_KEY = API_KEY;
	}

	public static OpenWeatherMapREST getInstance() {
		if (OpenWeatherMapREST.instance == null) {
			OpenWeatherMapREST.instance = new OpenWeatherMapREST();
			String key = System.getProperty(API_KEY_PROPERTY, null);
			if (key == null) {
				System.out.print("openweathermapKEY is required, Please register: http://openweathermap.org/register");
			}
			else {
				OpenWeatherMapREST.instance.setAPI_KEY(key);
			}
		}

		return OpenWeatherMapREST.instance;
	}

	public ForecastData getWeatherForcast(String city, String countryCode) {
		String url = BASE_URL + "data/2.5/forecast?q=" + city + "," + countryCode;
		if (API_KEY != null && API_KEY.isEmpty() == false) {
			url += "&APPID=" + API_KEY;
		}
		return getWeatherForecast(url);
	}
    
    public ForecastData getWeatherForcastZip(String postalCode, String countryCode) {
		String url = BASE_URL + "data/2.5/forecast?zip=" + postalCode + "," + countryCode;
		if (API_KEY != null && API_KEY.isEmpty() == false) {
			url += "&APPID=" + API_KEY;
		}
		return getWeatherForecast(url);
	}
    
    public ForecastData getWeatherForcastCoord(double lat, double lon) {
        String url = BASE_URL + "data/2.5/forecast?lat=" + lat + "&lon=" + lon;
		if (API_KEY != null && API_KEY.isEmpty() == false) {
			url += "&APPID=" + API_KEY;
		}
		return getWeatherForecast(url);
    }
    
    private ForecastData getWeatherForecast(String url) {
        String json = util.call(url);
		if (json == null)
			return null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			ForecastData data = mapper.readValue(json, ForecastData.class);
			return data;
		} catch (IOException e) {
            LOGGER.error("could not parse JSON: {}", json, e);
			return null;
		}
    }

	public CurrentData getWeatherCurrent(String city, String countryCode) {
		String url = BASE_URL + "data/2.5/weather?q=" + city + "," + countryCode;
		if (API_KEY != null && API_KEY.isEmpty() == false) {
			url += "&APPID=" + API_KEY;
		}
		return getWeatherCurrent(url);
	}
    
    public CurrentData getWeatherCurrentZip(String postalCode, String countryCode) {
		String url = BASE_URL + "data/2.5/weather?zip=" + postalCode + "," + countryCode;
		if (API_KEY != null && API_KEY.isEmpty() == false) {
			url += "&APPID=" + API_KEY;
		}
		return getWeatherCurrent(url);
	}
    
    public CurrentData getWeatherCurrentCoord(double lat, double lon) {
		String url = BASE_URL + "data/2.5/weather?lat=" + lat + "&lon=" + lon;
		if (API_KEY != null && API_KEY.isEmpty() == false) {
			url += "&APPID=" + API_KEY;
		}
		return getWeatherCurrent(url);
	}
    
    private CurrentData getWeatherCurrent(String url) {
        String json = util.call(url);
		if (json == null)
			return null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			CurrentData data = mapper.readValue(json, CurrentData.class);
			return data;
		} catch (IOException e) {
			LOGGER.error("irradiation could not be calculated", e);
			return null;
		}
    }

}
