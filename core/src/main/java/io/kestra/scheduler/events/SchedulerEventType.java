package io.kestra.scheduler.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.utils.Enums;

/**
 * Supported {@link SchedulerEvent} types 
 */
public enum SchedulerEventType {
    
    VNODES_ASSIGNMENT_REQUEST,
    VNODES_ASSIGNMENT_REPLY,
    VNODES_ASSIGNMENT_RELEASE,
    VNODES_ASSIGNMENT_REJECTED,
    
    // ERROR
    INVALID;
    
    @JsonCreator
    static SchedulerEventType from(final String s) {
        return Enums.getForNameIgnoreCase(s, SchedulerEventType.class, INVALID);
    }
}
