package de.fhg.iee.bacnet.api;

import java.util.List;

/**
 *
 * @author jlapp
 */
public interface EdeInformation {

    default String description() {
        return null;
    }

    default String getName() {
        return null;
    }

    default List<String> getStates() {
        return null;
    }

    default Boolean isSettable() {
        return null;
    }

    default Float minValue() {
        return null;
    }

    default Float maxValue() {
        return null;
    }

    default String unit() {
        return null;
    }

    default String objectTag() {
        return null;
    }

    default String getVendorSpecificAddress() {
        return null;
    }
    
}
