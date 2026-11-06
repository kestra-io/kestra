package io.kestra.webserver.services.playground;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * Why a Playground run reuses nothing; {@link #NONE} when it replays the run it builds on.
 */
public enum PlaygroundFreshReason {
    NONE,
    WHOLE_FLOW,
    NO_PREVIOUS_RUN,
    BUILD_ON_NOTHING,
    INPUTS_CHANGED,
    LABELS_CHANGED,
    VARIABLES_CHANGED,
    NOT_REACHED,
    UNKNOWN;

    @JsonCreator
    public static PlaygroundFreshReason fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, PlaygroundFreshReason.class, UNKNOWN);
    }
}
