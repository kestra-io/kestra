package io.kestra.executor.testkit;

import java.time.Instant;
import java.util.List;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;

/**
 * Execution fixtures for executor unit tests.
 */
public final class Executions {
    private Executions() {
        // utility class pattern
    }

    /**
     * A freshly created execution, exactly as the webserver/scheduler would submit it.
     */
    public static Execution created(FlowWithSource flow) {
        return Execution.newExecution(flow, List.of());
    }

    /**
     * A freshly created execution whose start date is {@code startDate}. Duration SLAs measure the execution
     * against the wall clock, not the harness clock: start it well in the past so a short SLA is deterministically
     * exceeded, however fast the test runs.
     */
    public static Execution createdAt(FlowWithSource flow, Instant startDate) {
        return created(flow).toBuilder()
            .state(new State(State.Type.CREATED, List.of(new State.History(State.Type.CREATED, startDate))))
            .build();
    }
}
