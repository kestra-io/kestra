package io.kestra.core.models.executions;

/**
 * Describe the kind of execution:
 * - TEST: created by a test
 * - PLAYGROUND: created by a playground
 * - NORMAL: anything else, for backward compatibility NORMAL is not persisted but null is used instead
 */
public enum ExecutionKind {
    NORMAL, TEST, PLAYGROUND
}
