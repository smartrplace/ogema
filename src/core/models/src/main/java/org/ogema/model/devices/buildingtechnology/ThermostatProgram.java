package org.ogema.model.devices.buildingtechnology;

import java.time.DayOfWeek;
import java.util.Optional;
import org.ogema.core.model.array.FloatArrayResource;
import org.ogema.core.model.array.IntegerArrayResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.model.prototypes.Data;

/**
 * A weekly program for a thermostat. Settings for a day consist of an integer
 * array defining the end points of the program's time intervals and a float
 * array for storing the interval values. Interval end points are given as
 * minute offsets into the day, with 1440 being the end of a day. The first
 * interval always starts at 0.
 * Common values for all week days or the week end can be set in {@link #endTimesWeekdays() }
 * and {@link #endTimesWeekends() }, settings for individual days will still take
 * precedence if available.
 *
 * @author jlapp
 */
public interface ThermostatProgram extends Data {
    
    IntegerResource programNumber();
    
    /**
     * Bitflags for the days that shall be transmitted, {@code 1 (Monday) ... 1 << 6 = 64 (Sunday)},
     * generally {@link DayOfWeek#getValue} - 1, use 127 for all days.
     * 
     * @return Bitflags for days to transmit: {@code 1 (Monday) ... 1 << 6 = 64 (Sunday)}
     */
    IntegerResource update();

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
    
    default void setEndTimes(DayOfWeek day, int[] times) {
        IntegerArrayResource arr = null;
        switch (day) {
            case MONDAY : arr = endTimesMonday(); break;
            case TUESDAY : arr = endTimesTuesday(); break;
            case WEDNESDAY : arr = endTimesWednesday(); break;
            case THURSDAY : arr = endTimesThursday(); break;
            case FRIDAY : arr = endTimesFriday(); break;
            case SATURDAY : arr = endTimesSaturday(); break;
            case SUNDAY : arr = endTimesSunday(); break;
        }
        if (arr != null) {
            arr.create();
            arr.setValues(times);
            arr.activate(false);
        }
    }
    
    default Optional<int[]> endTimesDay(DayOfWeek day) {
        IntegerArrayResource iar = null;
        switch (day) {
            case MONDAY : iar = endTimesMonday().isActive() ? endTimesMonday() : endTimesWeekdays(); break;
            case TUESDAY : iar = endTimesTuesday().isActive() ? endTimesTuesday() : endTimesWeekdays(); break;
            case WEDNESDAY : iar = endTimesWednesday().isActive() ? endTimesWednesday(): endTimesWeekdays(); break;
            case THURSDAY : iar = endTimesThursday().isActive() ? endTimesThursday(): endTimesWeekdays(); break;
            case FRIDAY : iar = endTimesFriday().isActive() ? endTimesFriday(): endTimesWeekdays(); break;
            case SATURDAY : iar = endTimesSaturday().isActive() ? endTimesSaturday(): endTimesWeekends(); break;
            case SUNDAY : iar = endTimesSunday().isActive() ? endTimesSunday(): endTimesWeekends(); break;
        }
        if (iar != null && iar.isActive()) {
            return Optional.of(iar.getValues());
        } else {
            return Optional.empty();
        }
    }
    
    default void setTemperatures(DayOfWeek day, float[] temps) {
        FloatArrayResource arr = null;
        switch (day) {
            case MONDAY : arr = temperaturesMonday(); break;
            case TUESDAY : arr = temperaturesTuesday(); break;
            case WEDNESDAY : arr = temperaturesWednesday(); break;
            case THURSDAY : arr = temperaturesThursday(); break;
            case FRIDAY : arr = temperaturesFriday(); break;
            case SATURDAY : arr = temperaturesSaturday(); break;
            case SUNDAY : arr = temperaturesSunday(); break;
        }
        if (arr != null) {
            arr.create();
            arr.setValues(temps);
            arr.activate(false);
        }
    }
    
    default Optional<float[]> temperaturesDay(DayOfWeek day) {
        FloatArrayResource iar = null;
        switch (day) {
            case MONDAY : iar = temperaturesMonday().isActive() ? temperaturesMonday() : temperaturesWeekdays(); break;
            case TUESDAY : iar = temperaturesTuesday().isActive() ? temperaturesTuesday() : temperaturesWeekdays(); break;
            case WEDNESDAY : iar = temperaturesWednesday().isActive() ? temperaturesWednesday(): temperaturesWeekdays(); break;
            case THURSDAY : iar = temperaturesThursday().isActive() ? temperaturesThursday(): temperaturesWeekdays(); break;
            case FRIDAY : iar = temperaturesFriday().isActive() ? temperaturesFriday(): temperaturesWeekdays(); break;
            case SATURDAY : iar = temperaturesSaturday().isActive() ? temperaturesSaturday(): temperaturesWeekends(); break;
            case SUNDAY : iar = temperaturesSunday().isActive() ? temperaturesSunday(): temperaturesWeekends(); break;
        }
        if (iar != null && iar.isActive()) {
            return Optional.of(iar.getValues());
        } else {
            return Optional.empty();
        }
    }
    
}
