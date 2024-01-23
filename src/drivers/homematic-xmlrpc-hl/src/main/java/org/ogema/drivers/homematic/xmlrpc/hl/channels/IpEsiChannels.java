package org.ogema.drivers.homematic.xmlrpc.hl.channels;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.drivers.homematic.xmlrpc.hl.api.AbstractDeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandler;
import org.ogema.drivers.homematic.xmlrpc.hl.api.DeviceHandlerFactory;
import org.ogema.drivers.homematic.xmlrpc.hl.api.HomeMaticConnection;
import org.ogema.drivers.homematic.xmlrpc.hl.types.HmDevice;
import org.ogema.drivers.homematic.xmlrpc.ll.api.DeviceDescription;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEvent;
import org.ogema.drivers.homematic.xmlrpc.ll.api.HmEventListener;
import org.ogema.drivers.homematic.xmlrpc.ll.api.ParameterDescription;
import org.ogema.model.actors.MultiSwitch;
import org.ogema.tools.resource.util.ResourceUtils;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jlapp
 */
@Component(service = {DeviceHandlerFactory.class}, property = {Constants.SERVICE_RANKING + ":Integer=1"})
public class IpBslDimmerChannels extends AbstractDeviceHandler implements DeviceHandlerFactory {
    
    public static enum Colors {
		
		BLACK(0),
		BLUE(1),
		GREEN(2),
		TURQUOISE(3),
		RED(4),
		PURPLE(5),
		YELLOW(6),
		WHITE(7);
		
		public final int code;

		private Colors(int code) {
			this.code = code;
		}
		
		public int getCode() {
			return code;
		}
		
	}

    Logger logger = LoggerFactory.getLogger(getClass());
    
    @Override
    public DeviceHandler createHandler(HomeMaticConnection connection) {
        return new IpBslDimmerChannels(connection);
    }

    public IpBslDimmerChannels() {
        super(null);
    }

    public IpBslDimmerChannels(HomeMaticConnection conn) {
        super(conn);
    }
    
    class BslEventListener implements HmEventListener {

        final Map<String, SingleValueResource> resources;
        final String baseAddress;

        public BslEventListener(Map<String, SingleValueResource> resources, String baseAddress) {
            this.resources = resources;
            this.baseAddress = baseAddress;
        }

        @Override
        public void event(List<HmEvent> events) {
            for (HmEvent e : events) {
				//logger.trace("event: {}", e);
                if (!e.getAddress().startsWith(baseAddress)) {
                    continue;
                }
				if (!"COLOR".equals(e.getValueKey())) {
					continue;
				}
				logger.debug("got COLOR feedback for {}", e.getAddress());
				logger.debug("resource map: {}", resources);
                SingleValueResource res = resources.get(e.getAddress());
                if (res == null) {
                    continue;
                }
                try {
					ValueResourceUtils.setValue(res, e.getValueInt());
                    logger.debug("resource updated: {} = {}", res.getPath(), e.getValue());
                } catch (RuntimeException ex) {
					logger.warn("set value failed: {}", ex.getMessage());
                }
            }
        }

    }

    @Override
    public boolean accept(DeviceDescription desc) {
        //not actually interested in DIMMER_WEEK_PROFILE, but there is only one such channel per device
        return ("HmIP-BSL".equalsIgnoreCase(desc.getParentType()) && "DIMMER_WEEK_PROFILE".equalsIgnoreCase(desc.getType()));
    }

    @Override
    public void setup(HmDevice parent, DeviceDescription desc, Map<String, Map<String, ParameterDescription<?>>> paramSets) {
        final String deviceAddress = desc.getAddress();
		final String baseAddress = deviceAddress.substring(0, deviceAddress.indexOf(":"));
        logger.debug("setup HmIP-BSL handler for address {}", baseAddress);
		// there are 2 DIMMER_TRANSMITTER channels with 3 DIMMER_VIRTUAL_RECEIVER channels each.
		// COLOR is writable on the DIMMER_VIRTUAL_RECEIVER channels. The final
		// COLOR value seems to be determined by an 'or' operation (default, configurable)
		// over the 3 channel, and gets reported on the DIMMER_TRANSMITTER channel if it actually changes.
		// There is no separate on/off setting, off is COLOR=BLACK.
		final String addressTr1 = baseAddress + ":7";
		final String addressRec1 = baseAddress + ":8";
		final String addressTr2 = baseAddress + ":11";
		final String addressRec2 = baseAddress + ":12";
		
        String sw1Name = ResourceUtils.getValidResourceName("DIMMER_LEDS_1_" + addressTr1);
		String sw2Name = ResourceUtils.getValidResourceName("DIMMER_LEDS_2_" + addressTr2);

        MultiSwitch sw1 = parent.addDecorator(sw1Name, MultiSwitch.class);
		MultiSwitch sw2 = parent.addDecorator(sw2Name, MultiSwitch.class);
		
        conn.registerControlledResource(conn.getChannel(parent, addressRec1), sw1);
		conn.registerControlledResource(conn.getChannel(parent, addressRec2), sw2);
		
        Map<String, SingleValueResource> resources = new HashMap<>();
		sw1.stateControl().create().activate(false);
		sw1.stateFeedback().create().activate(false);
		sw1.activate(false);
		sw2.stateControl().create().activate(false);
		sw2.stateFeedback().create().activate(false);
		sw2.activate(false);
		resources.put(addressTr1, sw1.stateFeedback());
		resources.put(addressTr2, sw2.stateFeedback());
        
		sw1.stateControl().addValueListener((FloatResource f) -> {
			int val = (int) f.getValue();
			logger.debug("setting COLOR value for {}: {}", addressRec1, val);
			conn.performSetValue(addressRec1, "COLOR", val);
		}, true);
		sw2.stateControl().addValueListener((FloatResource f) -> {
			int val = (int) f.getValue();
			logger.debug("setting COLOR value for {}: {}", addressRec2, val);
			conn.performSetValue(addressRec2, "COLOR", val);
		}, true);
		
        conn.addEventListener(new BslEventListener(resources, baseAddress));
    }
    
}
