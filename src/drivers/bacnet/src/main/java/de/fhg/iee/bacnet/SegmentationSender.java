package de.fhg.iee.bacnet;

import de.fhg.iee.bacnet.apdu.ApduConstants;
import de.fhg.iee.bacnet.apdu.ProtocolControlInformation;
import de.fhg.iee.bacnet.api.DeviceAddress;
import de.fhg.iee.bacnet.api.Indication;
import de.fhg.iee.bacnet.api.IndicationListener;
import de.fhg.iee.bacnet.api.Transport;
import de.fhg.iee.bacnet.tags.CompositeTag;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author jlapp
 */
public class SegmentationSender {
    
    public static final byte SEGMENT_ACK_NEGATIVE_ACK = 0b0000_0010;
    public static final byte SEGMENT_ACK_SERVER = 0b0000_0001;

    final AbstractTransport transport;
    boolean expectingReply;
    Transport.Priority prio;
    DeviceAddress destination;
    

    class Segment implements IndicationListener<Void> {

        final ProtocolControlInformation pci;
        final ByteBuffer rawData;
        volatile boolean ack = false;
        volatile int retry = 0;
        ByteBuffer apdu;

        public Segment(ProtocolControlInformation pci, ByteBuffer rawData) {
            this.pci = pci;
            this.rawData = rawData;
        }

        public synchronized ByteBuffer getApdu() {
            if (apdu != null) {
                return apdu;
            }
            transport.logger.trace("getApdu for segment {}, raw size {}", pci.getSequenceNumber(), rawData.remaining());
            ByteBuffer bb = ByteBuffer.allocateDirect(rawData.remaining() + 20);
            pci.write(bb);
            bb.put(rawData);
            bb.limit(bb.position());
            bb.rewind();
            transport.logger.trace("APDU size for segment {}: {}", pci.getSequenceNumber(), bb.remaining());
            rawData.rewind();
            return apdu = bb;
        }

        @Override
        public Void event(Indication ind) {
            ProtocolControlInformation i = ind.getProtocolControlInfo();
            // the SegmentACK-PDU actually does not contain the PCI, the type bits are
            // in the same place though...
            if (i.getPduType() != ApduConstants.TYPE_SEGMENT_ACK) {
                transport.logger.trace("not a segment ack, what do i do?");
                return null;
            }
            /*
            20.1.6 BACnet-SegmentACK-PDU
            The BACnet-SegmentACK-PDU is used to acknowledge the receipt of one or more APDUs containing portions of a segmented
            message. It may also request the next segment or segments of the segmented message.
            BACnet-SegmentACK-PDU ::= SEQUENCE {
                pdu-type [0] Unsigned (0..15), -- 4 for this PDU type
                reserved [1] Unsigned (0..3), -- shall be set to zero
                negative-ack [2] BOOLEAN,
                server [3] BOOLEAN,
                original-invoke-id [4] Unsigned (0..255),
                sequence-number [5] Unsigned (0..255),
                actual-window-size [6] Unsigned (1..127)
                -- Context specific tags 0..6 are NOT used in header encoding
            }
            */
            /*
            received 10 bytes from /192.168.20.10:55635 [81 0a 00 0a 01 00 40 2e 00 01]
            */
            ByteBuffer rawData = ind.getData();
            rawData.position(0);
            
            if (rawData.remaining() < 4) { //XXX what is this?
                transport.logger.warn("received SegmentACK-PDU with only {} bytes!", rawData.remaining());
                return null;
            }
            int control = Npdu.getUnsignedByte(rawData);
            int originalInvokeId = Npdu.getUnsignedByte(rawData);
            int sequenceNumber = Npdu.getUnsignedByte(rawData);
            int actualWindowSize = Npdu.getUnsignedByte(rawData);
            boolean isNegativeAck = (control & SEGMENT_ACK_NEGATIVE_ACK) != 0;
            boolean isServer = (control & SEGMENT_ACK_SERVER) != 0;
            transport.logger.trace("segment acknowleged: number {}, invoke ID {}, isNegative {}",
                sequenceNumber, originalInvokeId, isNegativeAck);
            return null;
        }

    }

    List<Segment> segments = new ArrayList<>();

    public SegmentationSender(AbstractTransport transport) {
        this.transport = transport;
    }

    public void complexAck(DeviceAddress destination, ProtocolControlInformation pci, ByteBuffer rawData,
            Transport.Priority prio, boolean expectingReply, IndicationListener<?> originalListener, int segmentSize) throws IOException {
        //xxx
        this.destination = destination;
        this.prio = prio;
        this.expectingReply = expectingReply;
        
        int position = 0;
        int segmentCount = (int) Math.ceil(((double) rawData.limit()) / segmentSize);
        transport.logger.trace("splitting {} bytes into {} segments", rawData.limit(), segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            ByteBuffer segmentBuffer = rawData.duplicate();
            int start = i * segmentSize;
            int end = Math.min(start + segmentSize, rawData.limit());
            segmentBuffer.position(start);
            segmentBuffer.limit(end);
            //TODO ??? window size != 1
            boolean moreFollows = i < segmentCount - 1;
            ProtocolControlInformation segPci = pci.withSegmentationInfo(
                    true, moreFollows, i, 1);
            segments.add(new Segment(segPci, segmentBuffer.slice()));
        }
        CompletableFuture.runAsync(this::sendSegments);
    }
    
    private void sendSegments() {
        try {
            for (Segment s: segments) {
                transport.logger.trace("sending segment {}", s.pci.getSequenceNumber());
                transport.request(destination, s.getApdu(), prio, expectingReply, s);
                Thread.sleep(200);
            }
        } catch (Throwable t) {
            //FIXME
            transport.logger.warn("error", t);
        } finally {
            transport.getInvokeIds(destination).release(segments.get(0).pci.getInvokeId());
        }
    }

}
