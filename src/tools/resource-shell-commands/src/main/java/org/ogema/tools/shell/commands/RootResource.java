package org.ogema.tools.shell.commands;

import java.util.ArrayList;
import java.util.List;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.model.Resource;
import org.ogema.core.resourcemanager.AccessMode;
import org.ogema.core.resourcemanager.AccessModeListener;
import org.ogema.core.resourcemanager.AccessPriority;
import org.ogema.core.resourcemanager.InvalidResourceTypeException;
import org.ogema.core.resourcemanager.NoSuchResourceException;
import org.ogema.core.resourcemanager.ResourceAccess;
import org.ogema.core.resourcemanager.ResourceAlreadyExistsException;
import org.ogema.core.resourcemanager.ResourceGraphException;
import org.ogema.core.resourcemanager.ResourceListener;
import org.ogema.core.resourcemanager.ResourceStructureListener;
import org.ogema.core.resourcemanager.ResourceValueListener;
import org.ogema.core.resourcemanager.VirtualResourceException;

/**
 *
 * @author jlapp
 */
public class RootResource implements Resource {
	
	final ApplicationManager appman;
	final ResourceAccess resacc;

	public RootResource(ApplicationManager appman) {
		this.appman = appman;
		this.resacc = appman.getResourceAccess();
	}

	@Override
	public String getName() {
		return "";
	}

	@Override
	public String getPath() {
		return "";
	}

	@Override
	public String getPath(String string) {
		return "";
	}

	@Override
	public String getLocation() {
		return "";
	}

	@Override
	public String getLocation(String string) {
		return "";
	}

	@Override
	public Class<? extends Resource> getResourceType() {
		return Resource.class;
	}

	@Override
	public void addValueListener(ResourceValueListener<?> rl, boolean bln) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public void addValueListener(ResourceValueListener<?> rl) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean removeValueListener(ResourceValueListener<?> rl) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	@SuppressWarnings("deprecation")
	public void addResourceListener(ResourceListener rl, boolean bln) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	@SuppressWarnings("deprecation")
	public boolean removeResourceListener(ResourceListener rl) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public void addAccessModeListener(AccessModeListener al) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean removeAccessModeListener(AccessModeListener al) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public void addStructureListener(ResourceStructureListener rl) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean removeStructureListener(ResourceStructureListener rl) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@Override
	public boolean isTopLevel() {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean isWriteable() {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean isDecorator() {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean requestAccessMode(AccessMode am, AccessPriority ap) throws SecurityException {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public AccessPriority getAccessPriority() {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public AccessMode getAccessMode() {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public <T extends Resource> T getParent() {
		return null;
	}

	@Override
	public <T extends Resource> List<T> getReferencingResources(Class<T> type) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public List<Resource> getReferencingNodes(boolean bln) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public List<Resource> getSubResources(boolean bln) {
		List<Resource> rval = new ArrayList<>();
		resacc.getToplevelResources(null).forEach(res -> {
			rval.add(res);
			if (bln) {
				rval.addAll(res.getSubResources(bln));
			}
		});
		return rval;
	}

	@Override
	public List<Resource> getDirectSubResources(boolean bln) {
		List<Resource> rval = new ArrayList<>();
		resacc.getToplevelResources(null).forEach(res -> {
			rval.add(res);
			if (bln) {
				rval.addAll(res.getDirectSubResources(bln));
			}
		});
		return rval;
	}

	@Override
	public boolean isReference(boolean bln) {
		return false;
	}

	@Override
	public <T extends Resource> T getSubResource(String string) throws NoSuchResourceException {
		return resacc.getResource(string);
	}

	@Override
	public <T extends Resource> List<T> getSubResources(Class<T> type, boolean bln) {
		List<T> rval = new ArrayList<>();
		resacc.getToplevelResources(type).forEach(res -> {
			rval.add(res);
			if (bln) {
				rval.addAll(res.getSubResources(type, bln));
			}
		});
		return rval;
	}

	@Override
	public void activate(boolean bln) throws SecurityException, VirtualResourceException {
	}

	@Override
	public void deactivate(boolean bln) throws SecurityException {
	}

	@Override
	public void setOptionalElement(String string, Resource rsrc) throws NoSuchResourceException, InvalidResourceTypeException, ResourceGraphException, VirtualResourceException {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public Resource addOptionalElement(String string) throws NoSuchResourceException {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public <T extends Resource> T addDecorator(String string, Class<T> type) throws NoSuchResourceException, ResourceAlreadyExistsException {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public <T extends Resource> T addDecorator(String string, T t) throws ResourceAlreadyExistsException, NoSuchResourceException, ResourceGraphException, VirtualResourceException {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public void deleteElement(String string) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean equalsLocation(Resource rsrc) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean equalsPath(Resource rsrc) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Resource> T create() throws NoSuchResourceException {
		return (T) this;
	}

	@Override
	public void delete() {
	}

	@Override
	public <T extends Resource> T setAsReference(T t) throws NoSuchResourceException, ResourceGraphException, VirtualResourceException {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public <T extends Resource> T getSubResource(String string, Class<T> type) throws NoSuchResourceException {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Resource> T getLocationResource() {
		return (T) this;
	}
	
	
	
}
