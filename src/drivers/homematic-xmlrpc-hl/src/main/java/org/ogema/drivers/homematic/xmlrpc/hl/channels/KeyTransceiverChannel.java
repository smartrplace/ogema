package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import java.util.List;
import java.util.Map;

import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.actors.EventPushButton;
import org.ogema.model.actors.EventPushButtonDevice;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds an {@link EventPushButtonDevice} for devices containing {@code KEY_TRANSCEIVER}
 * channels. Individual keys are only identified by their channel number, which
 * may vary with device type, so to order the button resources of a device, an
 * application should sort the available button resources by name and then access
 * buttons by their position in the sorted list. Beware that due to initialisation
 * method used by the Homematic driver, the order of the actual
 * {@link EventPushButtonDevice#buttons() } ResourceList may be different.
 * 
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class} /*, property = {Constants.SERVICE_RANKING + ":Integer=1"}*/ )
public class KeyTransceiverChannel extends AbstractDeviceHandler implements DeviceHandlerFactory {
    
    Logger logger = LoggerFactory.getLogger(getClass());
    
    @Override
    public DeviceHandler createHandler(HomeMaticConnection connection) {
        return new KeyTransceiverChannel(connection);
    }

    public KeyTransceiverChannel() {
        super(null);
    }

    public KeyTransceiverChannel(HomeMaticConnection conn) {
        super(conn);
    }
    
    class KeyEventListener implements HmEventListener {

        final EventPushButton button;
        final String baseAddress;

        public KeyEventListener(EventPushButton button, String baseAddress) {
            this.button = button;
            this.baseAddress = baseAddress;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
				//logger.trace("event: {}", e);
                if (!e.getAddress().startsWith(baseAddress)) {
                    continue;
                }
				switch (e.getValueKey()) {
					case "PRESS_SHORT":
						button.event().reading().setValue(9);
						logger.debug("short press registered on {}", button.getPath());
						break;
					case "PRESS_LONG":
						button.event().reading().setValue(1);
						logger.debug("long press registered on {}", button.getPath());
						break;
				}
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        return "KEY_TRANSCEIVER".equalsIgnoreCase(desc.getType());
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        final String deviceAddress = desc.getAddress();
        logger.debug("setup KEY_TRANSCEIVER handler for address {}", deviceAddress);
		final String channelNumber = deviceAddress.substring(deviceAddress.indexOf(":") + 1);

		EventPushButtonDevice keys = parent.getSubResource("KEYS", EventPushButtonDevice.class);
		String buttonName = String.format("button_%02d", Integer.valueOf(channelNumber));
		EventPushButton button = keys.buttons().getSubResource(buttonName, EventPushButton.class);
		button.event().reading().create();
		button.event().reading().activate(false);
		button.event().activate(false);
		button.activate(false);
		keys.buttons().activate(false);
		keys.activate(false);
		conn.registerControlledResource(conn.getChannel(parent, deviceAddress), button);
        conn.addEventListener(new KeyEventListener(button, deviceAddress));
    }
    
}
