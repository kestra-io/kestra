package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.executor.command.Create;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTriggerWaitForAllRetriesTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final FlowWithSource upstream = Flows.yaml("""
        id: upstream
        namespace: io.kestra.tests
        retry:
          behavior: CREATE_NEW_EXECUTION
          type: constant
          interval: PT1S
          maxAttempts: 2
        tasks:
          - id: fail
            type: io.kestra.plugin.core.log.Log
            message: fail
        """);
    private final FlowWithSource eagerListener = listener("eager-listener", false);
    private final FlowWithSource patientListener = listener("patient-listener", true);

    @Test
    void shouldSkipTriggerWaitingForAllRetriesWhileARetryRemains() {
        // Given
        registerFlows();

        // When
        Trace trace = harness.run(Executions.created(upstream), ScriptedWorker.failing("fail", T0));

        // Then
        assertThat(triggeredFlowIds(trace)).contains("eager-listener").doesNotContain("patient-listener");
    }

    @Test
    void shouldFireTriggerWaitingForAllRetriesOnceRetriesAreExhausted() {
        // Given
        registerFlows();
        List<String> triggered = new ArrayList<>(triggeredFlowIds(harness.run(Executions.created(upstream), ScriptedWorker.failing("fail", T0))));

        // When
        for (int i = 0; i < 3; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            triggered.addAll(triggeredFlowIds(harness.run(List.of(), ScriptedWorker.failing("fail", T0))));
        }

        // Then
        assertThat(triggered).filteredOn("patient-listener"::equals).hasSize(1);
    }

    @Test
    void shouldFireTriggerWaitingForAllRetriesOnceFlowTaskRetriesAreExhausted() {
        // Given
        FlowWithSource retryingUpstream = retryFailedTaskUpstream();
        harness.registerFlow(retryingUpstream);
        harness.registerFlow(eagerListener);
        harness.registerFlow(patientListener);

        // When
        Trace trace = harness.run(Executions.created(retryingUpstream), task -> Results.failedAttempts(task, 2, T0));

        // Then
        assertThat(triggeredFlowIds(trace)).containsExactlyInAnyOrder("eager-listener", "patient-listener");
    }

    @Test
    void shouldEvaluateDependsOnTriggerWaitingForAllRetriesOnlyOnceRetriesAreExhausted() {
        // Given
        harness.registerFlow(upstream);
        harness.registerFlow(dependsOnListener("eager-depends-on", false));
        harness.registerFlow(dependsOnListener("patient-depends-on", true));
        List<Trace> traces = new ArrayList<>();

        // When
        traces.add(harness.run(Executions.created(upstream), ScriptedWorker.failing("fail", T0)));
        for (int i = 0; i < 3; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            traces.add(harness.run(List.of(), ScriptedWorker.failing("fail", T0)));
        }

        // Then
        List<String> evaluated = traces.stream().flatMap(trace -> dependsOnEvaluations(trace).stream()).toList();
        assertThat(evaluated).filteredOn("patient-depends-on"::equals).hasSize(1);
        assertThat(evaluated).filteredOn("eager-depends-on"::equals).hasSizeGreaterThan(1);
        List<String> created = traces.stream().flatMap(trace -> createdFlowIds(trace).stream()).toList();
        assertThat(created).as("the fulfilled dependsOn trigger creates its execution").filteredOn("patient-depends-on"::equals).hasSize(1);
    }

    @Test
    void shouldFireTriggerWaitingForAllRetriesWhenTheUpstreamHasNoRetry() {
        // Given: an upstream that fails for good on its first attempt
        FlowWithSource noRetryUpstream = Flows.yaml("""
            id: upstream
            namespace: io.kestra.tests
            tasks:
              - id: fail
                type: io.kestra.plugin.core.log.Log
                message: fail
            """);
        harness.registerFlow(noRetryUpstream);
        harness.registerFlow(eagerListener);
        harness.registerFlow(patientListener);

        // When
        Trace trace = harness.run(Executions.created(noRetryUpstream), ScriptedWorker.failing("fail", T0));

        // Then
        assertThat(triggeredFlowIds(trace)).containsExactlyInAnyOrder("eager-listener", "patient-listener");
    }

    @Test
    void shouldSkipTriggerWaitingForAllRetriesWhileAFlowTaskRetryRemains() {
        // Given
        FlowWithSource retryingUpstream = retryFailedTaskUpstream();
        harness.registerFlow(retryingUpstream);
        harness.registerFlow(eagerListener);
        harness.registerFlow(patientListener);

        // When: the first of two allowed attempts fails
        Trace trace = harness.run(Executions.created(retryingUpstream), task -> Results.failedAttempts(task, 1, T0));

        // Then
        assertThat(triggeredFlowIds(trace)).contains("eager-listener").doesNotContain("patient-listener");
    }

    private static FlowWithSource retryFailedTaskUpstream() {
        return Flows.yaml("""
            id: upstream
            namespace: io.kestra.tests
            retry:
              behavior: RETRY_FAILED_TASK
              type: constant
              interval: PT1S
              maxAttempts: 2
            tasks:
              - id: fail
                type: io.kestra.plugin.core.log.Log
                message: fail
            """);
    }

    private static List<String> createdFlowIds(Trace trace) {
        return trace.emitted("executionCommand")
            .map(Trace.Emission::message)
            .filter(Create.class::isInstance)
            .map(command -> ((Create) command).flowId())
            .toList();
    }

    private static List<String> dependsOnEvaluations(Trace trace) {
        return trace.emitted("multipleConditionEvent")
            .map(e -> e.as(MultipleConditionEvent.class))
            .filter(event -> "upstream".equals(event.execution().getFlowId()) && event.execution().getState().isFailed())
            .map(event -> event.flow().getId())
            .toList();
    }

    private void registerFlows() {
        harness.registerFlow(upstream);
        harness.registerFlow(eagerListener);
        harness.registerFlow(patientListener);
    }

    private static List<String> triggeredFlowIds(Trace trace) {
        return trace.emitted("execution")
            .map(e -> e.as(Execution.class))
            .filter(execution -> execution.getTrigger() != null)
            .map(Execution::getFlowId)
            .toList();
    }

    private static FlowWithSource dependsOnListener(String id, boolean waitForAllRetries) {
        return Flows.yaml("""
            id: %s
            namespace: io.kestra.tests
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: triggered
            triggers:
              - id: on-upstream-failure
                type: io.kestra.plugin.core.trigger.Flow
                waitForAllRetries: %s
                dependsOn:
                  - states:
                      - FAILED
                    namespace: io.kestra.tests
                    flowId: upstream
            """.formatted(id, waitForAllRetries));
    }

    private static FlowWithSource listener(String id, boolean waitForAllRetries) {
        return Flows.yaml("""
            id: %s
            namespace: io.kestra.tests
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: triggered
            triggers:
              - id: on-upstream-failure
                type: io.kestra.plugin.core.trigger.Flow
                waitForAllRetries: %s
                states:
                  - FAILED
                when: "{{ flow.id == 'upstream' }}"
            """.formatted(id, waitForAllRetries));
    }
}
