package io.kestra.executor.testkit;

import java.util.List;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;

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
}
