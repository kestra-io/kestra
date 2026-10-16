package io.kestra.core.vnodes;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * The types of the messages of the vNodes assignment protocol.
 */
public enum VNodesAssignmentEventType {

    VNODES_ASSIGNMENT_REQUEST,
    VNODES_ASSIGNMENT_REPLY,
    VNODES_ASSIGNMENT_RELEASE,
    VNODES_ASSIGNMENT_REJECTED,

    // ERROR
    INVALID;

    @JsonCreator
    static VNodesAssignmentEventType from(final String s) {
        return Enums.getForNameIgnoreCase(s, VNodesAssignmentEventType.class, INVALID);
    }
}
