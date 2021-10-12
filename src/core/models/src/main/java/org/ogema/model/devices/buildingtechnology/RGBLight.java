package org.ogema.model.devices.buildingtechnology;

import org.ogema.core.model.simple.IntegerResource;
import org.ogema.model.actors.OnOffSwitch;
import org.ogema.model.prototypes.PhysicalElement;

public interface RGBLight extends PhysicalElement {
	/** Red value as 0...255*/
	IntegerResource r();
	/** Green value as 0...255*/
	IntegerResource g();

	/** Blue value as 0...255*/
	IntegerResource b();

	/** Cold white value as 0...255*/
	IntegerResource c();

	/** Warm white value as 0...255*/
	IntegerResource w();
	
	/**
	 * Switch to control when the light is on or off
	 */
	OnOffSwitch onOffSwitch();
}
