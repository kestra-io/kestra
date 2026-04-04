package io.kestra.core.models.tasks;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    enumAsRef = true,
    title = "Policy for handling child task failures in parallel flowable tasks."
)
public enum ChildFailurePolicy {
    @Schema(title = "Kill all running siblings immediately on first failure.")
    FAIL_FAST,

    @Schema(title = "Stop starting new siblings but let running ones finish.")
    STOP,

    @Schema(title = "Keep starting and running all siblings; fail only after all complete.")
    CONTINUE;

    @JsonCreator
    public static ChildFailurePolicy fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, ChildFailurePolicy.class, FAIL_FAST);
    }
}
