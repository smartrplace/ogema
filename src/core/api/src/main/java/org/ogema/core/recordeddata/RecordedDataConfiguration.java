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
package org.ogema.core.recordeddata;

import java.io.Serializable;
import java.util.Objects;

/**
 * Defines the configuration for one RecordedData database
 * 
 */
public class RecordedDataConfiguration implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * FIXED_INTERVAL - all values to be written are matched to the fixed time interval. In one interval there is only
	 * one value possible. Missing values will be filled with ??? data. Read-out values have fixed timestamp interval
	 * given by the user.<br>
	 * FLEXIBLE - There is no fixed time interval. Each written value will be stored with its original timestamp.
	 */
	public enum StorageType {
		/** write the value to the database every time the value is updated */
		ON_VALUE_UPDATE,

		/** write new values into database only if the value differs from the last stored value */
		ON_VALUE_CHANGED,

		/** write values with a fixed update rate */
		FIXED_INTERVAL,
		
		/**
		 * The framework will not write any values, but log data is made accessible nevertheless. 
		 * It is assumed that values will be inserted into the RecordedData database by some OGEMA-external mechanism.
		 * 
		 * @since 2.3.0
		 */
		MANUAL
	}

	private StorageType storageType;

	/**
	 * Time in ms! The Interval between the TimeStamps of the Values. If an App will log every 100ms a Value but the
	 * updateRate is set on 200ms, than will only record every second value!
	 */
	private long fixedInterval;

	/**
	 * Getter method for the storage type to be used
	 * 
	 * @return returns the storage type for this RecordedData set: FLEXIBLE or FIXED_INTERVAL
	 */
	public StorageType getStorageType() {
		return storageType;
	}

	/**
	 * Setter method for the storage type to be used
	 * 
	 * @param storageType
	 *            set the storage type for this RecordedData set
	 */
	public void setStorageType(StorageType storageType) {
		this.storageType = storageType;
	}

	/**
	 * Getter for the fixed time interval for storage type FIXED_INTERVAL
	 * 
	 * @return fixed time interval for storage type FIXED_INTERVAL
	 */
	public long getFixedInterval() {
		return fixedInterval;
	}

	/**
	 * Setter for the fixed time interval for storage type FIXED_INTERVAL
	 * 
	 * @param fixedInterval
	 *            fixed time interval for storage type FIXED_INTERVAL in ms
	 */
	public void setFixedInterval(long fixedInterval) {
		this.fixedInterval = fixedInterval;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 79 * hash + Objects.hashCode(this.storageType);
		hash = 79 * hash + (int) (this.fixedInterval ^ (this.fixedInterval >>> 32));
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
		final RecordedDataConfiguration other = (RecordedDataConfiguration) obj;
		if (this.fixedInterval != other.fixedInterval) {
			return false;
		}
		return this.storageType == other.storageType;
	}
	
}
