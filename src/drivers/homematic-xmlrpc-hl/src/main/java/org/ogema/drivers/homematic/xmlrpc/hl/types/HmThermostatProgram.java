package org.ogema.drivers.homematic.xmlrpc.hl.types;

import java.time.DayOfWeek;
import org.ogema.core.model.array.FloatArrayResource;
import org.ogema.core.model.array.IntegerArrayResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.model.prototypes.Data;

/**
 *
 * @author jlapp
 */
public interface HmThermostatProgram extends Data {
    
    IntegerResource programNumber();
    
    /**
     * Bitflags for the days that shall be transmitted, {@code 1 (Monday) ... 1 << 6 = 64 (Sunday)},
     * generally {@link DayOfWeek#getValue} - 1, use 127 for all days.
     * 
     * @return Bitflags for days to transmit: {@code 1 (Monday) ... 1 << 6 = 64 (Sunday)}
     */
    IntegerResource update();
    
    /**
     * Sets the value of {@link #update() } to trigger the transmission for
     * the selected days.
     * 
     * @param days days for which to transmit program values.
     */
    default void triggerUpdate(DayOfWeek ... days) {
        int val = 0;
        for (DayOfWeek d: days) {
            val |= 1 << (d.getValue() - 1);
        }
        update().create();
        update().setValue(val);
        update().activate(false);
    }
    
    IntegerArrayResource endTimesMonday();
    FloatArrayResource temperaturesMonday();
    
    IntegerArrayResource endTimesTuesday();
    FloatArrayResource temperaturesTuesday();
    
    IntegerArrayResource endTimesWednesday();
    FloatArrayResource temperaturesWednesday();
    
    IntegerArrayResource endTimesThursday();
    FloatArrayResource temperaturesThursday();
    
    IntegerArrayResource endTimesFriday();
    FloatArrayResource temperaturesFriday();
    
    IntegerArrayResource endTimesSaturday();
    FloatArrayResource temperaturesSaturday();
    
    IntegerArrayResource endTimesSunday();
    FloatArrayResource temperaturesSunday();
    
    IntegerArrayResource endTimesWeekdays();
    FloatArrayResource temperaturesWeekdays();
    
    IntegerArrayResource endTimesWeekends();
    FloatArrayResource temperaturesWeekends();
    
}
