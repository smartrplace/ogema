package org.ogema.drivers.homematic.xmlrpc.hl.api;

import java.util.Optional;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;

/**
 *
 * @author jlapp
 */
public interface HomeMaticDeviceAccess {
    
    Optional<HomeMaticConnection> getConnection(HmDevice toplevelDevice);
    
    boolean update(HmDevice device);
    
}
