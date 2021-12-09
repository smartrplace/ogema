package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import java.util.List;
import java.util.Optional;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.slf4j.Logger;

/**
 *
 * @author jlapp
 */
abstract class DeviceHandlers {
    
    private DeviceHandlers() {}
    
    static Optional<HmDevice> findDeviceChannel(
            HomeMaticConnection conn, Resource device, String channelType, Logger logger) {
        HmDevice topDevice = conn.findControllingDevice(device);
        if (topDevice == null) {
            logger.debug("could not find HmDevice for {}", device.getPath());
            return Optional.empty();
        }
        logger.debug("searching for channel {} on {}", channelType, topDevice.getPath());
        List<HmDevice> channels = topDevice.channels().getAllElements();
        if (channels.isEmpty()) {
            if (topDevice.getParent() instanceof ResourceList
                    && topDevice.getParent().getParent() instanceof HmDevice) {
                logger.trace("using channel list on {}", topDevice.getParent().getParent().getPath());
                channels = ((HmDevice)topDevice.getParent().getParent()).channels().getAllElements();
            }
        }
        return channels.stream().filter(chan -> {
             return channelType.equalsIgnoreCase(chan.type().getValue());
         }).findFirst();
    }
    
    /**
     * Link (or delink) 2 channels. The given devices must have been registered
     * using {@link HomeMaticConnection#registerControlledResource }
     * @param senderDevice 
     * @param senderChannelType 
     * @param receiverDevice 
     * @param receiverChannelType 
     * @param logger 
     * @param linkName (optional)
     * @param linkDescription (optional)
     * @param removeLink remove link if true, otherwise add link
     */
    static boolean linkChannels(HomeMaticConnection conn,
            Resource senderDevice, String senderChannelType, Resource receiverDevice, String receiverChannelType,
            Logger logger, String linkName, String linkDescription, boolean removeLink) {
        Optional<HmDevice> senderChannel = findDeviceChannel(conn, senderDevice, senderChannelType, logger);
        if (senderChannel.isPresent()) {
            Optional<HmDevice> receiverChannel = findDeviceChannel(conn, receiverDevice, receiverChannelType, logger);
            if (receiverChannel.isPresent()) {
                String senderAddress = senderChannel.get().address().getValue();
                String receiverAddress = receiverChannel.get().address().getValue();
                if (removeLink) {
                    logger.debug("removing link {} => {}", senderAddress, receiverAddress);
                    conn.performRemoveLink(senderAddress, receiverAddress);
                } else {
                    logger.debug("adding link {} => {}", senderAddress, receiverAddress);
                    conn.performAddLink(senderAddress, receiverAddress,
                        linkName, linkDescription);
                }
                return true;
            } else {
                logger.debug("could not find channel (receiver) {} / {}", receiverDevice.getPath(), receiverChannelType);
            }
        } else {
            logger.debug("could not find channel (sender) {} / {}", senderDevice.getPath(), senderChannelType);
        }
        return false;
    }
    
}
