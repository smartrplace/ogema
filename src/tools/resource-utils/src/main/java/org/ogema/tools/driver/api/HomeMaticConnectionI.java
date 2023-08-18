package org.ogema.tools.driver.api;

import java.io.IOException;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmLogicInterface;

public interface HomeMaticConnectionI {
	/** Start installation Mode. This is an extended option to do this compared to using {@link HmLogicInterface#installationMode()}#stateControl()
     * @param on install mode active state.
     * @param time time to remain active in seconds
     * @param mode 1: normal mode, 2: set all MASTER parameters to their default value and delete all links.
     * @throws IOException 
     */    
    void setInstallMode(boolean on, int time, int mode) throws IOException;
	
	/**
     * Flags:
     * <dl>
     * <dt>0x01</dt> <dd>DELETE_FLAG_RESET - reset device before delete</dd>
     * <dt>0x02</dt> <dd>DELETE_FLAG_FORCE - delete even if device is not reachable</dd>
     * <dt>0x04</dt> <dd>DELETE_FLAG_DEFER - delete as soon as device is reachable</dd>
     * </dl>
     * @param address device address (This is the full numeric device id, e.g. 000A1D89B0FEA2. This is 
     * 		the last part of the resource name of the HmDevice, after the last underscore.)
     * @param flags see javadoc
     * @throws IOException
     */
    void deleteDevice(String address, int flags) throws IOException;
}
