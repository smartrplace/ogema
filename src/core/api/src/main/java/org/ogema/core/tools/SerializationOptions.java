package org.ogema.core.tools;

/**
 *
 * @author jlapp
 */
public enum SerializationOptions {
	
	/**
	 * Do not write values when deserializing.
	 */
	DESERIALIZE_VALUES_NEVER,
	
	/**
	 * When deserializing, write only values that have a valid
	 * {@code lastUpdateTime} (i.e. not -1). This is the default behaviour.
	 */
	DESERIALIZE_VALUES_VALID,
	
	/**
	 * When deserializing, write only values with a {@code lastUpdateTime}
	 * that is more recent than the current resource value.
	 */
	DESERIALIZE_VALUES_NEWER
	
}
