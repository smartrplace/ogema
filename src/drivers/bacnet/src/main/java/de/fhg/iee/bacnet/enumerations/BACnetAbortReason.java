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
package de.fhg.iee.bacnet.enumerations;

import de.fhg.iee.bacnet.api.BACnetEnumeration;

/**
 *
 * @author jlapp
 */
public enum BACnetAbortReason implements BACnetEnumeration {

    other(0),
    buffer_overflow(1),
    invalid_apdu_in_this_state(2),
    preempted_by_higher_priority_task(3),
    segmentation_not_supported(4),
    security_error(5),
    insufficient_security(6),
    window_size_out_of_range(7),
    application_exceeded_reply_time(8),
    out_of_resources(9),
    tsm_timeout(10),
    apdu_too_long(11);

    public final int code;

    private BACnetAbortReason(int code) {
        this.code = code;
    }

    @Override
    public int getBACnetEnumValue() {
        return code;
    }

    public static BACnetAbortReason forEnumValue(int val) {
        for (BACnetAbortReason o : values()) {
            if (val == o.getBACnetEnumValue()) {
                return o;
            }
        }
        throw new IllegalArgumentException("unknown enum value: " + val);
    }
    
    public static String getReasonString(int enumVal) {
        for (BACnetAbortReason o : values()) {
            if (enumVal == o.code) {
                return o.name().replace('_', '-');
            }
        }
        return String.format("unknown reason (%d)", enumVal);
    }

}
