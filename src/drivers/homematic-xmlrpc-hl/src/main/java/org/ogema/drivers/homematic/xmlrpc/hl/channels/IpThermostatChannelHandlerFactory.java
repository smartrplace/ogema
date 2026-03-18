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
package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import org.osgi.service.component.annotations.Component;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.channels.IpThermostatChannelHandlerFactory.Config;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 *
 * @author jlapp
 */
@Designate(ocd = Config.class)
// ranking: above IpWeatherRoomSensorChannel which would create STH as SensorDevice
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=5"})
public class IpThermostatChannelHandlerFactory implements DeviceHandlerFactory {
	
	@ObjectClassDefinition
	public static @interface Config {
		boolean handleSTH() default true;
	}
	
	Config cfg;
	
	@Activate
	protected void activate(Config config) {
		this.cfg = config;
	}

    @Override
    public DeviceHandler createHandler(HomeMaticConnection connection) {
        return new IpThermostatChannel(connection, cfg);
    }    
    
}
