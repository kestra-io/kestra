package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.executor.command.Create;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTriggerOnFailedTriggerExecutionTest {

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final FlowWithSource scheduled = Flows.yaml("""
        id: scheduled
        namespace: io.kestra.tests
        tasks:
          - id: log
            type: io.kestra.plugin.core.log.Log
            message: never runs
        """);
    private final FlowWithSource listener = Flows.yaml("""
        id: listener
        namespace: io.kestra.tests
        tasks:
          - id: log
            type: io.kestra.plugin.core.log.Log
            message: triggered
        triggers:
          - id: on-failure
            type: io.kestra.plugin.core.trigger.Flow
            states:
              - FAILED
            when: "{{ flow.id == 'scheduled' }}"
        """);

    @Test
    void shouldFireFlowTriggerWhenTriggerCreatesFailedExecution() {
        // Given
        harness.registerFlow(scheduled);
        harness.registerFlow(listener);
        Create failedByTrigger = Create.of(new ExecutionId(scheduled.getTenantId(), scheduled.getNamespace(), scheduled.getId(), IdUtils.create(), null))
            .withStateType(State.Type.FAILED)
            .withTrigger(ExecutionTrigger.builder().id("schedule").type("io.kestra.plugin.core.trigger.Schedule").variables(Map.of()).build());

        // When
        Trace trace = harness.run(List.of(failedByTrigger), ScriptedWorker.succeeding(Instant.parse("2026-01-01T00:00:00Z")));

        // Then
        assertThat(
            trace.emitted("execution")
                .map(e -> e.as(Execution.class))
                .filter(execution -> execution.getTrigger() != null && "on-failure".equals(execution.getTrigger().getId()))
                .map(Execution::getFlowId)
                .toList()
        ).containsExactly("listener");
    }
}