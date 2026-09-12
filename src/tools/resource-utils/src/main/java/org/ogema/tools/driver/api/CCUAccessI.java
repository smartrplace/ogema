package org.ogema.tools.driver.api;

import java.io.IOException;
import java.io.InterruptedIOException;
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
	
	/** Data of a direct link ("Direktverknüpfung") between two device channels as it is configured
	 * on a CCU. See the HomeMatic XML-RPC method {@code getLinks}.
	 */
	public static class HomematicLinkData {
		/** Address of the sending channel, e.g. "LEQ0568335:1"*/
		public String sender;
		/** Address of the receiving channel, e.g. "OEQ2083227:1"*/
		public String receiver;
		/** Link name configured on the CCU, e.g. "TempSens". May be null.*/
		public String name;
		/** Link description configured on the CCU. May be null.*/
		public String description;
		public int flags;
		/** CCU on which the link is configured*/
		public HmLogicInterface iface;
	}

	/** Get the direct links configured on a CCU. This is a pure reading operation on the CCU.
	 * 
	 * @param iface CCU
	 * @param address device or channel address the links shall be reported for, e.g. "OEQ2083227:1".
	 * 		If null or empty all links known to the CCU are returned.
	 * @return all links in which the address given is involved (as sender or as receiver), respectively
	 * 		all links of the CCU if no address is given. An empty list is returned if the CCU is not
	 * 		connected.
	 * @throws IOException if the CCU could not be queried
	 */
	public List<HomematicLinkData> getLinks(HmLogicInterface iface, String address) throws IOException;

	/** Get all direct links configured on a CCU,
	 * see {@link #getLinks(HmLogicInterface, String)}
	 */
	public List<HomematicLinkData> getLinks(HmLogicInterface iface) throws IOException;

	/** Check whether the connection to a CCU is established. Note that direct links can only be
	 * read from a connected CCU, see {@link #getLinks(HmLogicInterface, String)}.
	 *
	 * @param iface CCU
	 * @return true if the CCU is known and connected
	 */
	public boolean isConnected(HmLogicInterface iface);

	/** Get all direct links configured on all CCUs connected. CCUs that cannot be queried are just
	 * skipped, so use {@link #getLinks(HmLogicInterface)} if you need to detect such failures.
	 */
	public List<HomematicLinkData> getAllLinks();
	
	/**
	 * Trigger a reboot of the connected CCU.
	 * 
	 * @param iface CCU interface
	 * @return return value of the reboot command, 0 indicates success.
	 * @throws IOException
	 * @throws InterruptedIOException if the command execution was interrupted
	 */
	public int reboot(HmLogicInterface iface) throws IOException;
	
	/**
	 * Trigger a reboot of the router connecting the CCU.
	 * 
	 * @param iface CCU interface
	 * @return -1 if no router paramaters are configured on iface, otherwise return value of the reboot command (0=success).
	 * @throws IOException
	 * @throws InterruptedIOException if the command execution was interrupted
	 */
	int rebootRouter(HmLogicInterface iface) throws IOException;

}
