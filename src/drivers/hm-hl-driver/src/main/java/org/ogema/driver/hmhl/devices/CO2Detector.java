/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ogema.driver.hmhl.devices;

import org.apache.commons.lang3.ArrayUtils;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.channelmanager.driverspi.DeviceLocator;
import org.ogema.core.channelmanager.measurements.ByteArrayValue;
import org.ogema.core.channelmanager.measurements.Value;
import org.ogema.core.model.units.ConcentrationResource;
import org.ogema.core.resourcemanager.AccessMode;
import org.ogema.core.resourcemanager.AccessPriority;
import org.ogema.driver.hmhl.Constants;
import org.ogema.driver.hmhl.Converter;
import org.ogema.driver.hmhl.HM_hlConfig;
import org.ogema.driver.hmhl.HM_hlDevice;
import org.ogema.driver.hmhl.HM_hlDriver;
import org.ogema.model.sensors.CO2Sensor;

public class CO2Detector extends HM_hlDevice {

	private ConcentrationResource concentration;

	public CO2Detector(HM_hlDriver driver, ApplicationManager appManager, HM_hlConfig config) {
		super(driver, appManager, config);
	}

	public CO2Detector(HM_hlDriver driver, ApplicationManager appManager, DeviceLocator dl) {
		super(driver, appManager, dl);
		addMandatoryChannels();
	}

	@Override
	protected void parseValue(Value value, String channelAddress) {
		byte[] array = null;

		if (value instanceof ByteArrayValue) {
			array = value.getByteArrayValue();
		}
		byte msgtype = array[array.length - 1];
		byte[] msg = ArrayUtils.removeAll(array, array.length - 2, array.length - 1);

		if (type.equals("0056") || type.equals("009F")) {
			if ((msgtype == 0x02 && msg[0] == 0x01) || (msgtype == 0x10 && msg[0] == 0x06) || (msgtype == 0x41)) {
				long lvl = Converter.toLong(msg[2]);

				if (type.equals("009F"))
					System.out.println("Level: " + lvl);
				System.out.println("State: " + lvl);
				concentration.setValue(lvl);
			}
		}
		else if (msgtype == 0x10 && msg[0] == 0x06) {
			// long err = Converter.toLong(msg[3]);
			long state = Converter.toLong(msg[2]);
			String state_str = (state > 2) ? "off" : "smoke-Alarm";

			System.out.println("Level: " + state);
			concentration.setValue(state);
			// String err_str = ((err & 0x80) > 0) ? "low" : "ok";
			System.out.println("State: " + state_str);
		}
		System.out.println("#######################\tCO2\t#############################");
		System.out.println("Concentration: " + concentration.getValue());
	}

	private void addMandatoryChannels() {
		HM_hlConfig attributeConfig = new HM_hlConfig();
		attributeConfig.driverId = hm_hlConfig.driverId;
		attributeConfig.interfaceId = hm_hlConfig.interfaceId;
		attributeConfig.deviceAddress = hm_hlConfig.deviceAddress;
		attributeConfig.channelAddress = "ATTRIBUTE:0001";
		attributeConfig.timeout = -1;
		attributeConfig.resourceName = hm_hlConfig.resourceName + "_HomeMatic_CO2Sensor";
		attributeConfig.chLocator = addChannel(attributeConfig);

		/*
		 * Initialize the resource tree
		 */
		// Create top level resource
		// The connection attribute and its children, current, voltage, power,
		// frequency
		CO2Sensor co2 = resourceManager.createResource(attributeConfig.resourceName, CO2Sensor.class);
//		co2.activate(true);

		concentration = (ConcentrationResource) co2.reading().create();
//		concentration.activate(true);
		//		concentration.setValue(0);
		concentration.requestAccessMode(AccessMode.EXCLUSIVE, AccessPriority.PRIO_HIGHEST);
		co2.activate(true);
	}

	@Override
	protected void unifyResourceName(HM_hlConfig config) {
		config.resourceName += Constants.HM_CO2_RES_NAME + config.deviceAddress.replace(':', '_');
	}

}
