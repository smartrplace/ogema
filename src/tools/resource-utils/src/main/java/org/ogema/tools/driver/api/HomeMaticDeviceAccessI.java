package org.ogema.tools.driver.api;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;

public interface HomeMaticDeviceAccessI {
	   //Optional<HomeMaticConnectionI> getConnection(HmDevice toplevelDevice);
	   
	   boolean update(HmDevice device);
}
