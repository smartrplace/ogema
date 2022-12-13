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
package org.ogema.resourcemanager.virtual;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.ogema.core.model.Resource;
import org.ogema.core.resourcemanager.InvalidResourceTypeException;
import org.ogema.core.resourcemanager.ResourceAlreadyExistsException;
import org.ogema.persistence.ResourceDB;
import org.ogema.persistence.impl.mem.DefaultSimpleResourceData;
import org.ogema.persistence.impl.mem.MemoryResourceDB;
import org.ogema.persistence.impl.mem.MemoryTreeElement;
import org.ogema.resourcetree.SimpleResourceData;
import org.ogema.resourcetree.TreeElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
public class DefaultVirtualResourceDB implements VirtualResourceDB {

    final ResourceDB realResources;
    final ResourceDB virtualResources;
    final Logger logger = LoggerFactory.getLogger(getClass());
    final Cache<String, VirtualTreeElement> topLevelElementCache = 
            CacheBuilder.newBuilder().weakValues().build();
    private final Object transactionLock = new Object();
    // guarded by transactionLock
    private int transactionCounter = 0;
//new ConcurrentHashMap<>();

    private final RemovalListener<String, DefaultVirtualTreeElement> cacheListener = new RemovalListener<String, DefaultVirtualTreeElement>() {

        @Override
        public void onRemoval(RemovalNotification<String, DefaultVirtualTreeElement> notification) {
            logger.trace("element removed from cache ({}): {}", notification.getCause(), notification.getKey());
        }
    };    
    final Cache<String, DefaultVirtualTreeElement> elements =
            CacheBuilder.newBuilder().weakValues().removalListener(cacheListener).build();//new ConcurrentHashMap<>();

    public DefaultVirtualResourceDB(ResourceDB realResources) {
        this.realResources = realResources;
        this.virtualResources = new MemoryResourceDB();
    }

    @Override
    public VirtualTreeElement addResource(String name, Class<? extends Resource> type, String appID)
            throws ResourceAlreadyExistsException, InvalidResourceTypeException {
        TreeElement te = realResources.addResource(name, type, appID);
        DefaultVirtualTreeElement dvte = getElement(te);
        // MemoryTreeElement updates
        for (DefaultVirtualTreeElement child : dvte.virtualSubresources.values()) {
        	if (child.isVirtual()) {
        		((MemoryTreeElement) child.getEl()).setParent(dvte.getEl());
        	}
        }
        return dvte;
    }

    @Override
    public VirtualTreeElement getToplevelResource(String name) {
        VirtualTreeElement vte = topLevelElementCache.getIfPresent(name);
        if (vte != null) {
            return vte;
        }
        TreeElement te = realResources.getToplevelResource(name);
        if (te != null) {
            vte = getElement(te);
            topLevelElementCache.put(name, vte);
        }
        return vte;
    }

    @Override
    public Class<? extends Resource> addOrUpdateResourceType(Class<? extends Resource> type)
            throws ResourceAlreadyExistsException, InvalidResourceTypeException {
        virtualResources.addOrUpdateResourceType(type);
        return realResources.addOrUpdateResourceType(type);
    }

    @Override
    public Collection<Class<?>> getTypeChildren(String name) throws InvalidResourceTypeException {
        return realResources.getTypeChildren(name);
    }

    @Override
    public boolean hasResourceType(String name) {
        return realResources.hasResourceType(name);
    }

    @Override
    public List<Class<? extends Resource>> getAllResourceTypesInstalled() {
        return realResources.getAllResourceTypesInstalled();
    }

    @Override
    public void deleteResource(TreeElement elem) {
        makeVirtual(elem);
    }
    

    @SuppressWarnings("deprecation")
    private void makeVirtual(TreeElement elem) {
        final DefaultVirtualTreeElement virtualElement = getElement(elem);
        if (!elem.isReference()) {
            for (TreeElement child : virtualElement.getChildren()) {
                makeVirtual(child);
            }
        }
        
        if (!virtualElement.isVirtual()) {
            TreeElement realElement = virtualElement.getEl();
            TreeElement realParent = realElement.getParent();
            Object oldResRef = realElement.getResRef();
            TreeElement newElementParent = realParent != null ? getElement(realParent) : null;
            MemoryTreeElement replacement;
            if (org.ogema.core.model.SimpleResource.class.isAssignableFrom(elem.getType())){
                SimpleResourceData data;
                if (elem.isReference()){
                    data = new DefaultSimpleResourceData();
                } else {
                    data = elem.getData();
                }
                replacement = new MemoryTreeElement(elem.getName(), elem.getType(), newElementParent, elem.isDecorator(), data);
            } else {
                replacement = new MemoryTreeElement(elem.getName(), elem.getType(), newElementParent, elem.isDecorator());
            }
                    
            if (oldResRef != null) {
                replacement.setResRef(oldResRef);
            }
            virtualElement.setEl(replacement);
            if (elem.isToplevel()) {
                topLevelElementCache.invalidate(elem.getName());
            }
            realResources.deleteResource(realElement);
            for (DefaultVirtualTreeElement child : virtualElement.getVirtualChildren()) {
            	if (child.isVirtual()) {
            		((MemoryTreeElement) child.getEl()).setParent(replacement);
            	}
            	
            }
        }
    }

    @Override
    public boolean hasResource(String name) {
        return realResources.hasResource(name);
    }

    @Override
    public Collection<TreeElement> getAllToplevelResources() {
        Collection<TreeElement> real = realResources.getAllToplevelResources();
        Collection<TreeElement> rval = new ArrayList<>(real.size());
        for (TreeElement e : real) {
            rval.add(getElement(e));
        }
        return rval;
    }

    @Override
    public void startTransaction() {
    	synchronized (transactionLock) {
	    	if (transactionCounter++ == 0)
	    		realResources.startTransaction();
    	}
    }

    @Override
    public void finishTransaction() {
    	synchronized (transactionLock) {
    		if (--transactionCounter == 0)
    			realResources.finishTransaction();
    		else if (transactionCounter < 0)
    			throw new IllegalStateException("finishTransaction has been called more often than startTransaction");
    	}
    }

    @Override
    public boolean isDBReady() {
        return realResources.isDBReady();
    }

    @Override
    public TreeElement getByID(int id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public DefaultVirtualTreeElement getElement(TreeElement el) {
    	final String path = el.getPath();
    	DefaultVirtualTreeElement vEl0 = elements.getIfPresent(path);
    	if (vEl0 != null && vEl0.getEl() == el) {
    		return vEl0;
    	}
    	synchronized (this) {
    		while (el instanceof DefaultVirtualTreeElement) {
	            el = ((DefaultVirtualTreeElement) el).getEl();
	        }
	        DefaultVirtualTreeElement vEl = elements.getIfPresent(path);
	        //assert vEl == null || !(el instanceof DefaultVirtualTreeElement) || ((el instanceof DefaultVirtualTreeElement) && el == vEl) : "duplicate DefaultVirtualTreeElement: " + el + " | " + vEl;
	        
	        if (vEl == null) {
	            vEl = new DefaultVirtualTreeElement(el, this);
	            elements.put(path, vEl);
	        } else if (vEl.getEl() != el) {  // FIXME it is unclear here which element is new
	        	//System.out.printf("REPLACE ELEMENT at %s: %s%n", path, el);
				// let's not use TreeElements that are actually deleted...
				if (el instanceof VirtualTreeElement || realResources.getByID(el.getResID()) != null) {
					vEl.setEl(el);
				} else { //how?
					logger.debug("getElement called for deleted TreeElement: {}", el);
				}
	        }
	        return vEl;
		}
    }
    
    protected synchronized DefaultVirtualTreeElement getElement(String name, TreeElement parent, Class<? extends Resource> type, boolean decorator) {
        String path = parent.getPath() + "/" + name;
        DefaultVirtualTreeElement vEl = elements.getIfPresent(path);
        if (vEl == null){
            MemoryTreeElement el = new MemoryTreeElement(name, type, parent, decorator);
            vEl = new DefaultVirtualTreeElement(el, this);
            elements.put(path, vEl);
        }
        return vEl;
    }

	@Override
	public Collection<TreeElement> getFilteredNodes(Map<String, String> dict) {
        Collection<TreeElement> realElements = realResources.getFilteredNodes(dict);
        Collection<TreeElement> vEls = new ArrayList<>(realElements.size());
        for (TreeElement e: realElements) {
            vEls.add(getElement(e));
        }
		return vEls;
	}

	@Override
	public TreeElement getFilteredNodesByPath(String path, boolean isRoot) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Class<?>> getModelDeclaredChildren(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Class<? extends Resource>> getResourceTypesInstalled(Class<? extends Resource> cls) {
		return realResources.getResourceTypesInstalled(cls);
	}

	@Override
	public void doStorage() {
		// TODO Auto-generated method stub
		
	}

}
