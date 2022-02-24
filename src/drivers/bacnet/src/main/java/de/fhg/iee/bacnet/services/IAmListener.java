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
import de.fhg.iee.bacnet.api.DeviceAddress;
import de.fhg.iee.bacnet.api.Indication;
import de.fhg.iee.bacnet.api.IndicationListener;
import de.fhg.iee.bacnet.api.Transport;
import de.fhg.iee.bacnet.enumerations.BACnetObjectType;
import de.fhg.iee.bacnet.enumerations.BACnetSegmentation;
import de.fhg.iee.bacnet.enumerations.BACnetUnconfirmedServiceChoice;
import de.fhg.iee.bacnet.tags.ObjectIdentifierTag;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IndicationListener which answers to who-is messages by sending out an i-am
 * message.
 *
 * @author jlapp
 */
public class IAmListener implements IndicationListener<Boolean> {

    //always 'device'
    final int objectType = BACnetObjectType.device.getBACnetEnumValue();
    final int instanceNumber;
    final int maxApduSizeAccepted;
    final BACnetSegmentation segmentationSupport;
    final int vendorId;
    
    final Map<String, Long> lastIam = new ConcurrentHashMap<>();
    
    static final Logger LOGGER = LoggerFactory.getLogger(IAmListener.class);

    public IAmListener(int instanceNumber, int maxApduSizeAccepted, BACnetSegmentation segmentationSupport, int vendorId) {
        this.instanceNumber = instanceNumber;
        this.maxApduSizeAccepted = maxApduSizeAccepted;
        this.segmentationSupport = segmentationSupport;
        this.vendorId = vendorId;
    }
    
    public void broadcastIAm(Transport t) {
        LOGGER.debug("broadcasting i-am to {}", t.getBroadcastAddress());
        sendIAm(t, t.getBroadcastAddress());
    }

    public void sendIAm(Transport t, DeviceAddress target) {
        ByteBuffer iAmPdu = UnconfirmedServices.createIAmApdu(
                new ObjectIdentifierTag(BACnetObjectType.device, instanceNumber),
                maxApduSizeAccepted, segmentationSupport, vendorId);
        try {
            LOGGER.debug("sending i-am to {}", target);
            t.request(target, iAmPdu, Transport.Priority.Normal, false, null);
        } catch (IOException ex) {
            LoggerFactory.getLogger(getClass()).error("sending of i-am request failed", ex);
        }
    }

    @Override
    public Boolean event(Indication i) {
        ProtocolControlInformation pci = i.getProtocolControlInfo();
        if (pci.getPduType() == ApduConstants.TYPE_UNCONFIRMED_REQ
                && pci.getServiceChoice() == BACnetUnconfirmedServiceChoice.who_Is.getBACnetEnumValue()) {
            LOGGER.trace("received who-is from {}", i.getSource());
            
            // testing
            //FIXME: address needs equals & hashcode
            String dest = i.getSource().toDestinationAddress().toString();
            long minInterval = 5_000;
            long now = System.currentTimeMillis();
            if (lastIam.getOrDefault(dest, now) + minInterval > now) {
                LOGGER.trace("not answering who-is from {}", i.getSource());
            } else {
                lastIam.put(dest, now);
                sendIAm(i.getTransport(), i.getSource().toDestinationAddress());
            }
            return true;
        }
        return false;
    }

}
