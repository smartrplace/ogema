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
package de.fhg.iee.bacnet.services;

import de.fhg.iee.bacnet.apdu.ApduConstants;
import de.fhg.iee.bacnet.apdu.ProtocolControlInformation;
import de.fhg.iee.bacnet.api.Indication;
import de.fhg.iee.bacnet.api.IndicationListener;
import de.fhg.iee.bacnet.api.Transport;
import de.fhg.iee.bacnet.enumerations.BACnetConfirmedServiceChoice;
import de.fhg.iee.bacnet.enumerations.BACnetErrorClass;
import de.fhg.iee.bacnet.enumerations.BACnetErrorCode;
import de.fhg.iee.bacnet.enumerations.BACnetObjectType;
import de.fhg.iee.bacnet.enumerations.BACnetPropertyIdentifier;
import de.fhg.iee.bacnet.enumerations.BACnetRejectReason;
import de.fhg.iee.bacnet.tags.CompositeTag;
import de.fhg.iee.bacnet.tags.EnumeratedValueTag;
import de.fhg.iee.bacnet.tags.ObjectIdentifierTag;
import de.fhg.iee.bacnet.tags.Tag;
import de.fhg.iee.bacnet.tags.TagConstants;
import de.fhg.iee.bacnet.tags.UnsignedIntTag;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
public class MinimalDeviceService implements IndicationListener<Void> {
    
    /*
    22.1.5  Minimum Device Requirements
    A device that conforms to the BACnet protocol and contains an application layer shall:
    (a) contain exactly one Device object,
    (b) execute the ReadProperty service,
    (c) execute the Who-Has and Who-Is services (and thus initiate the I-Have and I-Am services) unless the device is an
    MS/TP slave device,
    (d) execute the WriteProperty service if the device executes the WritePropertyMultiple, AddListElement or
    RemoveListElement services,
    (e) allow the WriteProperty service to modify any properties that are modifiable by the AddListElement or
    RemoveListElement services, and
    (f) execute the WriteProperty service if the device contains any objects with properties that are required to be writable.
    TODO: i-have, i-am and array properties
     */
    final ObjectIdentifierTag oid;
    
    final Logger logger;

    public MinimalDeviceService(ObjectIdentifierTag oid, Logger logger) {
        this.oid = oid;
        this.logger = logger;
        addProperty(oid, BACnetPropertyIdentifier.object_list, () -> objects.keySet());
    }

    static class PropertyConversion {

        final Supplier<? extends Object> supplier;
        final Consumer<CompositeTag> consumer;

        public PropertyConversion(Supplier<? extends Object> supplier, Consumer<CompositeTag> consumer) {
            this.supplier = supplier;
            this.consumer = consumer;
        }
    }
    Map<ObjectIdentifierTag, Map<Integer, PropertyConversion>> objects =
            //FIXME: needs insertion order with device at [0] + concurrency
            new LinkedHashMap<>(); //new ConcurrentHashMap<>();

    public final void addProperty(ObjectIdentifierTag oid, BACnetPropertyIdentifier prop, Supplier<? extends Object> value) {
        addProperty(oid, prop, value, null);
    }

    public void addProperty(ObjectIdentifierTag oid, BACnetPropertyIdentifier prop, Supplier<? extends Object> value, Consumer<CompositeTag> writer) {
        objects.computeIfAbsent(oid, (de.fhg.iee.bacnet.tags.ObjectIdentifierTag __) -> {
            return new HashMap<>();
        }).put(prop.getBACnetEnumValue(), new PropertyConversion(value, writer));
    }
    
    public boolean removeObject(ObjectIdentifierTag oid) {
        if (oid.getObjectType() == BACnetObjectType.device.getBACnetEnumValue()) {
            //ignore calls to remove the device object
            return false;
        }
        return objects.remove(oid) != null;
    }

    @Override
    public Void event(Indication i) {
        ProtocolControlInformation pci = i.getProtocolControlInfo();
        if (pci.getPduType() == ApduConstants.TYPE_CONFIRMED_REQ) {
            if (pci.getServiceChoice() == BACnetConfirmedServiceChoice.readProperty.getBACnetEnumValue()) {
                serviceReadPropertyRequest(i);
            } else if (pci.getServiceChoice() == BACnetConfirmedServiceChoice.writeProperty.getBACnetEnumValue()) {
                serviceWritePropertyRequest(i);
            } else if (pci.getServiceChoice() == BACnetConfirmedServiceChoice.readPropertyMultiple.getBACnetEnumValue()) {
                serviceReadPropertyMultipleRequest(i);
            } else {
                ObjectIdentifierTag requestOid = new ObjectIdentifierTag(i.getData());
                if (objects.containsKey(requestOid)) {
                    sendReject(i, BACnetRejectReason.unrecognized_service.getBACnetEnumValue());
                }
            }
        }
        return null;
    }

    private void serviceReadPropertyRequest(Indication i) {
        ObjectIdentifierTag requestOid = new ObjectIdentifierTag(i.getData());
        UnsignedIntTag propId = new UnsignedIntTag(i.getData());
        if (objects.containsKey(requestOid)) {
            int id = propId.getValue().intValue();
            int index = -1;
            if (i.getData().remaining() > 0) {
                index = new UnsignedIntTag(i.getData()).getValue().intValue();
                //sendPropertyAt(i, oid, id, index.getValue().intValue());
                //logger.warn("got unsupported readProperty request with index for {}[{}]", propId.getValue(), index);
            }
            logger.debug("got readProperty request for property {}/{}, index {}", requestOid.getInstanceNumber(), propId.getValue(), index);
            if (id == BACnetPropertyIdentifier.object_list.getBACnetEnumValue()) {
                if (index != -1) {
                    sendObjectAt(i, index);
                } else {
                    sendObjectList(i);
                }
            } else {
                if (index != -1) {
                    sendPropertyAt(i, oid, id, index);
                } else {
                    sendProperty(i, requestOid, id);
                }
            }
        } else {
            logger.debug("got readProperty request for unknown type/instance/property {}/{}/{}",
                    requestOid.getObjectType(), requestOid.getInstanceNumber(), propId.getValue());
            sendError(i, BACnetErrorClass.object, BACnetErrorCode.unknown_object);
        }
    }
    
    static class PropertyReference {
        final ObjectIdentifierTag oid;
        final int index;

        public PropertyReference(ObjectIdentifierTag oid, int index) {
            this.oid = oid;
            this.index = index;
        }
        
    }
    
    private void serviceReadPropertyMultipleRequest(Indication ind) {
        ByteBuffer data = ind.getData();
        //XXX allocating fixed buffer...
        ByteBuffer output = ByteBuffer.allocate(8192);
        ProtocolControlInformation pci = new ProtocolControlInformation(ApduConstants.APDU_TYPES.COMPLEX_ACK, BACnetConfirmedServiceChoice.readPropertyMultiple);
        pci = pci.withInvokeId(ind.getProtocolControlInfo().getInvokeId());
        pci.write(output);
        
        while (data.remaining() > 0) {
            CompositeTag object = new CompositeTag(data);
            ObjectIdentifierTag oid = new ObjectIdentifierTag(
                    0, Tag.TagClass.Context, object.getOidType(), object.getOidInstanceNumber());
            CompositeTag propSpecListTag = new CompositeTag(data);
            List<CompositeTag> propSpecList = new ArrayList<>(propSpecListTag.getSubTags());
            oid.write(output);
            Tag.createOpeningTag(1).write(output);
            for (int i = 0; i < propSpecList.size(); i++) {
                int property = propSpecList.get(i).getUnsignedInt().intValue();
                //TODO: meta properties 'all', 'required' & 'optional'
                int index = -1;
                if (i < propSpecList.size() - 1 && propSpecList.get(i+1).getTagNumber() == 1) {
                    i++;
                    index = propSpecList.get(i).getUnsignedInt().intValue();
                }
                new UnsignedIntTag(2, Tag.TagClass.Context, property).write(output);
                if (index != -1) {
                    new UnsignedIntTag(3, Tag.TagClass.Context, index).write(output);
                }
                try {
                    Object value = getPropertyAt(oid, property, index);
                    if (value != null) {
                        Tag.createOpeningTag(4).write(output);
                        writeValue(value, output);
                        Tag.createClosingTag(4).write(output);
                    } else {
                        logger.error("implement me: return error value, no value for {},{}[{}]", oid, property, index);
                    }
                } catch (IndexOutOfBoundsException ex) {
                    logger.error("implement me: return error value", ex);
                }
            }
            Tag.createClosingTag(1).write(output);
        }
        output.flip();
        try {
            ind.getTransport().request(ind.getSource().toDestinationAddress(),
                    output, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of object list failed", ex);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void writeValue(Object val, ByteBuffer out) {
        if (val instanceof Tag) {
            ((Tag)val).write(out);
        } else if (val instanceof Collection) {
            //logger.trace("writing NULL tag for null value in collection");
            //new Tag(0, Tag.TagClass.Application, TagConstants.TAG_NULL).write(buf);
            System.out.println("write Collection");
            for (Object o : (Collection) val) {
                if (o == null) {
                    logger.trace("writing NULL tag for null value in collection");
                    new Tag(0, Tag.TagClass.Application, TagConstants.TAG_NULL).write(out);
                } else {
                    ((Tag) o).write(out);
                }
            }
        } else if (val == null) {
            logger.warn("writing NULL tag for null value"); //probably an error
            new Tag(0, Tag.TagClass.Application, TagConstants.TAG_NULL).write(out);
        } else if (val.getClass().isArray()) {
            Tag[] a = (Tag[]) val;
            for (Tag t : a) {
                if (t == null) {
                    logger.trace("writing NULL tag for null value in array");
                    new Tag(0, Tag.TagClass.Application, TagConstants.TAG_NULL).write(out);
                } else {
                    t.write(out);
                }
            }
        } else {
            logger.warn("asked to write unsupported type, writing NULL tag: {}", val); //probably an error
            new Tag(0, Tag.TagClass.Application, TagConstants.TAG_NULL).write(out);
        }
    }

    private void serviceWritePropertyRequest(Indication i) {
        ObjectIdentifierTag requestOid = new ObjectIdentifierTag(i.getData());
        if (objects.containsKey(requestOid)) {
            UnsignedIntTag propId = new UnsignedIntTag(i.getData());
            logger.debug("got writeProperty request for property {}", propId.getValue());
            int id = propId.getValue().intValue();
            PropertyConversion p = objects.computeIfAbsent(requestOid, (de.fhg.iee.bacnet.tags.ObjectIdentifierTag __) -> {
                return Collections.emptyMap();
            }).get(id);
            if (p == null || p.consumer == null) {
                sendError(i, BACnetErrorClass.property, BACnetErrorCode.write_access_denied);
            } else {
                CompositeTag ct = new CompositeTag(i.getData());
                if (i.getData().remaining() > 0) {
                    UnsignedIntTag priority = new UnsignedIntTag(i.getData());
                    logger.trace("ignoring priority {} on write to {}/{}",
                            priority.getValue(), requestOid.getInstanceNumber(), propId.getValue());
                }
                p.consumer.accept(ct);
                sendAck(i);
            }
        } else {
            logger.debug("got writeProperty request for unknown property {}/{}",
                    requestOid.getObjectType(), requestOid.getInstanceNumber());
        }
    }

    private void sendAck(Indication i) {
        logger.trace("Send Ack to "+i.getSource().toDestinationAddress()+" from MinimalDeviceService");
        ByteBuffer buf = ByteBuffer.allocate(30);
        ProtocolControlInformation pci = new ProtocolControlInformation(ApduConstants.APDU_TYPES.SIMPLE_ACK, i.getProtocolControlInfo().getServiceChoice()).withInvokeId(i.getProtocolControlInfo().getInvokeId());
        pci.write(buf);
        buf.flip();
        try {
            i.getTransport().request(i.getSource(), buf, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of ACK message failed", ex);
        }
    }

    private void sendReject(Indication i, int reason) {
        logger.trace("Send Reject to "+i.getSource().toDestinationAddress()+" from MinimalDeviceService");
        ByteBuffer buf = ByteBuffer.allocate(30);
        ProtocolControlInformation pci = new ProtocolControlInformation(ApduConstants.APDU_TYPES.REJECT, reason).withInvokeId(i.getProtocolControlInfo().getInvokeId());
        pci.write(buf);
        new EnumeratedValueTag(reason).write(buf);
        buf.flip();
        try {
            i.getTransport().request(i.getSource(), buf, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of ACK message failed", ex);
        }
    }

    private void sendError(Indication i, BACnetErrorClass eClass, BACnetErrorCode eCode) {
        logger.trace("Send Error {},{} to {}", eClass, eCode, i.getSource().toDestinationAddress());
        ByteBuffer buf = ByteBuffer.allocate(30);
        ProtocolControlInformation pci = new ProtocolControlInformation(ApduConstants.APDU_TYPES.ERROR, i.getProtocolControlInfo().getServiceChoice()).withInvokeId(i.getProtocolControlInfo().getInvokeId());
        pci.write(buf);
        new UnsignedIntTag(eClass.getBACnetEnumValue()).write(buf);
        new UnsignedIntTag(eCode.getBACnetEnumValue()).write(buf);
        buf.flip();
        try {
            i.getTransport().request(i.getSource(), buf, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of error message failed", ex);
        }
    }

    private void sendProperty(Indication i, ObjectIdentifierTag oid, int propId) {
        logger.trace("Send Property to "+i.getSource().toDestinationAddress()+" from MinimalDeviceService");
        PropertyConversion p = objects.getOrDefault(oid, Collections.emptyMap()).get(propId);
        if (p == null || p.supplier == null) {
            sendError(i, BACnetErrorClass.property, BACnetErrorCode.unknown_property);
            return;
        }
        Supplier<? extends Object> propertyTag = p.supplier;
        ByteBuffer buf = ByteBuffer.allocate(8192);
        ProtocolControlInformation pci = new ProtocolControlInformation(ApduConstants.APDU_TYPES.COMPLEX_ACK, BACnetConfirmedServiceChoice.readProperty);
        pci = pci.withInvokeId(i.getProtocolControlInfo().getInvokeId());
        pci.write(buf);
        new ObjectIdentifierTag(0, Tag.TagClass.Context, oid.getObjectType(), oid.getInstanceNumber()).write(buf);
        new UnsignedIntTag(1, Tag.TagClass.Context, propId).write(buf);
        Tag.createOpeningTag(3).write(buf);
        Object value = propertyTag.get();
        writeValue(value, buf);
        Tag.createClosingTag(3).write(buf);
        buf.flip();
        try {
            i.getTransport().request(i.getSource(), buf, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of object list failed", ex);
        }
    }
    
    private Object getPropertyAt(ObjectIdentifierTag oid, int propId, int index) throws IndexOutOfBoundsException {
        PropertyConversion p = objects.getOrDefault(oid, Collections.emptyMap()).get(propId);
        if (p == null || p.supplier == null) {
            return null;
        }
        if (index == -1) {
            return p.supplier.get();
        }
        Object value = p.supplier.get();
        if (value != null) {
            if (value instanceof Collection) {
                if (value instanceof List) {
                    if (index == 0) {
                        return new UnsignedIntTag(((List)value).size());
                    } else {
                        return ((List)value).get(index-1);
                    }
                } else {
                    if (index == 0) {
                        return new UnsignedIntTag(((Collection)value).size());
                    } else {
                        @SuppressWarnings("unchecked")
                        List<Object> l = new ArrayList<>((Collection)value);
                        return l.get(index-1);
                    }
                }
            } else if (value.getClass().isArray()) {
                if (index == 0) {
                    return new UnsignedIntTag(((Tag[])value).length);
                } else {
                    return ((Tag[]) value)[index-1];
                }
            }
        }
        return null;
    }
    
    private void sendPropertyAt(Indication i, ObjectIdentifierTag oid, int propId, int index) {
        logger.trace("Send Property {}[{}] to {}", propId, index, i.getSource().toDestinationAddress());
        PropertyConversion p = objects.getOrDefault(oid, Collections.emptyMap()).get(propId);
        if (p == null || p.supplier == null) {
            sendError(i, BACnetErrorClass.property, BACnetErrorCode.unknown_property);
            return;
        }
        Supplier<? extends Object> propertyTag = p.supplier;
        ByteBuffer buf = ByteBuffer.allocate(1024);
        ProtocolControlInformation pci = new ProtocolControlInformation(ApduConstants.APDU_TYPES.COMPLEX_ACK, BACnetConfirmedServiceChoice.readProperty);
        pci = pci.withInvokeId(i.getProtocolControlInfo().getInvokeId());
        pci.write(buf);
        new ObjectIdentifierTag(0, Tag.TagClass.Context, oid.getObjectType(), oid.getInstanceNumber()).write(buf);
        new UnsignedIntTag(1, Tag.TagClass.Context, propId).write(buf);
        new UnsignedIntTag(2, Tag.TagClass.Context, index).write(buf);
        Tag.createOpeningTag(3).write(buf);
        Object value = propertyTag.get();
        if (value != null) {
            if (value instanceof Tag) {
                ((Tag) value).write(buf);
            } else if (value instanceof Collection) {
                if (value instanceof List) {
                    if (index == 0) {
                        new UnsignedIntTag(((List)value).size()).write(buf);
                    } else {
                        Tag t = (Tag)((List)value).get(index-1);
                        t.write(buf);
                    }
                } else {
                    if (index == 0) {
                        new UnsignedIntTag(((Collection)value).size()).write(buf);
                    } else {
                        @SuppressWarnings("unchecked")
                        List<Object> l = new ArrayList<>((Collection)value);
                        Tag t = (Tag)l.get(index-1);
                        t.write(buf);
                    }
                }
            } else if (value.getClass().isArray()) {
                if (index == 0) {
                    new UnsignedIntTag(((Tag[])value).length).write(buf);
                } else {
                    ((Tag[]) value)[index-1].write(buf);
                }
            }
        }
        Tag.createClosingTag(3).write(buf);
        //FIXME error for unavailable index / IndexOutOfBoundsException
        buf.flip();
        try {
            i.getTransport().request(i.getSource(), buf, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of object list failed", ex);
        }
    }
    
    private void sendObjectAt(Indication i, int index) {
        logger.trace("Send object at [{}] to {}", index, i.getSource().toDestinationAddress());
        ByteBuffer buf = ByteBuffer.allocate(50);
        ProtocolControlInformation pci = new ProtocolControlInformation(
                ApduConstants.APDU_TYPES.COMPLEX_ACK, BACnetConfirmedServiceChoice.readProperty);
        pci = pci.withInvokeId(i.getProtocolControlInfo().getInvokeId());
        pci.write(buf);
        new ObjectIdentifierTag(0, Tag.TagClass.Context, oid.getObjectType(), oid.getInstanceNumber()).write(buf);
        new UnsignedIntTag(1, Tag.TagClass.Context, BACnetPropertyIdentifier.object_list.getBACnetEnumValue()).write(buf);
        new UnsignedIntTag(2, Tag.TagClass.Context, index).write(buf);
        
        if (index == 0) {
            logger.trace("write array size: {}", objects.size());
            Tag.createOpeningTag(3).write(buf);
            new UnsignedIntTag(objects.size()).write(buf);
            //new UnsignedIntTag(3, Tag.TagClass.Context, index).write(buf);
            Tag.createClosingTag(3).write(buf);
        } else {

            Tag.createOpeningTag(3).write(buf);
            oid.write(buf);
            if (index != 0) {
                List<ObjectIdentifierTag> oids = new ArrayList<>(objects.keySet());
                oids.get(index - 1).write(buf);
            } else {
                //send size
                new UnsignedIntTag(3, Tag.TagClass.Context, index).write(buf);
            }
            Tag.createClosingTag(3).write(buf);
        }
        buf.flip();
        try {
            i.getTransport().request(i.getSource(), buf, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of object list failed", ex);
        }
    }

    private void sendObjectList(Indication i) {
        logger.trace("Send objectList to "+i.getSource().toDestinationAddress()+" from MinimalDeviceService");
        ByteBuffer buf = ByteBuffer.allocate(8 * objects.size() + 50); //XXX: need to check maximum sizes / segmentation
        ProtocolControlInformation pci = new ProtocolControlInformation(
                ApduConstants.APDU_TYPES.COMPLEX_ACK, BACnetConfirmedServiceChoice.readProperty);
        pci = pci.withInvokeId(i.getProtocolControlInfo().getInvokeId());
        pci.write(buf);
        new ObjectIdentifierTag(0, Tag.TagClass.Context, oid.getObjectType(), oid.getInstanceNumber()).write(buf);
        new UnsignedIntTag(1, Tag.TagClass.Context, BACnetPropertyIdentifier.object_list.getBACnetEnumValue()).write(buf);
        Tag.createOpeningTag(3).write(buf);
        oid.write(buf); // always include self
        for (ObjectIdentifierTag id : objects.keySet()) {
            if (!oid.equals(id)) {
                id.write(buf);
            }
        }
        Tag.createClosingTag(3).write(buf);
        logger.trace("build object list response, {} bytes", buf.position());
        buf.flip();
        try {
            i.getTransport().request(i.getSource(), buf, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of object list failed", ex);
        }
    }
    
}
