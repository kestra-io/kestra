package io.kestra.executor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.executor.command.Create;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a full executor cycle through the real {@code DefaultExecutor} (with a real
 * {@link io.kestra.executor.FlowTriggerService}) to prove that a Flow trigger whose {@code when} cannot be
 * rendered does not fire, but is logged on both the evaluated execution and the flow that owns the trigger —
 * the regression behind #10857.
 */
class FlowTriggerInvalidWhenCycleTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    private static FlowWithSource upstream() {
        return Flows.yaml("""
            id: invalid-when-upstream
            namespace: io.kestra.tests
            tasks:
              - id: hello
                type: io.kestra.plugin.core.log.Log
                message: hi
            """);
    }

    @Test
    void anUnrenderableTriggerWhenIsLoggedOnBothSidesAndDoesNotFire() {
        // Given an upstream flow and a listener whose trigger-level `when` references `namespace`,
        // which is not available at trigger time and therefore cannot be rendered
        FlowWithSource upstream = upstream();
        FlowWithSource listener = Flows.yaml("""
            id: invalid-when-listener
            namespace: io.kestra.tests
            triggers:
              - id: on_upstream
                type: io.kestra.plugin.core.trigger.Flow
                states: [SUCCESS]
                when: "{{ namespace }}"
            tasks:
              - id: noop
                type: io.kestra.plugin.core.log.Log
                message: never reached
            """);
        harness.registerFlow(upstream);
        harness.registerFlow(listener);

        // When the upstream runs to SUCCESS twice through the whole executor machine
        Execution first = Executions.created(upstream);
        Execution second = Executions.created(upstream);
        Trace firstTrace = harness.run(first, ScriptedWorker.succeeding(T0));
        Trace secondTrace = harness.run(second, ScriptedWorker.succeeding(T0));
        assertThat(harness).hasExecutionInState(first, State.Type.SUCCESS);
        assertThat(harness).hasExecutionInState(second, State.Type.SUCCESS);

        // Then the trigger does not fire: no execution is created for the listener
        List<Execution> triggered = Stream.of(firstTrace, secondTrace)
            .flatMap(trace -> trace.emitted("execution"))
            .map(emission -> emission.as(Execution.class))
            .filter(execution -> "invalid-when-listener".equals(execution.getFlowId()))
            .toList();
        assertThat(triggered).isEmpty();

        // and each upstream execution gets a WARN saying the downstream flow will not be started
        assertThat(harness.logs())
            .filteredOn(log -> log.getLevel() == Level.WARN && "invalid-when-upstream".equals(log.getFlowId()))
            .extracting(LogEntry::getExecutionId)
            .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(harness.logs())
            .filteredOn(log -> log.getLevel() == Level.WARN && "invalid-when-upstream".equals(log.getFlowId()))
            .allMatch(log -> log.getMessage().contains("the downstream flow will not be started"));
        // while the flow that owns the trigger gets a single ERROR, not one per upstream execution
        assertThat(harness.logs())
            .filteredOn(log -> log.getLevel() == Level.ERROR && "invalid-when-listener".equals(log.getFlowId()))
            .hasSize(1);
    }

    @Test
    void anUnrenderableDependsOnWhenIsLoggedAndDoesNotFire() {
        // Given an upstream flow and a listener whose dependsOn entry has an unrenderable `when`
        FlowWithSource upstream = upstream();
        FlowWithSource listener = Flows.yaml("""
            id: invalid-dependson-when-listener
            namespace: io.kestra.tests
            triggers:
              - id: on_upstream
                type: io.kestra.plugin.core.trigger.Flow
                dependsOn:
                  - namespace: io.kestra.tests
                    flowId: invalid-when-upstream
                    states: [SUCCESS]
                    when: "{{ namespace }}"
            tasks:
              - id: noop
                type: io.kestra.plugin.core.log.Log
                message: never reached
            """);
        harness.registerFlow(upstream);
        harness.registerFlow(listener);

        // When the upstream runs to SUCCESS, the dependsOn trigger is evaluated through the multiple-condition cycle
        Execution created = Executions.created(upstream);
        Trace trace = harness.run(created, ScriptedWorker.succeeding(T0));
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);

        // Then the trigger does not fire: no execution command is created for the listener
        List<Create> commands = trace.emitted("executionCommand")
            .map(emission -> emission.as(Create.class))
            .filter(command -> "invalid-dependson-when-listener".equals(command.flowId()))
            .toList();
        assertThat(commands).isEmpty();

        // and the unrenderable dependsOn `when` is logged as an ERROR on the flow that owns the trigger
        assertThat(harness.logs())
            .anyMatch(log -> log.getLevel() == Level.ERROR && "invalid-dependson-when-listener".equals(log.getFlowId()));
        // and the upstream WARN says the execution does not count towards the dependsOn, not that the flow will never start
        assertThat(harness.logs())
            .filteredOn(log -> log.getLevel() == Level.WARN && "invalid-when-upstream".equals(log.getFlowId()))
            .singleElement()
            .satisfies(log -> assertThat(log.getMessage())
                .contains("this execution does not count towards its `dependsOn`")
                .doesNotContain("will not be started"));
    }
}
