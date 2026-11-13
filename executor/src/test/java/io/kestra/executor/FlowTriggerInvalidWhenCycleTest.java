package io.kestra.executor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;

import io.kestra.core.executor.command.Create;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
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

    @ParameterizedTest
    @ValueSource(strings = { "{{ nmespace }}", "{{ nmespace == 'production' }}", "{{ labels.env == }}" })
    void anUnrenderableTriggerWhenIsLoggedOnBothSidesAndDoesNotFire(String when) {
        FlowWithSource upstream = upstream();
        FlowWithSource listener = Flows.yaml("""
            id: invalid-when-listener
            namespace: io.kestra.tests
            triggers:
              - id: on_upstream
                type: io.kestra.plugin.core.trigger.Flow
                states: [SUCCESS]
                when: "%s"
            tasks:
              - id: noop
                type: io.kestra.plugin.core.log.Log
                message: never reached
            """.formatted(when));
        harness.registerFlow(upstream);
        harness.registerFlow(listener);

        // When the upstream runs to SUCCESS through the whole executor machine
        Execution created = Executions.created(upstream);
        Trace trace = harness.run(created, ScriptedWorker.succeeding(T0));
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);

        // Then the trigger does not fire: no execution is created for the listener
        List<Execution> triggered = trace.emitted("execution")
            .map(emission -> emission.as(Execution.class))
            .filter(execution -> "invalid-when-listener".equals(execution.getFlowId()))
            .toList();
        assertThat(triggered).isEmpty();

        // and it is logged on both sides: an ERROR on the flow that owns the trigger (its misconfiguration)
        assertThat(harness.logs())
            .anyMatch(log -> log.getLevel() == Level.ERROR && "invalid-when-listener".equals(log.getFlowId()));
        // and a WARN on the evaluated upstream execution (not its error, but it surfaces something went wrong)
        assertThat(harness.logs())
            .anyMatch(log -> log.getLevel() == Level.WARN && "invalid-when-upstream".equals(log.getFlowId()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "{{ nmespace }}", "{{ nmespace == 'production' }}", "{{ labels.env == }}" })
    void anUnrenderableDependsOnWhenIsLoggedAndDoesNotFire(String when) {
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
                    when: "%s"
            tasks:
              - id: noop
                type: io.kestra.plugin.core.log.Log
                message: never reached
            """.formatted(when));
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
        assertThat(harness.logs())
            .anyMatch(log -> log.getLevel() == Level.WARN && upstream.getId().equals(log.getFlowId()));
    }

    @Test
    void shouldSilentlyIgnoreThreeUnrelatedExecutionsWhenListenerReadsMissingLabel() {
        FlowWithSource upstream = upstream();
        FlowWithSource listener = Flows.yaml("""
            id: listener
            namespace: company.team
            triggers:
              - id: on_prod
                type: io.kestra.plugin.core.trigger.Flow
                when: "{{ labels.env == 'production' }}"
                dependsOn:
                  - namespace: company.team
                    flowId: extract
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: "{{ trigger.executionId }}"
            """);
        harness.registerFlow(upstream);
        harness.registerFlow(listener);

        for (int i = 0; i < 3; i++) {
            Execution created = Executions.created(upstream);
            Trace trace = harness.run(created, ScriptedWorker.succeeding(T0));
            assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
            assertListenerNotTriggered(trace, listener);
        }

        assertThat(harness.logs()).noneMatch(log -> log.getLevel() == Level.WARN || log.getLevel() == Level.ERROR);
    }

    @ParameterizedTest
    @MethodSource("missingAttributeConditions")
    void shouldSilentlyRejectMissingAttributesWhenEvaluatingFlowConditions(String triggerWhen, String dependencyWhen) {
        FlowWithSource upstream = upstream();
        FlowWithSource listener = listener(triggerWhen, dependencyWhen);
        harness.registerFlow(upstream);
        harness.registerFlow(listener);

        Execution created = Executions.created(upstream);
        Trace trace = harness.run(created, ScriptedWorker.succeeding(T0));

        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS);
        assertListenerNotTriggered(trace, listener);
        assertThat(harness.logs()).noneMatch(log -> log.getLevel() == Level.WARN || log.getLevel() == Level.ERROR);
    }

    static Stream<Arguments> missingAttributeConditions() {
        return Stream.of("{{ labels.env == 'production' }}", "{{ execution.outputs.x }}", "{{ labels.evn }}")
            .flatMap(when -> Stream.of(Arguments.of(when, "true"), Arguments.of("true", when)));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shouldTriggerOnlyForMatchingLabelsWhenEvaluatingFlowConditions(boolean dependencyCondition) {
        String when = "{{ labels.env == 'production' }}";
        FlowWithSource upstream = upstream();
        FlowWithSource listener = listener(dependencyCondition ? "true" : when, dependencyCondition ? when : "true");
        harness.registerFlow(upstream);
        harness.registerFlow(listener);

        Execution staging = Executions.created(upstream).withLabels(List.of(new Label("env", "staging")));
        assertListenerNotTriggered(harness.run(staging, ScriptedWorker.succeeding(T0)), listener);

        Execution production = Executions.created(upstream).withLabels(List.of(new Label("env", "production")));
        Trace trace = harness.run(production, ScriptedWorker.succeeding(T0));
        assertThat(trace.emitted("executionCommand").map(emission -> emission.as(Create.class).flowId()))
            .containsExactly(listener.getId());
        assertThat(harness.logs()).noneMatch(log -> log.getLevel() == Level.WARN || log.getLevel() == Level.ERROR);
    }

    private static FlowWithSource listener(String triggerWhen, String dependencyWhen) {
        return Flows.yaml("""
            id: missing-attribute-listener
            namespace: io.kestra.tests
            triggers:
              - id: on_upstream
                type: io.kestra.plugin.core.trigger.Flow
                states: [SUCCESS]
                when: "%s"
                dependsOn:
                  - namespace: io.kestra.tests
                    flowId: invalid-when-upstream
                    states: [SUCCESS]
                    when: "%s"
            tasks:
              - id: noop
                type: io.kestra.plugin.core.log.Log
                message: triggered
            """.formatted(triggerWhen, dependencyWhen));
    }

    private static void assertListenerNotTriggered(Trace trace, FlowWithSource listener) {
        assertThat(trace.emitted("execution").map(emission -> emission.as(Execution.class).getFlowId()))
            .doesNotContain(listener.getId());
        assertThat(trace.emitted("executionCommand").map(emission -> emission.as(Create.class).flowId()))
            .doesNotContain(listener.getId());
    }
}
