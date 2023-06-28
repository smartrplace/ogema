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
package org.ogema.drivers.homematic.xmlrpc.hl.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.ogema.core.model.Resource;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;

/**
 * Used by {@link DeviceHandler} implementations to communicate with its HomeMatic
 * logic interface and to setup OGEMA specific device/resource relations.
 *
 * @author jlapp
 */
public interface HomeMaticConnection {

    /**
     * Register an event listener with this connection.
     * @param l event listener
     */
    void addEventListener(HmEventListener l);

    /**
     * Returns the HmDevice element controlling the given OGEMA resource, as
     * configured with {@link #registerControlledResource }
     *
     * @param ogemaDevice
     * @return HomeMatic device resource controlling the given resource or
     * null.
     * @see #registerControlledResource(org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice, org.ogema.core.model.Resource) 
     */
    @SuppressWarnings(value = "rawtypes")
    HmDevice findControllingDevice(Resource ogemaDevice);

    /**
     * Finds a channel resource for a given device.
     * @param device top level device
     * @param channelAddress 
     * @return device channel with given address or null.
     */
    HmDevice getChannel(HmDevice device, String channelAddress);

    /**
     * Returns the resource representing the HomeMatic device the channel belongs to.
     * If called with a top level device resource, return the argument.
     * @param channel
     * @return top level device resource.
     */
    HmDevice getToplevelDevice(HmDevice channel);

    /**
     * Calls the {@code addLink} method of the HomeMatic logic interface.
     * @param sender homematic address of the sending device.
     * @param receiver homematic address of the receiving device.
     * @param name user defined name for the link.
     * @param description link description.
     */
    void performAddLink(String sender, String receiver, String name, String description);
    
    /**
     * Calls the {@code removeLink} method of the HomeMatic logic interface.
     * @param sender homematic address of the sending device.
     * @param receiver homematic address of the receiving device.
     */
    void performRemoveLink(String sender, String receiver);
    
    List<Map<String, Object>> performGetLinks(String address, int flags);

    void performPutParamset(String address, String set, Map<String, Object> values);

    void performSetValue(String address, String valueKey, Object value);

    /**
     * Configure a control relationship between the homematic device and a resource
     * that can be retrieved by using {@link #findControllingDevice }.
     * A device handler should call this method for every device resource that
     * it creates, so that the control relationship can be retrieved by
     * other device handlers.
     * 
     * @param channel resource of a homematic device channel.
     * @param ogemaDevice resource controlled by the homematic device.
     */
    void registerControlledResource(HmDevice channel, Resource ogemaDevice);

    void removeEventListener(HmEventListener l);
    
    <T> T getValue(String address, String value_key) throws IOException;
    
    Map<String, Object> getParamset(String address, String set) throws IOException;
    
    Map<String, ParameterDescription<?>> getParamsetDescription(String address, String set) throws IOException;
	
	List<DeviceDescription> listRemoteDevices() throws IOException;
	
	String getConnectionUrl();
	
	boolean isConnected();
	
	/**
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
     * @param address device address
     * @param flags see javadoc
     * @throws IOException
     */
    void deleteDevice(String address, int flags) throws IOException;
    
}
