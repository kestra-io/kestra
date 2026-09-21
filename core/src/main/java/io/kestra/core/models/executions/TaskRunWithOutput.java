package io.kestra.core.models.executions;

import java.util.Map;

/**
 * Utility class to hold a {@link TaskRun} and its outputs.
 * Must only be used as a temporary carrier for methods that must return both.
 */
public record TaskRunWithOutput(TaskRun taskRun, Map<String, Object> outputs, AssetEmission assetEmission) {
    public TaskRunWithOutput(TaskRun taskRun, Map<String, Object> outputs) {
        this(taskRun, outputs, AssetEmission.OK);
    }

    public enum AssetEmission {
        OK,
        /** The declaration rendered but the assets could not be emitted — softened per {@code assetFailureBehavior}. */
        FAILED,
        /** The declaration itself is invalid, so no retry or later execution can make it succeed. */
        DECLARATION_INVALID
    }
}
