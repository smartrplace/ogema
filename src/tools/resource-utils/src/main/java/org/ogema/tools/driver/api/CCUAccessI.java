package org.ogema.tools.driver.api;

import java.io.IOException;
import java.util.List;

import org.ogema.drivers.homematic.xmlrpc.hl.types.HmLogicInterface;

public interface CCUAccessI {
	/** Get HomeMaticConnectionI to trigger special teach-in mode and reset devices
	 * 
	 * @param iface
	 * @return
	 */
	public HomeMaticConnectionI getConnection(HmLogicInterface iface);
	
	/**
	 * Get the addresses of all devices lost on all known CCUs
	 *
	 * @return List of addresses for devices known to RFD daemon.
	 */
	public List<String> getLostDevices();
	
	/**
	 * Get the addresses of all devices lost on a certain CCU
	 *
	 * @return List of addresses for devices known to RFD daemon.
	 * @throws IOException 
	 */
	public List<String> getLostDevices(HmLogicInterface iface) throws IOException;
	
	/**
	 * Get the addresses of all devices known to the RFD process on the given
	 * CCU.
	 *
	 * @param iface
	 * @return List of addresses for devices known to RFD daemon.
	 * @throws IOException
	 */
	public List<String> listRfdDevices(HmLogicInterface iface) throws IOException;

	/** Delete a faulty entry
	 * 
	 * @param iface
	 * @param addr
	 * @return the exit value of the subprocess represented by this Process object. By convention, the value 0 indicates normal termination.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public int deleteRfdDevice(HmLogicInterface iface, String addr) throws IOException, InterruptedException;

	public static class HomematicConnectionData {
		public String address;
		public boolean isLost;
		public HmLogicInterface iface;
	}
	
	public List<HomematicConnectionData> getConnectionsData();
	
	public List<HomematicConnectionData> getConnectionsData(HmLogicInterface iface);
	
	/**
	 * Trigger a reboot of the connected CCU.
	 * 
	 * @param iface CCU interface
	 * @return return value of the reboot command, 0 indicates success.
	 * @throws IOException
	 */
	public int reboot(HmLogicInterface iface) throws IOException;

}
