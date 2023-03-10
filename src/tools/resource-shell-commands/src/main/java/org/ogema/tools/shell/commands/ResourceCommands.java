package org.ogema.tools.shell.commands;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.model.Resource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(
		service=ResourceCommands.class,
		property= {
				"osgi.command.scope=resource",

				"osgi.command.function=addSubResource",
				"osgi.command.function=createResource",
				"osgi.command.function=getResource",
				"osgi.command.function=getResources",
				"osgi.command.function=getSubResource",
				"osgi.command.function=getSubResources",
				"osgi.command.function=numResources", 
				"osgi.command.function=numSubresources",
				"osgi.command.function=optionalElements",
				"osgi.command.function=resourcesBySize",
				"osgi.command.function=subResources",
				"osgi.command.function=toplevelResources"
		}
)
public class ResourceCommands {
	
	private static final String[] CLASS_PREFIXES = {
		"Schedule", "org.ogema.core.model.schedule.", // "*Schedule", "org.ogema.core.model.schedule.*Schedule",
		"ArrayResource", "org.ogema.core.model.array.",  // "*ArrayResource", "org.ogema.core.model.schedule.*ArrayResource",
		"Resource", "org.ogema.core.model.simple.",
		"Sensor", "org.ogema.model.sensors.",   // "*Sensor", "org.ogema.model.sensors.*Sensor",
		"Actor", "org.ogema.model.actors.",
		"Switch", "org.ogema.model.actors.",
		"Room", "org.ogema.model.locations.",
		"Building", "org.ogema.model.locations.",
		"BuildingPropertyUnit", "org.ogema.model.locations.",
		"Meter", "org.ogema.model.metering.",
		"Data", "org.ogema.model.prototypes.",
		"PhysicalElement", "org.ogema.model.prototypes.",
		"Thermostat", "org.ogema.model.devices.buildingtechnology.",
		"Light", "org.ogema.model.devices.buildingtechnology.",
		"SwitchBox", "org.ogema.model.devices.sensoractordevices.",
		"SensorDevice", "org.ogema.model.devices.sensoractordevices.",
		"Storage", "org.ogema.model.devices.storage.",
		"ChargingPoint", "org.ogema.model.devices.storage.",
		"ElectricityChargingStation", "org.ogema.model.devices.storage.",
		"Vehicle", "org.ogema.model.devices.vehicles.",
		"Generator", "org.ogema.model.devices.generators.",
		"Plant", "org.ogema.model.devices.generators.",
		"HeatPump", "org.ogema.model.devices.generators.",
		"Heater", "org.ogema.model.devices.generators.",
		"ConnectionBox", "org.ogema.model.devices.connectiondevices.",
	};
	private static final String[] RESOURCE_METHODS = {"addDecorator", "addOptionalElement", "create", "getLocationResource", "getParent", "getSubResource", "setAsReference", };
	private final CountDownLatch startLatch = new CountDownLatch(1);
	private volatile ServiceRegistration<Application> appService;
	private ApplicationManager appMan;
	

	@Activate
	protected void activate(BundleContext ctx) {
		final Application app = new Application() {

			@Override
			public void start(ApplicationManager appManager) {
				appMan = appManager;
				startLatch.countDown();
			}

			@Override
			public void stop(AppStopReason reason) {
				appMan = null;
			}
			
		};
		this.appService = ctx.registerService(Application.class, app, null);
	}
	
	@Deactivate 
	protected void deactivate() {
		final ServiceRegistration<Application> appService = this.appService;
		if (appService != null) {
			try {
				appService.unregister();
			} catch (Exception ignore) {}
			this.appService = null;
			this.appMan = null;
		} 
				
	}
	
	@Descriptor("Count the number of resources, optionally for a specific resource type")
	public int numResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Count only toplevel resources") 
			@Parameter(names = { "-top" }, absentValue = "false", presentValue="true") 
			boolean toplevelOnly
			) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		Class<? extends Resource> clazz = null;
		if (!type.isEmpty()) {
			clazz = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
			if (clazz == null)
				return 0;
		}
		if (toplevelOnly)
			return appMan.getResourceAccess().getToplevelResources(clazz).size();
		else
			return appMan.getResourceAccess().getResources(clazz).size();
	}
	
	@Descriptor("Get the number of resources matching a path expression.")
	public int numResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Make the path expression case sensitive")
			@Parameter(names= {"-c", "--case-sensitive"}, absentValue="false", presentValue="true")
			boolean caseSensitive,
			@Descriptor("Filter for active resources")
			@Parameter(names= {"-a", "--active"}, absentValue="false", presentValue="true")
			boolean active,
			@Descriptor("Include virtual subresources")
			@Parameter(names= {"-v", "--virtual"}, absentValue="false", presentValue="true")
			boolean virtual,
			@Descriptor("A path expression, such as 'myResource/*/reading', or 'my*/*'")
			String pathExpression) throws InterruptedException {
		return getResources(type, caseSensitive, active, virtual, pathExpression).size();
	}
	
	@Descriptor("Count the number of subresources for a specific resource")
	public int numSubresources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Resource") Resource base) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		if (base == null)
			return 0;
		Class<? extends Resource> clazz = null;
		if (!type.isEmpty()) {
			clazz = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
			if (clazz == null)
				return 0;
		}
		if (clazz == null)
			return base.getDirectSubResources(true).size();
		else {
			final AtomicInteger cnt = new AtomicInteger(0);
			countSubresources(base, clazz, cnt);
			return cnt.get();
		}
	}
	
	@Descriptor("Count the number of subresources for a specific resource")
	public int numSubresources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Resource path") String path) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		final Resource base = appMan.getResourceAccess().getResource(path);
		if (base == null)
			System.out.println("Resource not found: " + path);
		return numSubresources(type, base);
	}

	@Descriptor("Create a new resource")
	public Resource createResource(
			@Descriptor("Resource path")
			String path,
			@Descriptor("Full resource type, such as 'org.ogema.model.locations.Room', or abbreviated resource type, such as 'Room' for selected common types.")
			String type
			) throws InterruptedException {
		if (type.isEmpty() || path.isEmpty()) {
			System.out.println("Type and path must not be empty");
			return null;
		}
		startLatch.await(30, TimeUnit.SECONDS);
		final Resource existing = appMan.getResourceAccess().getResource(path);
		if (existing != null) {
			if (existing.exists()) {
				System.out.println("Resource " + path + " already exists: " + existing);
				return null;
			}
		}
		final Class<? extends Resource> resType = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
		if (resType == null) 
			return null;
		return appMan.getResourceManagement().createResource(path, resType);
	}
	
	// TODO option to set value
	@Descriptor("Add a subresource")
	public Resource addSubResource(
			@Descriptor("Activate the new subresource immediately?")
			@Parameter(names= { "-a", "--activate"}, absentValue = "false", presentValue="true")
			boolean activate,
			@Descriptor("Full resource type, such as 'org.ogema.model.locations.Room', or abbreviated resource type, such as 'Room' for selected common types. "
					+ "If absent, the subresource must exist as an optional element in the model declaration of the parent resource.")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Parent resource")
			Resource parent,
			@Descriptor("New resource name. Paths are allowed as well if all intermediate resources are declared in the model. E.g. 'reading/program' for a sensor.")
			String name
			) throws InterruptedException {
		if (parent == null || name.isEmpty()) {
			System.out.println("Path and name must not be empty");
			return null;
		}
		startLatch.await(30, TimeUnit.SECONDS);
		final String[] pathComponents = name.split("/");
		Resource sub = parent;
		final int iterationBoundary = type.isEmpty() ? pathComponents.length : pathComponents.length-1;
		for (int idx=0; idx<iterationBoundary; idx++) { // all but the last subresource must be present in the model, for the last one a type can be provided
			final String component = pathComponents[idx];
			final Resource sub2 = sub.getSubResource(component);
			if (sub2 == null) {
				System.out.println("Resource " + sub +" does not have a declared child " + component);
				return null;
			}
			sub = sub2;
		}
		if (type.isEmpty() && !sub.exists())
			sub.create();
		if (!type.isEmpty()) {
			final Class<? extends Resource> resType = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
			if (resType == null) 
				return null;
			if (!sub.exists())
				sub.create();
			sub = sub.addDecorator(pathComponents[pathComponents.length-1], resType);
		} 
		if (activate)
			sub.activate(false);
		return sub;
	}
	
	@Descriptor("Add a subresource")
	public Resource addSubResource(
			@Descriptor("Activate the new subresource immediately?")
			@Parameter(names= { "-a", "--activate"}, absentValue = "false", presentValue="true")
			boolean activate,
			@Descriptor("Full resource type, such as 'org.ogema.model.locations.Room', or abbreviated resource type, such as 'Room' for selected common types. "
					+ "If absent, the subresource must exist as an optional element in the model declaration of the parent resource.")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Parent resource path")
			String path,
			@Descriptor("New resource name")
			String name
			) throws InterruptedException {
		if (path.isEmpty() || name.isEmpty()) {
			System.out.println("Path and name must not be empty");
			return null;
		}
		final Resource parent = this.getResource(path);
		if (parent == null)
			return null;
		return addSubResource(activate, type, parent, name);
	}
	
	@Descriptor("Get a subresource")
	public Resource getSubResource(
			@Descriptor("Full resource type, such as 'org.ogema.model.locations.Room', or abbreviated resource type, such as 'Room' for selected common types. "
					+ "If absent, the subresource must be present already. It can be virtual, though. If a type is provided and the subresource does not exist,"
					+ " it is created as a virtual resource and returned.")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Parent resource")
			Resource parent,
			@Descriptor("Subresource name. Paths are allowed as well, e.g. 'reading/program' for a sensor.")
			String name
			) throws InterruptedException {
		if (parent == null || name.isEmpty()) {
			System.out.println("Path and name must not be empty");
			return null;
		}
		startLatch.await(30, TimeUnit.SECONDS);
		final String[] pathComponents = name.split("/");
		Resource sub = parent;
		final int iterationBoundary = type.isEmpty() ? pathComponents.length : pathComponents.length-1;
		for (int idx=0; idx<iterationBoundary; idx++) { // all but the last subresource must be present in the model, for the last one a type can be provided
			final String component = pathComponents[idx];
			final Resource sub2 = sub.getSubResource(component);
			if (sub2 == null) {
				System.out.println("Resource " + sub +" does not have a subresource " + component);
				return null;
			}
			sub = sub2;
		}
		if (!type.isEmpty()) {
			final Class<? extends Resource> resType = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
			if (resType == null) 
				return null;
			if (!sub.exists())
				sub.create();
			sub = sub.getSubResource(pathComponents[pathComponents.length-1], resType);
		}
		return sub;
	}
	
	@Descriptor("Get a subresource")
	public Resource getSubResource(
			@Descriptor("Full resource type, such as 'org.ogema.model.locations.Room', or abbreviated resource type, such as 'Room' for selected common types. "
					+ "If absent, the subresource must be present already. It can be virtual, though. If a type is provided and the subresource does not exist,"
					+ " it is created as a virtual resource and returned.")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Parent resource path")
			String parentPath,
			@Descriptor("Subresource name. Paths are allowed as well, e.g. 'reading/program' for a sensor.")
			String name
			) throws InterruptedException {
		if (parentPath.isEmpty() || name.isEmpty()) {
			System.out.println("Path and name must not be empty");
			return null;
		}
		final Resource parent = this.getResource(parentPath);
		if (parent == null)
			return null;
		return getSubResource(type, parent, name);
	}
	
	@SuppressWarnings("unchecked")
	@Descriptor("Get toplevel resources")
	public List<Resource> toplevelResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		Class<? extends Resource> clazz = null;
		if (!type.isEmpty()) {
			clazz = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
			if (clazz == null)
				return Collections.emptyList();
		}
		return (List<Resource>) appMan.getResourceAccess().getToplevelResources(clazz);
	}
	
	
	@SuppressWarnings("unchecked")
	@Descriptor("Get sub resources of a specified resource")
	public List<Resource> subResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Search for recursive subresources?")
			@Parameter(names= { "-r", "--recursive"}, absentValue = "false", presentValue="true")
			boolean recursive,
			@Descriptor("Resource") Resource resource
			) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		Class<? extends Resource> clazz = null;
		if (!type.isEmpty()) {
			clazz = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
			if (clazz == null)
				return Collections.emptyList();
		}
		if (clazz == null)
			return resource.getDirectSubResources(recursive);
		else
			return (List<Resource>) resource.getSubResources(clazz, recursive);
	}
	
	@Descriptor("Get sub resources of a specified resource")
	public List<Resource> subResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Search for recursive subresources?")
			@Parameter(names= { "-r", "--recursive"}, absentValue = "false", presentValue="true")
			boolean recursive,
			@Descriptor("Resource path") String path
			) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		final Resource base = appMan.getResourceAccess().getResource(path);
		if (base == null) {
			System.out.println("Resource not found: " + path);
			return Collections.emptyList();
		}
		return subResources(type, recursive, base);
	}
	
	@Descriptor("This is an alias for the subResources command")
	public List<Resource> getSubResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Search for recursive subresources?")
			@Parameter(names= { "-r", "--recursive"}, absentValue = "false", presentValue="true")
			boolean recursive,
			@Descriptor("Resource") Resource resource
			) throws InterruptedException {
		return subResources(type, recursive, resource);
	}
	
	@Descriptor("This is an alias for the subResources command")
	public List<Resource> getSubResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Search for recursive subresources?")
			@Parameter(names= { "-r", "--recursive"}, absentValue = "false", presentValue="true")
			boolean recursive,
			@Descriptor("Resource path") String path
			) throws InterruptedException {
		return subResources(type, recursive, path);
	}
	
	@Descriptor("Get the resource at the specified path")
	public Resource getResource(@Descriptor("Resource path") String path) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		if (path.endsWith("/"))
			path = path.substring(0, path.length()-1);
		if (path.startsWith("/"))
			path = path.substring(1);
		final Resource parent = appMan.getResourceAccess().getResource(path);
		if (parent == null) {
			System.out.println("Resource " + path + " does not exist");
			return null;
		}
		return parent;
	}
	
	@SuppressWarnings("unchecked")
	@Descriptor("Get resources of a specified type.")
	public List<Resource> getResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		Class<? extends Resource> clazz = null;
		if (!type.isEmpty()) {
			clazz = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
			if (clazz == null)
				return Collections.emptyList();
		}
		return (List<Resource>) appMan.getResourceAccess().getResources(clazz);
	}
	
	// here one could use Java pattern matching, but this is more cumbersome to use 
	@SuppressWarnings("unchecked")
	@Descriptor("Get resources based on a path expression.")
	public List<Resource> getResources(
			@Descriptor("Resource type (full class name, or abbreviated resource type, such as 'Room' for selected common types)")
			@Parameter(names= { "-t", "--type"}, absentValue = "")
			String type,
			@Descriptor("Make the path expression case sensitive")
			@Parameter(names= {"-c", "--case-sensitive"}, absentValue="false", presentValue="true")
			boolean caseSensitive,
			@Descriptor("Filter for active resources")
			@Parameter(names= {"-a", "--active"}, absentValue="false", presentValue="true")
			boolean active,
			@Descriptor("Include virtual subresources")
			@Parameter(names= {"-v", "--virtual"}, absentValue="false", presentValue="true")
			boolean virtual,
			@Descriptor("A path expression, such as 'myResource/*/reading' or 'my*/*'")
			String pathExpression) throws InterruptedException {
		pathExpression = pathExpression.trim();
		if (pathExpression.isEmpty())
			return getResources(type);
		if (pathExpression.startsWith("/"))
			pathExpression = pathExpression.substring(1);
		if (pathExpression.endsWith("/"))
			pathExpression = pathExpression.substring(0, pathExpression.length() - 1);
		if (!caseSensitive)
			pathExpression = pathExpression.toLowerCase();
		startLatch.await(30, TimeUnit.SECONDS);
		Class<? extends Resource> clazz = null;
		if (!type.isEmpty()) {
			clazz = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
			if (clazz == null)
				return Collections.emptyList();
		}
		final String[] pathComponents = pathExpression.trim().split("/");
		final String first = pathComponents[0];
		final int wildcardIdx = first.indexOf("*");
		List<Resource> currentResources;
		if (wildcardIdx < 0 && caseSensitive) {
			final Resource base = appMan.getResourceAccess().getResource(first);
			if (base == null || 
					(pathComponents.length == 1 && clazz != null && !clazz.isAssignableFrom(base.getResourceType())) ||
					(!virtual && !base.exists())) {
				return Collections.emptyList();
			}
			currentResources = new ArrayList<>(2);
			currentResources.add(base);
		} else if (first.lastIndexOf("*") == wildcardIdx) {
			currentResources = (List<Resource>) appMan.getResourceAccess().getToplevelResources(pathComponents.length > 1 ? null : clazz);
			if (!"*".equals(first)) {
				final List<Resource> matches = new ArrayList<>(currentResources.size());
				for (Resource r: currentResources) {
					if (matches(first, wildcardIdx, r.getName(), caseSensitive))
						matches.add(r);
				}
				currentResources = matches;
			}			
		} else {
			throw new IllegalArgumentException("Path expression not supported: " + pathExpression);
		}
		for (int idx=1; idx<pathComponents.length; idx++) {
			final String component = pathComponents[idx];
			final int wildcardIdxSub = component.indexOf("*");
			if (wildcardIdxSub != component.lastIndexOf("*"))
				throw new IllegalArgumentException("Path expression not supported: " + component);
			final List<Resource> subs = new ArrayList<>();
			final boolean isFinalLevel = pathComponents.length == idx+1 && clazz != null;
			for (Resource r: currentResources) {
				if (wildcardIdxSub < 0 && caseSensitive) {
					final Resource sub = r.getSubResource(component);
					if (sub != null && (!isFinalLevel || clazz.isAssignableFrom(sub.getResourceType())) || (!virtual && !sub.exists())) 
						subs.add(sub);
				} else {
					final List<Resource> localSubs = (List<Resource>) (isFinalLevel ? r.getSubResources(clazz, false) : r.getSubResources(false));
					for (Resource sub: localSubs) {
						if (matches(component, wildcardIdx, sub.getName(), caseSensitive) && (virtual || sub.exists()))
							subs.add(sub);
					}
				}
			}
			currentResources = subs;
		}
		if (active && !currentResources.isEmpty()) {
			final List<Resource> activeResources= new ArrayList<>(currentResources.size());
			for (Resource r: currentResources) {
				if (r.isActive())
					activeResources.add(r);
			}
			currentResources = activeResources;
		}
		return currentResources;
	}
	
	private static boolean matches(String pattern, int wildcardIdx, String name, boolean caseSensitive) {
		if (!caseSensitive) // we assume that pattern is already in lower case
			name = name.toLowerCase();
		if ("*".equals(pattern))
			return true;
		if (wildcardIdx == 0)
			return name.endsWith(pattern.substring(1));
		if (wildcardIdx == pattern.length() - 1)
			return name.startsWith(pattern.substring(0, pattern.length() - 1));
		if (wildcardIdx < 0)
			return name.equals(pattern);
		return name.startsWith(pattern.substring(0, wildcardIdx)) && name.endsWith(pattern.substring(0, wildcardIdx+1));
	}
	
	@Descriptor("Get toplevel resources sorted by the number of subresources")
	public LinkedHashMap<Resource, Integer> resourcesBySize() throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		final LinkedHashMap<Resource, Integer> resourcesBySize = new LinkedHashMap<>();
		final List<Resource> resources = appMan.getResourceAccess().getToplevelResources(null);
		if (resources.isEmpty())
			return resourcesBySize;
		final Map<Resource, Integer> map = new HashMap<>();
		for (Resource resource: resources) {
			map.put(resource, resource.getDirectSubResources(true).size());
		}
		// XXX inefficient sorting...
		Map.Entry<Resource, Integer> nextCandidate = null;
		int candidateSize = -1;
		while (resourcesBySize.size() < map.size()) {
			for (Map.Entry<Resource, Integer> entry : map.entrySet()) {
				if (entry.getValue() > candidateSize && !resourcesBySize.containsKey(entry.getKey())) {
					nextCandidate = entry;
					candidateSize = entry.getValue();
				}
			}
			resourcesBySize.put(nextCandidate.getKey(), nextCandidate.getValue());
			nextCandidate = null;
			candidateSize = -1;
		}
		return resourcesBySize;
	}
	
	@Descriptor("Get declared optional elements (child resources) of a resource type")
	@SuppressWarnings("unchecked")
	public Map<String, Class<? extends Resource>> optionalElements(@Descriptor("Resource type") Class<? extends Resource> type) {
		if (type == null)
			return null;
		final Method[] methods = type.getMethods();
		final Map<String, Class<? extends Resource>> children = new HashMap<>();
		outer: for (Method m: methods) {
			final String name = m.getName();
			final Class<?> returnType = m.getReturnType();
			if (!Resource.class.isAssignableFrom(returnType))
				continue;
			for (String knownMethod: RESOURCE_METHODS) {
				if (name.equals(knownMethod))
					continue outer;
			}
			children.put(name, (Class<? extends Resource>) returnType);
		}
		return children;
	}
	
	@Descriptor("Get declared optional elements (child resources) of a resource type")
	public Map<String, Class<? extends Resource>> optionalElements(@Descriptor("A resource") Resource r) {
		return optionalElements(r.getResourceType());
	}
	
	@Descriptor("Get declared optional elements (child resources) of a resource type")
	public Map<String, Class<? extends Resource>> optionalElements(@Descriptor("Resource type fully qualified name or short form") String type) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		return optionalElements(loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type));
	}
	
	private static void countSubresources(final Resource r, final Class<? extends Resource> type, final AtomicInteger cnt) {
		for (final Resource sub : r.getDirectSubResources(false)) {
			if (type.isAssignableFrom(sub.getResourceType()))
				cnt.incrementAndGet();
			if (sub.isReference(false))
				return;
			countSubresources(sub, type, cnt);
		}
	}
	
	private static Class<? extends Resource> loadResourceClass(final BundleContext ctx, final String className) {
		if (className == null || className.isEmpty())
			return null;
		if (className.indexOf(".") < 0) {
			for (int idx=0; idx<CLASS_PREFIXES.length/2; idx++) {
				final String name = CLASS_PREFIXES[2 * idx];
				if (className.endsWith(name)) {
					final Class<? extends Resource> type = loadFullResourceClass(ctx, CLASS_PREFIXES[2*idx+1] + className, false);
					if (type != null)
						return type;
				}
			}
		}
		return loadFullResourceClass(ctx, className, true);
	}
	
    @SuppressWarnings("unchecked")
    private static Class<? extends Resource> loadFullResourceClass(final BundleContext ctx, final String className, final boolean checkOtherBundles) {
    	try {
	 	    final Class<?> clzz = Class.forName(className);
		    if (Resource.class.isAssignableFrom(clzz))
			    return (Class<? extends Resource>) clzz;
	    } catch (ClassNotFoundException expected) {}
    	if (!checkOtherBundles)
    		return null;
	    for (Bundle b : ctx.getBundles()) {
		    final ClassLoader loader = b.adapt(BundleWiring.class).getClassLoader();
		    if (loader == null)
		    	continue;
		    try {
			    final Class<?> clzz = loader.loadClass(className);
			    if (Resource.class.isAssignableFrom(clzz))
				    return (Class<? extends Resource>) clzz;
		    } catch (ClassNotFoundException expected) {}
	    } 
	    System.out.println("Resource type " + className + " not found.");
	    return null;
    }
	
    
    

}
