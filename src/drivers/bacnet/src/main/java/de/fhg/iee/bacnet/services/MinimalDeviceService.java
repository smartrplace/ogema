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
            sendPropertyAt(i, requestOid, id, index);
        } else {
            logger.debug("got readProperty request for unknown type/instance/property {}/{}/{}",
                    requestOid.getObjectType(), requestOid.getInstanceNumber(), propId.getValue());
            sendReadPropertyError(i, BACnetErrorClass.object, BACnetErrorCode.unknown_object);
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
    
    //writes a ReadAccessResult list-of-results entry, see BACnet 135-2016 p.870
    private void writeReadPropertyMultipleResult(ObjectIdentifierTag oid,
            int property, int index, ByteBuffer output) {
        new UnsignedIntTag(2, Tag.TagClass.Context, property).write(output);
        if (index != -1) {
            new UnsignedIntTag(3, Tag.TagClass.Context, index).write(output);
        }
        if (objects.get(oid) == null) { // unknown object? §15.7.1.3.1
            Tag.createOpeningTag(5).write(output);
            new UnsignedIntTag(BACnetErrorClass.object.getBACnetEnumValue()).write(output);
            new UnsignedIntTag(BACnetErrorCode.unknown_object.getBACnetEnumValue()).write(output);
            Tag.createClosingTag(5).write(output);
            return;
        }
        if (!objects.get(oid).containsKey(property)) { // unknown property? §15.7.1.3.1
            Tag.createOpeningTag(5).write(output);
            new UnsignedIntTag(BACnetErrorClass.property.getBACnetEnumValue()).write(output);
            new UnsignedIntTag(BACnetErrorCode.unknown_property.getBACnetEnumValue()).write(output);
            Tag.createClosingTag(5).write(output);
            return;
        }
        try {
            Object value = getPropertyAt(oid, property, index);
            if (value != null) {
                Tag.createOpeningTag(4).write(output);
                writeValue(value, output);
                Tag.createClosingTag(4).write(output);
            } else {
                Tag.createOpeningTag(4).write(output);
                new Tag(0, Tag.TagClass.Application, TagConstants.TAG_NULL).write(output);
                Tag.createClosingTag(4).write(output);
            }
        } catch (IndexOutOfBoundsException ex) {
            //§15.7.1.3.1
            Tag.createOpeningTag(5).write(output);
            new UnsignedIntTag(BACnetErrorClass.property.getBACnetEnumValue()).write(output);
            new UnsignedIntTag(BACnetErrorCode.invalid_array_index.getBACnetEnumValue()).write(output);
            Tag.createClosingTag(5).write(output);
        }
    }
    
    private void writeAllProperties(ObjectIdentifierTag oid, int property, ByteBuffer output) {
        Map<Integer, PropertyConversion> allProps = objects.get(oid);
        if (allProps == null) {
            new UnsignedIntTag(2, Tag.TagClass.Context, property).write(output);
            Tag.createOpeningTag(5).write(output);
            new UnsignedIntTag(BACnetErrorClass.object.getBACnetEnumValue()).write(output);
            new UnsignedIntTag(BACnetErrorCode.unknown_object.getBACnetEnumValue()).write(output);
            Tag.createClosingTag(5).write(output);
            return;
        }
        for (int prop: allProps.keySet()) {
            if (prop == BACnetPropertyIdentifier.property_list.getBACnetEnumValue()) {
                continue;
            }
            writeReadPropertyMultipleResult(oid, prop, -1, output);
        }
    }
    
    // see BACnet 135-2016 §15.7
    private void serviceReadPropertyMultipleRequest(Indication ind) {
        ByteBuffer data = ind.getData();
        //XXX allocating fixed buffer...
        ByteBuffer output = ByteBuffer.allocate(8192);
        ProtocolControlInformation pci = new ProtocolControlInformation(ApduConstants.APDU_TYPES.COMPLEX_ACK, BACnetConfirmedServiceChoice.readPropertyMultiple);
        pci = pci.withInvokeId(ind.getProtocolControlInfo().getInvokeId());
        pci.write(output);
        while (data.remaining() > 0) {
            CompositeTag object = new CompositeTag(data);
            ObjectIdentifierTag readOid = new ObjectIdentifierTag(
                    0, Tag.TagClass.Context, object.getOidType(), object.getOidInstanceNumber());
            CompositeTag propSpecListTag = new CompositeTag(data);
            List<CompositeTag> propSpecList = new ArrayList<>(propSpecListTag.getSubTags());
            readOid.write(output);
            Tag.createOpeningTag(1).write(output);
            for (int i = 0; i < propSpecList.size(); i++) {
                int property = propSpecList.get(i).getUnsignedInt().intValue();
                logger.trace("readPropertyMultiple: {}, {}", readOid.getInstanceNumber(), property);
                //TODO: meta properties 'all', 'required' & 'optional', see §15.7.3.1.2
                // do not return property-list for 'all' or 'required'
                if (property == BACnetPropertyIdentifier.all.getBACnetEnumValue()
                        || property == BACnetPropertyIdentifier.required.getBACnetEnumValue()
                        || property == BACnetPropertyIdentifier.required.getBACnetEnumValue()) {
                    writeAllProperties(readOid, property, output);
                    continue;
                }
                int index = -1;
                if (i < propSpecList.size() - 1 && propSpecList.get(i+1).getTagNumber() == 1) {
                    i++;
                    index = propSpecList.get(i).getUnsignedInt().intValue();
                }
                writeReadPropertyMultipleResult(readOid, property, index, output);
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
                sendReadPropertyError(i, BACnetErrorClass.property, BACnetErrorCode.write_access_denied);
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

    private void sendReadPropertyError(Indication i, BACnetErrorClass eClass, BACnetErrorCode eCode) {
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

    // throws IllegalArgumentException if index != -1 and property is not an array
    // throws IndexOutOfBoundsException if index != -1 and index is out of bounds
    private Object getPropertyAt(ObjectIdentifierTag oid, int propId, int index)
            throws IndexOutOfBoundsException, IllegalArgumentException {
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
            } else {
                throw new IllegalArgumentException(
                    String.format("property %s, %d is not an array", oid, propId));
            }
        }
        return null;
    }
    
    private void sendPropertyAt(Indication i, ObjectIdentifierTag oid, int propId, int index) {
        logger.trace("Send Property {}[{}] to {}", propId, index, i.getSource().toDestinationAddress());
        if (!objects.containsKey(oid)) {
            sendReadPropertyError(i, BACnetErrorClass.object, BACnetErrorCode.unknown_object);
            return;
        }
        PropertyConversion p = objects.getOrDefault(oid, Collections.emptyMap()).get(propId);
        if (p == null || p.supplier == null) {
            sendReadPropertyError(i, BACnetErrorClass.property, BACnetErrorCode.unknown_property);
            return;
        }
        //FIXME huge allocation that may still be insufficient, maybe switch to netty ByteBuf?
        ByteBuffer buf = ByteBuffer.allocate(8192);
        ProtocolControlInformation pci = new ProtocolControlInformation(ApduConstants.APDU_TYPES.COMPLEX_ACK, BACnetConfirmedServiceChoice.readProperty);
        pci = pci.withInvokeId(i.getProtocolControlInfo().getInvokeId());
        pci.write(buf);
        new ObjectIdentifierTag(0, Tag.TagClass.Context, oid.getObjectType(), oid.getInstanceNumber()).write(buf);
        new UnsignedIntTag(1, Tag.TagClass.Context, propId).write(buf);
        if (index > -1) {
            new UnsignedIntTag(2, Tag.TagClass.Context, index).write(buf);
        }
        Tag.createOpeningTag(3).write(buf);
        try {
            Object value = getPropertyAt(oid, propId, index);
            writeValue(value, buf);
        } catch (IndexOutOfBoundsException ioobe) {
            sendReadPropertyError(i, BACnetErrorClass.property, BACnetErrorCode.invalid_array_index);
            return;
        } catch (IllegalArgumentException iae) {
            sendReadPropertyError(i, BACnetErrorClass.property, BACnetErrorCode.property_is_not_an_array);
            return;
        }
        Tag.createClosingTag(3).write(buf);
        buf.flip();
        try {
            i.getTransport().request(i.getSource(), buf, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(MinimalDeviceService.class).error("sending of object list failed", ex);
        }
    }
    
}
