package io.kestra.core.executor.command;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

public enum ExecutionCommandType {
    CHANGE_TASK_RUN_STATE,
    FORCE_RUN,
    PAUSE,
    RESTART,
    RESUME,
    RESUME_FROM_BREAKPOINT,
    UNQUEUE,
    UPDATE_LABELS,
    UPDATE_STATUS,
    // ERROR
    INVALID;

    @JsonCreator
    static ExecutionCommandType from(final String s) {
        return Enums.getForNameIgnoreCase(s, ExecutionCommandType.class, INVALID);
    }
}
