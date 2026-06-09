package io.kestra.core.models.executions;

/**
 * Describe the kind of execution:
 * - TEST: created by a test
 * - PLAYGROUND: created by a playground
 * - LOOP: a virtual loop-iteration execution
 * - SUBFLOW_FUNCTION: a subflow run synchronously from the {@code subflow()} Pebble function (e.g. to
 *   populate an input's {@code values:}); kept out of the main execution list/dashboards like the other
 *   non-NORMAL kinds
 * - NORMAL: anything else, for backward compatibility NORMAL is not persisted but null is used instead
 */
public enum ExecutionKind {
    NORMAL,
    TEST,
    PLAYGROUND,
    LOOP,
    SUBFLOW_FUNCTION
}
