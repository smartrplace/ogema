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
package org.ogema.drivers.homematic.xmlrpc.hl;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.xmlrpc.XmlRpcException;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HomeMatic;
import org.ogema.drivers.homematic.xmlrpc.ll.xmlrpc.MapXmlRpcStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
public abstract class WriteAction {
    
    private final long firstTry = System.currentTimeMillis();
    private int tries = 0;
    private boolean success = false;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteScheduler.class);
    
    // for the scheduler
    long nextRun = firstTry;

    final long creationTimestamp() {
        return firstTry;
    }
    
    final int tries() {
        return tries;
    }
    
    final boolean writeSucceeded() {
        return success;
    }
    
    final boolean write() {
        if (!success) {
            tries++;
            success = performWrite();
        }
        return success;
    }
    
    /** identifies a target, so that newer write operations will replace
     older, pending (failed) writes.
     @return String identifying the target of the write operation (e.g. channel + value key) */
    abstract String target();
    
    /** Actual implementation of the write action, do not throw exceptions, log errors.
     @return write successful
     */
    abstract boolean performWrite();
    
    /**
     * Coalesce this action's parameters and those of a not yet perfomed previously
     * enqueued action.
     * 
     * @param previous previous, not yet performed action
     */
    void coalesce(WriteAction previous) {
        // only some actions can do something useful here
    }
    
    static WriteAction createSetValue(final HomeMatic hm, final String address, final String valueKey, final Object value) {
        return new WriteAction() {
            @Override
            String target() {
                return valueKey + "@" + address;
            }

            @Override
            boolean performWrite() {
                try {
                    LOGGER.trace("performing SetValue {} {}:={}", address, valueKey, value);
                    hm.setValue(address, valueKey, value);
                    return true;
                } catch (XmlRpcException ex) {
                    LoggerFactory.getLogger(HomeMaticDriver.class).warn("write failed (try {}) for {}@{} := {}", tries(), valueKey, address, value, ex);
                    return false;
                }
            }

        };
    }
    
    static WriteAction createAddLink(final HomeMatic hm, final String sender, final String receiver, final String name, final String description) {
        return new WriteAction() {
            @Override
            String target() {
                return sender + ">>" + receiver;
            }

            @Override
            boolean performWrite() {
                try {
                    LOGGER.trace("performing AddLink {}=>{} ('{}', {})", sender, receiver, name, description);
                    hm.addLink(sender, receiver, name, description);
                    return true;
                } catch (XmlRpcException ex) {
                    LoggerFactory.getLogger(HomeMaticDriver.class).warn("addLink failed (try {}) for {} >> {}",
                            tries(), sender, receiver, ex);
                    return false;
                }
            }
            
        };
    }
    
    static WriteAction createRemoveLink(final HomeMatic hm, final String sender, final String receiver) {
        return new WriteAction() {
            @Override
            String target() {
                return sender + ">>" + receiver;
            }

            @Override
            boolean performWrite() {
                try {
                    LOGGER.trace("performing RemoveLink {}=>{}", sender, receiver);
                    hm.removeLink(sender, receiver);
                    return true;
                } catch (XmlRpcException ex) {
                    LoggerFactory.getLogger(HomeMaticDriver.class).warn("removeLink failed (try {}) for {} >|> {}",
                            tries(), sender, receiver, ex);
                    return false;
                }
            }
            
        };
    }
    
    static class PutParamsetAction extends WriteAction {
        
        final HomeMatic hm;
        final String address;
        final String set;
        final Map<String, Object> values;

        public PutParamsetAction(HomeMatic hm, String address, String set, Map<String, Object> values) {
            this.hm = hm;
            this.address = address;
            this.set = set;
            this.values = new LinkedHashMap<>(values);
        }
        
        @Override
            String target() {
                return "[" + set + "]@" + address;
            }

            @Override
            boolean performWrite() {
                try {
                    LOGGER.trace("performing PutParamset {} {} {}", address, set, values);
                    MapXmlRpcStruct valueStruct = new MapXmlRpcStruct(values);
                    hm.putParamset(address, set, valueStruct);
                    return true;
                } catch (XmlRpcException ex) {
                    LoggerFactory.getLogger(HomeMaticDriver.class).warn("putParamset failed (try {}) for [{}]@{}",
                            tries(), set, address, ex);
                    return false;
                }
            }
            
            @Override
            void coalesce(WriteAction previous) {
                if (!(previous instanceof PutParamsetAction)) {
                    return;
                }
                Map<String, Object> previousValues = ((PutParamsetAction)previous).values;
                previousValues.forEach((key, val) -> {
                    if (!values.containsKey(key)) {
                        values.put(key, val);
                    }
                });
            }
    }
    
    static WriteAction createPutParamset(final HomeMatic hm, final String address, final String set, final Map<String, Object> values) {
        return new PutParamsetAction(hm, address, set, values);
    }
    
}
