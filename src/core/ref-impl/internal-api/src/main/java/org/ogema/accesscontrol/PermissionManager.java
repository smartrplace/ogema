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
package org.ogema.accesscontrol;

import java.io.PrintStream;
import java.security.AccessControlContext;
import java.security.Permission;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.ogema.applicationregistry.ApplicationRegistry;
import org.ogema.core.application.AppID;
import org.ogema.core.application.Application;
import org.ogema.core.channelmanager.ChannelConfiguration;
import org.ogema.core.channelmanager.driverspi.DeviceLocator;
import org.ogema.core.model.Resource;
import org.ogema.core.security.AppPermission;
import org.ogema.core.security.WebAccessManager;
import org.ogema.resourcetree.TreeElement;
import org.osgi.framework.Bundle;
import org.osgi.service.condpermadmin.ConditionalPermissionInfo;
import org.osgi.service.condpermadmin.ConditionalPermissionUpdate;
import org.osgi.service.permissionadmin.PermissionInfo;

/**
 * This class is the entry point to the functionality related to permission handling. It encapsulates the Java/OSGi
 * permissions handling via Conditional Permission Admin and the OGEMA specific access permissions via AccessManager.
 */
public interface PermissionManager {

	/**
	 * Get the single instance of AccessManager which controls user based resource access by the users.
	 * 
	 * @return The AccessManager instance.
	 */
	public AccessManager getAccessManager();

	/**
	 * Update the policy table of the conditional permission admin. The given AppPermission includes a list of
	 * AppPermissionTypes where each of them represents a named entry of the policy table. The AppPermission instance is
	 * obtained by the call to {@link #getPolicies(AppID)}. Multiple entries, positive (permission) or
	 * negative(exception), should be avoided, in order to prevent negative effects on the performance.
	 *
	 * @param perm
	 *            AppPermission that contains the description of the permissions to be granted.
	 * @return true, if successful
	 */
	public boolean installPerms(AppPermission perm);

	/**
	 * Get a list of access policy applied to the given application. It include all access permissions granted to this
	 * application.
	 * 
	 * @param app
	 *            The application whose policy is requested.
	 * @return List of descriptions with an entry for each granted permission.
	 */
	public AppPermission getPolicies(AppID app);

	/**
	 * Install a set of permissions defined as default permissions for all Apps. This method is called if the
	 * administrator requests the change of installed Default policies.
	 * 
	 *            List of Permission where each of them can be provided with an condition as expected by the method
	 *            org.osgi.service.condpermadmin .ConditionalPermissionUpdate.getConditionalPermissionInfos(). For the
	 *            exact format definition of the permission info string see OSGi Service Platform Core Specification,
	 *            The OSGi Alliance, Release 4, Version 4.2
	 */
	public AppPermission setDefaultPolicies();

	/**
	 * Checks if the current access context contains the permission that implies the given permission. The check policy
	 * is: If the call stack contains a class of an ogema application, the protection domain of the last calling
	 * application is used. If no ogema application is contained the standard AccessControlContext without any
	 * modifications is used.
	 * 
	 * @param perm
	 *            The permission to be checked.
	 * @return true if the permission is granted, false otherwise.
	 */
	public boolean handleSecurity(Permission perm);

	/**
	 * Checks if the user and all of the user's parents imply the given permission.
	 * 
	 * @param user
	 *            The user to be checked.
	 * @param perm
	 *            The permission to be checked.
	 * @return true if the permission is granted, false otherwise.
	 */
	public boolean handleSecurity(String user, Permission perm);

	/**
	 * Checks if the given access control context contains the permission that implies the given permission.
	 * 
	 * @param perm
	 *            The permission to be checked.
	 * @param acc
	 *            The context to be checked.
	 * @return true if the permission is granted, false otherwise.
	 */
	boolean handleSecurity(Permission perm, AccessControlContext acc);

	/**
	 * Gets the reference to a global {@link WebAccessManager} instance which can be used get registration information
	 * and for authentication purposes but not to register web resources.
	 * 
	 * @return WebAccessManager reference.
	 */
	public WebAccessManager getWebAccess();

	/**
	 * Return the application specific WebAccessManager for the given app.
	 * 
	 * @param app
	 *            Application for which to return the WebAccessManager.
	 * @return WebAccessManager for that AppID
	 */
	public WebAccessManager getWebAccess(AppID app);
	
	/**
	 * Close the WebAccessManager for a given app.
	 * @param app
	 * @return
	 */
	public boolean closeWebAccess(AppID app);

	/**
	 * Gets the implementation dependent object that support the injection of permissions and the manipulation of the
	 * permission configuration of the system. In a security implementation that bases on the OSGi security this object
	 * could be the ConditionalPermissionAdmin.
	 * 
	 * @return The object reference.
	 */
	public Object getSystemPermissionAdmin();

	/**
	 * Gets the {@link ApplicationRegistry}.
	 * 
	 * @return
	 */
	public ApplicationRegistry getApplicationRegistry();

	/**
	 * Creates an AppPermission instance that contains the application specific policies. This method is called to get
	 * the current policy configuration of the application or to manipulate it.
	 * 
	 * @param location
	 *            The location string of the bundle, that contains the application.
	 * @return The reference to the AppPermission object.
	 */
	public AppPermission createAppPermission(String location);

	/**
	 * Checks the permission to create an OGEMA resource by the given application. The resource to be created is
	 * specified by its type class and resource path. The specified count gives the number of the resources from this
	 * type that are already created by the same application.
	 * 
	 * @param app
	 *            The Application object which is the owner of the resource.
	 * @param type
	 *            The type class of the resource.
	 * @param name
	 *            The unique path of the resource.
	 * @param count
	 *            The number of the resources of the demanded type that are already owned by the specified application.
	 * @return true if the permission is granted, false otherwise.
	 */
	public boolean checkCreateResource(Application app, Class<? extends Resource> type, String name, int count);

	/**
	 * Creates a {@link ResourceAccessRights} object that contains and caches the permission of an application to access
	 * to the specified resource. The injected permission is determined at the calling time and doesn't change even
	 * tough the permission configuration in the system changes.
	 * 
	 * @param app
	 *            The application owning the access permission.
	 * @param el
	 *            The {@link TreeElement} object, that represents the resource, the access right are to be evaluated
	 *            for.
	 * @return The ResourceAccessRights object.
	 */
	public ResourceAccessRights getAccessRights(Application app, TreeElement el);
	
	public ResourceAccessRights getAccessRights(Application app, TreeElement el, String user);

	/**
	 * Checks the permission of the given application to delete the resource specified with its TreeElement object.
	 * 
	 * @param app
	 *            The application that demands the delete access to the resource.
	 * @param te
	 *            The {@link TreeElement} object that represent the resource that is to be deleted.
	 * @return true if the permission is granted, false otherwise.
	 */
	public boolean checkDeleteResource(Application app, TreeElement te);

	/**
	 * Gets an AppPermission instance that encapsulates the default permission currently set in the system.
	 * 
	 * @return The AppPermission reference
	 */
	public AppPermission getDefaultPolicies();

	/**
	 * Prints the policies currently configured out to the specified PrintStream.
	 * 
	 * @param os
	 *            The PrintStream, the policies printed out to.
	 */
	public void printPolicies(PrintStream os);

	/**
	 * Checks the Permission to create a channel to a low level driver via the Channel Manager. The application the
	 * permission is granted to is determined as the latest calling application in the call stack.
	 * 
	 * @see PermissionManager#handleSecurity PermissionManager.handleSecurity
	 * 
	 * @param configuration
	 *            The description object of the channel to be created.
	 * @param deviceLocator
	 *            The description object of the device, the channel connected to.
	 * @return true if the Permission is granted, false otherwise.
	 */
	public boolean checkAddChannel(ChannelConfiguration configuration, DeviceLocator deviceLocator);

	/**
	 * Checks the Permission to delete a channel that is created before. The application the permission is granted to is
	 * determined as the latest calling application in the call stack.
	 * 
	 * @see PermissionManager#handleSecurity PermissionManager.handleSecurity
	 * 
	 * @param configuration
	 *            The description object of the channel to be deleted.
	 * @param deviceLocator
	 *            The description object of the device, the channel connected to.
	 * @return true if the Permission is granted, false otherwise.
	 */
	public boolean checkDeleteChannel(ChannelConfiguration configuration, DeviceLocator deviceLocator);

	/**
	 * Gets an AccessControlContext instance that includes the protection domain of the specified bundle only.
	 * 
	 * @param class1
	 * 
	 * @return AccessControlContext with bundles ProtectionDomain
	 */
	public AccessControlContext getBundleAccessControlContext(Class<?> class1);

	/**
	 * Removes a permission that was granted to the specified bundle before. The permission to be removed is specified
	 * by the name of the permission class and the optional parameter filterString and actions.
	 *
	 * @param bundle
	 *            The bundle its policy should be reduced.
	 * @param permissionClassName
	 *            The name of the permission to be removed.
	 * @param filterString
	 *            The filter of the permission to be removed.
	 * @param actions
	 *            The actions of the permission.
	 * @return true, if the reduction of the policy could be achieved by removing of a permission from the policy table
	 *         or false, if the reduction was achievable by adding of negative policy only.
	 */
	public boolean removePermission(Bundle bundle, String permissionClassName, String filterString, String actions);

	/**
	 * Sets an access context as the current threads relevant AccessControlContext. Dependent on the implementation the
	 * permission manager can decide, if it uses the new context, the default context or any other implementation
	 * specific context for the security checks.
	 *
	 * @param acc
	 *            the new access context
	 */
	public void setAccessContext(AccessControlContext acc);

	/**
	 * Resets the access context that is set before as the current threads relevant AccessControlContext. After the
	 * reset the permission manager uses the default access control context for security checks.
	 */
	public void resetAccessContext();

	/**
	 * Provides a map of policies applied to the bundle of the specified location.
	 * 
	 * @param bLoc
	 *            The location string as it returned by Bundle.getLocation().
	 * @return A map where the keys are the unique names of the policies and the values the policy itself coded as a
	 *         ConditionalPermissionInfo object.
	 */
	public Map<String, ConditionalPermissionInfo> getGrantedPerms(String bLoc);

	/**
	 * Checks if a policy specified with its permission type, permission filter and actions, is applied to all bundles.
	 * 
	 * @param permType
	 *            Fully qualified name of the permission class.
	 * @param filter
	 *            The permission name string, that is used as filter by the permission class.
	 * @param actions
	 *            Comma separated list of the actions as they are defined by the permission class.
	 * @return
	 */
	public boolean isDefaultPolicy(String permType, String filter, String actions);

	/**
	 * Checks if the framework is started with activated security.
	 * 
	 * @return
	 */
	public boolean isSecure();

	/**
	 * @deprecated ConditionalPermissionUpdate does not work that way!
	 *             ConditionalPermissionUpdate#getConditionalPermissionInfos returns
	 *             the list of CPIs as it was when the CPU was created.
	 * 
	 * Removes a permission that was granted to the specified bundle before. The permission to be removed is specified
	 * by the name of the permission class and the optional parameter filterString and actions. In case of removing
	 * multiple permissions this method is more performance than
	 * {@link PermissionManager#removePermission(Bundle, String, String, String)}. If this method is called multiple
	 * times for the same bundle the ConditionalPermissionUpdate argument is initialized before the first call and the
	 * same reference is given each call.
	 *
	 * @param cpu
	 *            The ConditionalPermissionUpdate object
	 * @param bundle
	 *            The reference of the bundle, that owns the permission to be removed.
	 * @param permissionClassName
	 *            Type of the permission to be removed
	 * @param filterString
	 *            The name string of the permission to be removed
	 * @param actions
	 *            The actions to be removed from the permission
	 * @return true, if the reduction of the policy could be achieved by removing of a permission from the policy table
	 *         or false, if the reduction was achievable by adding of negative policy only.
	 */
	@Deprecated
	public boolean removePermissionManual(ConditionalPermissionUpdate cpu, Bundle bundle, String permissionClassName,
			String filterString, String actions);
	
	/**
	 * Bulk removal method.
	 * @param bundle
	 *            The reference of the bundle, that owns the permission to be removed.
	 * @param permissions
	 *            Permissions to remove.
	 * @return List of permissions for which the remove call actually changed the
	 *         active permission set.
	 */
	default public List<PermissionInfo> removePermissions(final Bundle bundle, Collection<PermissionInfo> permissions) {
		throw new UnsupportedOperationException();
	}

	public boolean checkWebAccess(AppID accessor, AppID access);
}
