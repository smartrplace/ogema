package org.ogema.model.smartgriddata;

import org.ogema.core.model.ResourceList;
import org.ogema.core.model.units.PowerResource;
import org.ogema.model.prototypes.PhysicalElement;

public interface GridDataConnector extends PhysicalElement {
	/** Dynamic electricity price (active power). It is assumed to contain any
	 * variable delivery and grid fees*/
	ElectricityPrice elPrice();

	/** If more than one market is relevant for the grid, additional prices can
	 * be provided here
	 */
	ResourceList<ElectricityPrice> additionalMarkets();
	
	/** Estimation of total wind power in relevant grid*/
	PowerResource windPowerTotal();
	
	/** Estimation of total solar power in relevant grid*/
	PowerResource solarPowerTotal();

	/** Estimation of wind power offered at electricity market in relevant grid*/
	PowerResource windPowerInMarket();

	/** Estimation of solar power offered at electricity market in relevant grid*/
	PowerResource solardPowerInMarket();
}
