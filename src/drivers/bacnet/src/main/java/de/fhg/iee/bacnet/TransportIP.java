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
package de.fhg.iee.bacnet;

import de.fhg.iee.bacnet.apdu.ProtocolControlInformation;
import de.fhg.iee.bacnet.api.DeviceAddress;
import de.fhg.iee.bacnet.api.Indication;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author jlapp
 */
public class TransportIP extends AbstractTransport {

    public final static int MAX_APDU_SIZE = 1476;

    private final boolean offlineMode = Boolean.getBoolean("org.ogema.driver.bacnet.testwithoutconnection");

    private int port;
    
    private DatagramChannel sendChannel;
    private final Map<DatagramChannel, SelectionKey> channels = new HashMap<>();
    
    private Selector sel;
    private InetAddress broadcast;
    private InetAddress localAddress;
    private Thread receiverThread;

    public TransportIP(InetAddress localAddress, InetAddress broadcast, int port) throws IOException {
        this.localAddress = localAddress;
        this.broadcast = broadcast;
        this.port = port;
        openSocket();
    }

    /**
     * Create a new BACnet IP transport using the first IP4 address on the
     * selected interface and port.
     *
     * @param iface network interface
     * @param port port number, using 0 will automatically select a free port.
     * @throws IOException
     */
    public TransportIP(NetworkInterface iface, int port) throws IOException {
        for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
            if (ia.getAddress() instanceof Inet4Address) {
                localAddress = (Inet4Address) ia.getAddress();
                broadcast = ia.getBroadcast();
            }
        }
        this.port = port;
        openSocket();
    }

    private void openSocket() throws IOException {
        if (offlineMode) {
            logger.info("BACnet IP transport created in offline mode.");
        }
        sel = Selector.open();

        sendChannel = DatagramChannel.open();
        sendChannel.configureBlocking(false);
        sendChannel.setOption(StandardSocketOptions.SO_BROADCAST, true);
        sendChannel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        sendChannel.bind(new InetSocketAddress(localAddress, port));
        channels.put(sendChannel, sendChannel.register(sel, SelectionKey.OP_READ));
        // in case of port = 0
        port = sendChannel.socket().getLocalPort();

        DatagramChannel broadcastChannel = DatagramChannel.open();
        broadcastChannel.configureBlocking(false);
        broadcastChannel.setOption(StandardSocketOptions.SO_BROADCAST, true);
        broadcastChannel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        broadcastChannel.bind(new InetSocketAddress(broadcast, port));
        channels.put(broadcastChannel, broadcastChannel.register(sel, SelectionKey.OP_READ));
        
        logger.debug("opened UDP port {}:{}, broadcast={} ({})", localAddress, port,
                sendChannel.socket().getBroadcast(), broadcast);
        
        receiverThread = new Thread(this::receive, getClass().getSimpleName() + " UDP receiver " + localAddress + ":" + port);
    }

    static String bytesToString(byte[] bytes, int l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l; i++) {
            byte b = bytes[i];
            int v = b < 0
                    ? 256 + b
                    : b;
            if (i > 0) {
                sb.append(" ");
            }
            if (v < 16) {
                sb.append("0");
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    void receive() {
        logger.trace("starting UDP receiver thread");
        ByteBuffer recbuf = ByteBuffer.allocate(2048);
        recbuf.order(ByteOrder.BIG_ENDIAN); 
        while (!Thread.interrupted()) {
            try {
                if (sel.select() > 0) {
                    Iterator<SelectionKey> keys = sel.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey k = keys.next();
                        keys.remove();
                        recbuf.clear();

                        DatagramChannel chan = (DatagramChannel) k.channel();

                        InetSocketAddress src = (InetSocketAddress) chan.receive(recbuf);
                        int len = recbuf.position();
                        recbuf.flip();

                        byte[] msg = new byte[len];
                        
                        recbuf.get(msg);
                        //System.arraycopy(buf, 0, msg, 0, msg.length);
                        ByteBuffer bb = ByteBuffer.wrap(msg);

                        if (logger.isTraceEnabled()) {
                            if (len <= 40) {
                                logger.trace("received {} bytes on {} from {}:{} [{}]",
                                        len, chan.getLocalAddress(), src.getAddress(), src.getPort(), bytesToString(msg, len));
                            } else {
                                logger.trace("received {} bytes on {} from {}:{}", len, chan.getLocalAddress(), src.getAddress(), src.getPort());
                            }
                        }
                        
                        //all virtual link control messages start with type, function & length
                        int bvlcType = Npdu.getUnsignedByte(bb);
                        int bvlcFunction = Npdu.getUnsignedByte(bb);
                        int bvlcLength = bb.order(ByteOrder.BIG_ENDIAN).getChar();
                        if (bvlcType != 0x81) {
                            logger.debug("package is not a BACnet/IP message, BVLC Type field has value {}", Integer.toHexString(bvlcType));
                            continue;
                        }
                        if (bvlcFunction != 0x0a && bvlcFunction != 0x0b) {
                            logger.warn("received unsupported BVLC Function: {}", Integer.toHexString(bvlcType));
                            continue;
                        }
                        Npdu npdu = new Npdu(bb);

                        BACnetIpAddress srcAddr = new BACnetIpAddress();
                        srcAddr.addr = src.getAddress();
                        srcAddr.npdu = npdu;
                        srcAddr.port = src.getPort();

                        if (npdu.isNetworkMessage()) {
                            if (npdu.getMessageType() == 0x01) {
                                logger.debug("got I-Am-Router-To-Network from {}", srcAddr);
                            } else {
                                logger.debug("ignoring network message (type {}) from {}", npdu.getMessageType(), srcAddr.addr);
                            }
                            continue;
                        }
                        ByteBuffer apdu = bb.slice().asReadOnlyBuffer();

                        ProtocolControlInformation pci = new ProtocolControlInformation(apdu);
                        int pciSize = apdu.position();
                        apdu.rewind();

                        apdu.position(pciSize);
                        apdu.mark();

                        Indication i = new DefaultIndication(srcAddr, pci, apdu.duplicate(), npdu.getPriority(), npdu.isExpectingReply(), this);
                        receivedPackage(i);
                    }
                }
                //FIXME: exception handling
            } catch (SocketException ex) {
                if (Thread.interrupted()) {
                    logger.debug("transport closed");
                    break;
                }
                logger.error("exception in UDP receiver", ex);
            } catch (IOException ex) {
                logger.error("exception in UDP receiver", ex);
            } catch (Exception ex) {
                logger.error("exception in UDP receiver", ex);
            }
        }
        logger.trace("Finished receive while loop...");
    }

    public static DeviceAddress createAddress(InetAddress ip, int port, Npdu npdu) {
        BACnetIpAddress addr = new BACnetIpAddress();
        addr.addr = ip;
        addr.port = port;
        addr.npdu = npdu;
        return addr;
    }

    static class BACnetIpAddress implements DeviceAddress {

        Npdu npdu;
        InetAddress addr;
        int port;

        @Override
        public DeviceAddress toDestinationAddress() {
            BACnetIpAddress dest = new BACnetIpAddress();
            dest.addr = addr;
            dest.port = port;
            //BACnet/IP does not use local address in NPDU
            dest.npdu = npdu.withoutSource();
            //devices reachable via bridge/router have source info
            if (npdu.hasSource()) {
                //TODO: hop count?
                dest.npdu = dest.npdu.withDestination(
                        npdu.getSourceNet(), npdu.getSourceAddress(), 255);
            } else {
                dest.npdu = dest.npdu.withoutDestination();
            }
            return dest;
        }

        @Override
        public String toString() {
            if (addr == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(addr.toString()).append(" NPDU:");
            if (npdu.hasSource()) {
                sb.append(" src: ").append(npdu.getSourceNet())
                        .append("/")
                        .append(Arrays.toString(npdu.getSourceAddress()));
                //.append(bytesToString(npdu.getSourceAddress(), npdu.getSourceAddress().length));
            }
            if (npdu.hasDestination()) {
                sb.append(" dst: ").append(npdu.getDestinationNet())
                        .append("/")
                        .append(Arrays.toString(npdu.getDestinationAddress()));
                //.append(bytesToString(npdu.getDestinationAddress(), npdu.getDestinationAddress().length));
            }
            if (!npdu.hasSource() && !npdu.hasDestination()) {
                sb.append(" -");
            }
            return sb.toString();
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 67 * hash + Objects.hashCode(this.npdu);
            hash = 67 * hash + Objects.hashCode(this.addr);
            hash = 67 * hash + this.port;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final BACnetIpAddress other = (BACnetIpAddress) obj;
            if (this.port != other.port) {
                return false;
            }
            if (!Objects.equals(this.npdu, other.npdu)) {
                return false;
            }
            return Objects.equals(this.addr, other.addr);
        }

    }

    public int getPort() {
        return port;
    }

    @Override
    public DeviceAddress getLocalAddress() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public DeviceAddress getBroadcastAddress() {
        BACnetIpAddress addr = new BACnetIpAddress();
        addr.addr = broadcast;
        addr.port = this.port;
        addr.npdu = new Npdu().withDestination(0xFFFF, null, 255);
        return addr;
    }

    @Override
    protected void sendData(ByteBuffer data, Priority prio, boolean expectingReply, DeviceAddress destination) throws IOException {
        BACnetIpAddress addr = (BACnetIpAddress) destination;
        Npdu npdu = addr.npdu.withExpectingReply(expectingReply);

        byte[] npduOctets = npdu.toArray();
        int vlcSize = 4;
        int packetSize = vlcSize + npduOctets.length + data.limit();
        byte[] packetData = new byte[packetSize];
        //TODO need full VLC support(?) see spec J.2
        //write virtual link control
        packetData[0] = (byte) 0x81;
        packetData[1] = (byte) (npdu.isBroadcast() ? 0x0b : 0x0a);
        packetData[2] = (byte) ((packetSize & 0xFF00) >> 8);
        packetData[3] = (byte) (packetSize & 0xFF);
        System.arraycopy(npduOctets, 0, packetData, 4, npduOctets.length);
        data.get(packetData, vlcSize + npduOctets.length, data.limit());
        if (logger.isTraceEnabled()) {
            if (packetData.length <= 40) {
                logger.trace("sending {} bytes to {}:{} [{}]",
                        packetData.length, addr.addr, addr.port, bytesToString(packetData, packetData.length));
            } else {
                logger.trace("sending {} bytes to {}:{}", packetData.length, addr.addr, addr.port);
            }
        }
        DatagramPacket p = new DatagramPacket(packetData, packetData.length, addr.addr, addr.port);

        if (offlineMode) {
            return;
        }

        //sock.send(p);
        //int sent = sendingSocket.getChannel().send(ByteBuffer.wrap(packetData), new InetSocketAddress(addr.addr, addr.port));
        int sent = sendChannel.send(ByteBuffer.wrap(packetData), new InetSocketAddress(addr.addr, addr.port));
        logger.trace("bytes sent: {}", sent);
        //sendingSocket.send(p);
    }

    @Override
    protected void doClose() throws IOException {
        receiverThread.interrupt();
        channels.forEach((c, k) -> {
            k.cancel();
            try {
                c.close();
            } catch (IOException ex) {
                logger.error("close", ex);
            }
        });
        sel.close();
    }

    @Override
    protected void doStart() {
        receiverThread.start();
    }

}
