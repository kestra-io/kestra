package io.kestra.core.models.executions;

import jakarta.validation.constraints.NotNull;

/**
 * Describe the kind of execution:
 * - TEST: created by a test
 * - PLAYGROUND: created by a playground
 * - LOOP: a virtual loop-iteration execution
 * - NORMAL: anything else, for backward compatibility NORMAL is not persisted, but null is used instead
 */
public enum ExecutionKind {
    NORMAL,
    TEST,
    PLAYGROUND,
    LOOP;

    /**
     * @return true if the execution is normal (Kind.NORMAL or null)
     */
    public static boolean isNormal(@NotNull Execution execution) {
        return execution.getKind() == null || execution.getKind() == NORMAL;
    }
}
