package org.ogema.drivers.homematic.xmlrpc.hl.api;

import java.util.Map;
import java.util.Optional;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmLogicInterface;
import org.ogema.tools.driver.api.HomeMaticDeviceAccessI;

/**
 *
 * @author jlapp
 */
public interface HomeMaticDeviceAccess extends HomeMaticDeviceAccessI {
    
    Optional<HomeMaticConnection> getConnection(HmDevice toplevelDevice);
    
    boolean update(HmDevice device);
	
	Map<HmLogicInterface, HomeMaticConnection> getConnections();
    
}
