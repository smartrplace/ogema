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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

//import org.joda.time.DateTime;
import org.ogema.apps.openweathermap.dao.CurrentData;
import org.ogema.apps.openweathermap.dao.ForecastData;
import org.ogema.apps.openweathermap.dao.OpenWeatherMapREST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Access to the openweathermap services.
 * 
 * @author brequardt
 */
public class WeatherUtil {
    
    private static final int CONNECTION_TIMEOUT = 60000;
    private static final int CONNECTION_READ_TIMEOUT = 30000;

	private static WeatherUtil instance;
    
    private final static Logger LOGGER = LoggerFactory.getLogger(WeatherUtil.class);

	private WeatherUtil() {
	}

	public String call(String query) {

		if (query.toLowerCase().startsWith("https")) {
			return callSSL(query);
		}

		String result = "{}";
		try {
			final URLConnection urlCon = new URL(query).openConnection();
            urlCon.setConnectTimeout(CONNECTION_TIMEOUT);
            urlCon.setReadTimeout(CONNECTION_READ_TIMEOUT);
			urlCon.setRequestProperty("accept", "text/json");
			final InputStream input = urlCon.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(urlCon.getInputStream(), StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				result = line;
			}
			input.close();
		} catch (IOException e) {
            LOGGER.error("OpenWeatherMap query {} failed.", query, e);
			return null;
		}
		return result;
	}

	private String callSSL(String querry) {

		String result = "{}";
		try {
			final SSLContext sslContext = SSLContext.getInstance("SSL");
//			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
			final URLConnection urlCon = new URL(querry).openConnection();
            urlCon.setConnectTimeout(CONNECTION_TIMEOUT);
            urlCon.setReadTimeout(CONNECTION_READ_TIMEOUT);
			urlCon.setRequestProperty("accept", "text/json");
			((HttpsURLConnection) urlCon).setSSLSocketFactory(sslSocketFactory);
            try (InputStream input = urlCon.getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                String line;
                while ((line = reader.readLine()) != null) {
                    result = line;
                }
            }
		} catch (NoSuchAlgorithmException | IOException e) {
			return "ERROR: " + Arrays.toString(e.getStackTrace());
		}
		return result;
	}

	public boolean ping(String ip, Integer port, Integer timeout) {
		try {
			Socket so = new Socket();
			so.connect(new InetSocketAddress(ip, port), timeout);
			so.close();
			return true;
		} catch (IOException ex) {
			ex.printStackTrace();
			return false;
		}
	}

	public static synchronized WeatherUtil getInstance() {
		if (WeatherUtil.instance == null) {
			WeatherUtil.instance = new WeatherUtil();
		}

		return WeatherUtil.instance;
	}

	public String toString(ForecastData data) {
		final StringBuilder sb = new StringBuilder();
		String city = data.getCity().getName();
		for (org.ogema.apps.openweathermap.dao.List entry : data.getList()) {
			String date = entry.getDtTxt();
			String cloud = entry.getClouds().getAll() + "";
			String humidity = entry.getMain().getHumidity() + "";
			Double irradiance = (Math.round((entry.getIrradiation()) * 100) / 100.0);
			String tab = "\t";
			if (irradiance > 10000) {
				tab = "";
			}

			Double temp = (Math.round((entry.getMain().getTemp() - 273.15) * 100) / 100.0);
			sb.append(city).append(" (").append(date).append("): ").append(cloud).append(" % Wolken,\t")
					.append(irradiance).append(" Watt/m²\t").append(tab).append(humidity).append("% Feuchtigkeit,\t")
					.append(temp).append(" °C\n");
		}

		return sb.toString();
	}

	public String toString(CurrentData data) {
		final StringBuilder sb = new StringBuilder();
		String city = data.getName();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		String sunUp = sdf.format(new Date(data.getSys().getSunrise() * 1000L));
		String sunDown = sdf.format(new Date(data.getSys().getSunset() * 1000L));
		Double temp = (Math.round((data.getMain().getTemp() - 273.15) * 100) / 100.0);

		sb.append(city).append(":\n\tSonnenaufgang: ").append(sunUp).append(", Sonnenuntergang: ").append(sunDown)
				.append("\n\t").append("Temperatur: ").append(temp).append(", Luftdruck: ")
				.append(data.getMain().getPressure()).append("\n\t").append("Wind: ").append(data.getWind().getSpeed())
				.append("km/h");

		return sb.toString();
	}
    
    public void calculateIrradiation(ForecastData data) {
        final String city = data.getCity().getName();
		final String country = data.getCity().getCountry();

		final CurrentData current = OpenWeatherMapREST.getInstance().getWeatherCurrent(city, country);
        calculateIrradiation(data, current);
    }

	public void calculateIrradiation(ForecastData data, CurrentData current) {
		
		if (current != null) {
			final long sunUp = getMillisOfDay(current.getSys().getSunrise() * 1000L);
			final long sunDown = getMillisOfDay(current.getSys().getSunset() * 1000L);

			final Double latitude = data.getCity().getCoord().getLat(); // Breitengrad
			final Double longitude = data.getCity().getCoord().getLon(); // Laengengrad

			for (final org.ogema.apps.openweathermap.dao.List entry : data.getList()) {
				Integer cloudPercent = entry.getClouds().getAll();

				long millis = entry.getDt() * 1000l;

				// TODO: Sonnenauf- und untergang
				Double irradiance = 0.0d;
				long dayMillis = getMillisOfDay(millis);
				if (sunUp < dayMillis && dayMillis < sunDown) {
					irradiance = calculateIrradiation(latitude, longitude, cloudPercent, millis);
				}
				entry.setIrradiation(irradiance);
			}
		}
		/*
		else {
			OpenWeatherMapApplication.instance.logger.error("current weather not received sunrise is missing");
		}
		*/
	}

	private long getMillisOfDay(long millis) {
		long day = 24 * 60 * 60 * 1000l;
		return millis % day;
	}
    
    public double calculateCurrentIrradiance(CurrentData data) {
        return calculateIrradiation(
                data.getCoord().getLat(),
                data.getCoord().getLon(),
                data.getClouds().getAll(),
                data.getDt() * 1000L);
    }

	private Double calculateIrradiation(final Double latidute, final Double longitude, final Integer cloudPercent,
			final long LT) {

//		DateTime cal = new DateTime(LT);
		Calendar cal = Calendar.getInstance(Locale.ENGLISH);
		cal.setTimeInMillis(LT);

		final double LSTM = 15.0;
		final double EoT = getEoT(LT);
		final double TC = 4 * (longitude - LSTM) + EoT;
//		final Double LST = (double) cal.getHourOfDay() + (double) cal.getMinuteOfHour() / 60 + (TC / 60);
		final double LST = (double) cal.get(Calendar.HOUR_OF_DAY) + (double) cal.get(Calendar.MINUTE) / 60 + (TC / 60);

		final double HRA = (-1 * LSTM) * (LST - 12);
		final double B = getB(LT);

		// So far, all angles were expressed as gradiant values. However, they
		// need to be converted to Radians because Math functions use those!
		final double deklination = 23.45 * Math.sin(Math.toRadians(B));
		final double angleSunEarth = Math.asin(Math.cos(Math.toRadians(latidute))
				* Math.cos(Math.toRadians(deklination)) * Math.cos(Math.toRadians(HRA))
				+ Math.sin(Math.toRadians(latidute)) * Math.sin(Math.toRadians(deklination)));

		double irradiance = (1066 - cloudPercent * 3.14d) * Math.sin(angleSunEarth);
		if (irradiance < 0) {
			irradiance = 0.0d;
		}
		return irradiance;
	}

	private double getEoT(long millis) {
		final double B = getB(millis);
		final double eot = 9.87d * Math.sin(2 * B) - 7.53 * Math.cos(B) - 1.5 * Math.sin(B);
		return eot;
	}

	private double getB(long millis) {
		final double d = getDayofYear(millis);
		double B = 360d / 365d * d - 81;
		return B;
	}

	private int getDayofYear(long millis) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(millis);
		return cal.get(Calendar.DAY_OF_YEAR);

	}

}
