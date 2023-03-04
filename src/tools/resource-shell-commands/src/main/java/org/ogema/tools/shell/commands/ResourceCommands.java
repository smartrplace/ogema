package org.ogema.tools.shell.commands;

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
				"osgi.command.function=numResources", 
				"osgi.command.function=numSubresources",
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
			@Descriptor("Parent resource")
			Resource parent,
			@Descriptor("New resource name")
			String name,
			@Descriptor("Full resource type, such as 'org.ogema.model.locations.Room', or abbreviated resource type, such as 'Room' for selected common types.")
			String type
			) throws InterruptedException {
		if (parent == null || type.isEmpty() || name.isEmpty()) {
			System.out.println("Type, path and name must not be empty");
			return null;
		}
		startLatch.await(30, TimeUnit.SECONDS);
		final Class<? extends Resource> resType = loadResourceClass(appMan.getAppID().getBundle().getBundleContext(), type);
		if (resType == null) 
			return null;
		if (!parent.exists())
			parent.create();
		final Resource sub = parent.addDecorator(name, resType);
		if (activate)
			sub.activate(false);
		return sub;
	}
	
	@Descriptor("Add a subresource")
	public Resource addSubResource(
			@Descriptor("Activate the new subresource immediately?")
			@Parameter(names= { "-a", "--activate"}, absentValue = "false", presentValue="true")
			boolean activate,
			@Descriptor("Parent resource path")
			String path,
			@Descriptor("New resource name")
			String name,
			@Descriptor("Full resource type, such as 'org.ogema.model.locations.Room', or abbreviated resource type, such as 'Room' for selected common types.")
			String type
			) throws InterruptedException {
		if (type.isEmpty() || path.isEmpty() || name.isEmpty()) {
			System.out.println("Type, path and name must not be empty");
			return null;
		}
		startLatch.await(30, TimeUnit.SECONDS);
		if (path.endsWith("/"))
			path = path.substring(0, path.length()-1);
		if (path.startsWith("/"))
			path = path.substring(1);
		final Resource parent = appMan.getResourceAccess().getResource(path);
		if (parent == null) {
			System.out.println("Parent " + path + " does not exist");
			return null;
		}
		return addSubResource(activate, parent, name, type);
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
	
	@Descriptor("Get the resource at the specified path")
	public Resource getResource(@Descriptor("Resource path") String path) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		return appMan.getResourceAccess().getResource(path);
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
