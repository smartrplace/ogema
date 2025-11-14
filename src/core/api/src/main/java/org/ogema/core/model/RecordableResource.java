package org.ogema.core.model;

import org.ogema.core.recordeddata.RecordedData;

/**
 * A {@link ValueResource} that also provides recording of its values (logging).
 * @author jlapp
 */
public interface RecordableResource extends ValueResource {
	
	/**
	 * Provides access to this resources logged data.
	 * 
	 * @return logged data access.
	 */
	RecordedData getHistoricalData();
	
}
