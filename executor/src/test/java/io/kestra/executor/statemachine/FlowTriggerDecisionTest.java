package io.kestra.executor.statemachine;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Window;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import io.kestra.executor.FlowTriggerService;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.InMemoryMultipleConditionStateStore;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision matrix for {@link FlowTriggerService} — which upstream execution fires which Flow
 * trigger, and how dependsOn multiple-condition windows accumulate across executions.
 * Twins FlowTriggerServiceTest (same module, {@code @MicronautTest}) for the standard-conditions
 * lane and adds the dependsOn lane, which previously had no service-level coverage (only
 * runner-level MultipleConditionTriggerCaseTest over H2). Hand-wired: real
 * {@link ConditionService} (stateless), the testkit's run-context factory (real Pebble), a
 * {@code CALLS_REAL_METHODS} {@link FlowService} mock (only the pure {@code removeUnwanted} is
 * reached) and {@link InMemoryMultipleConditionStateStore} mirroring the JDBC get-or-create /
 * reset-on-success semantics. No Micronaut, no database.
 */
class FlowTriggerDecisionTest {

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final InMemoryMultipleConditionStateStore multipleConditionStateStore = new InMemoryMultipleConditionStateStore();
    private final FlowTriggerService flowTriggerService = new FlowTriggerService(
        new ConditionService(),
        harness.runContextFactory(),
        // FlowService is field-injected and repository-backed; the only method FlowTriggerService
        // calls is removeUnwanted, which is pure — same pattern as the harness's ExecutionService
        Mockito.mock(FlowService.class, Mockito.CALLS_REAL_METHODS)
    );

    // --- standard conditions: who fires

    @Test
    void shouldCreateExecutionWhenUpstreamSucceedsAndTriggerHasNoConditions() {
        // Given: an upstream flow and a flow listening to it with a bare Flow trigger
        Flow upstream = upstreamFlow();
        Flow listening = listeningFlow(flowTrigger().build());
        Execution success = executionOf(upstream, State.Type.SUCCESS);

        // When
        List<Execution> executions = flowTriggerService.computeExecutionsFromFlowTriggerConditions(success, listening);

        // Then: one CREATED execution of the listening flow, carrying the upstream execution in
        // its trigger variables
        assertThat(executions).hasSize(1);
        Execution created = executions.getFirst();
        assertThat(created.getFlowId()).isEqualTo(listening.getId());
        assertThat(created.getNamespace()).isEqualTo(listening.getNamespace());
        assertThat(created.getState().getCurrent()).isEqualTo(State.Type.CREATED);
        assertThat(created.getTrigger().getVariables())
            .containsEntry("executionId", success.getId())
            .containsEntry("flowId", upstream.getId())
            .containsEntry("namespace", upstream.getNamespace());
    }

    @Test
    void shouldNotFireWhenFlowHasNoFlowTrigger() {
        // Given: the upstream flow itself declares no trigger
        Flow upstream = upstreamFlow();

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstream, State.Type.SUCCESS), upstream))
            .isEmpty();
    }

    @Test
    void shouldNotFireWhenTriggerFlowIsTheExecutionFlowItself() {
        // Given: a flow whose Flow trigger would react to its own executions
        Flow recursive = listeningFlow(flowTrigger().build());
        Execution ownExecution = executionOf(recursive, State.Type.SUCCESS);

        // When / Then: the recursion guard (FlowService#removeUnwanted) filters it
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(ownExecution, recursive))
            .isEmpty();
    }

    @Test
    void shouldNotFireWhenExecutionStateIsNotListened() {
        // Given: default states are terminal + PAUSED — CREATED is a fresh execution, not a
        // state transition
        Flow listening = listeningFlow(flowTrigger().build());
        Execution created = executionOf(upstreamFlow(), State.Type.CREATED);

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(created, listening))
            .isEmpty();
    }

    @Test
    void shouldFireOnlyOnListedStatesWhenTriggerRestrictsStates() {
        // Given: a trigger listening to FAILED only
        Flow upstream = upstreamFlow();
        Flow listening = listeningFlow(flowTrigger().states(List.of(State.Type.FAILED)).build());

        // When / Then: SUCCESS is ignored, FAILED fires
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstream, State.Type.SUCCESS), listening))
            .isEmpty();
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstream, State.Type.FAILED), listening))
            .hasSize(1);
    }

    @Test
    void shouldNotFireWhenExecutionKindIsNotNormal() {
        // Given: a TEST-kind execution (flow unit tests must never cascade into flow triggers)
        Flow listening = listeningFlow(flowTrigger().build());
        Execution testExecution = executionOf(upstreamFlow(), State.Type.SUCCESS)
            .toBuilder().kind(ExecutionKind.TEST).build();

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(testExecution, listening))
            .isEmpty();
    }

    @Test
    void shouldNotFireWhenListeningFlowIsDraft() {
        // Given: a draft revision is never picked up implicitly, like webhooks/schedules/subflows
        Flow draft = listeningFlowBuilder(flowTrigger().build()).draft(true).build();

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstreamFlow(), State.Type.SUCCESS), draft))
            .isEmpty();
    }

    @Test
    void shouldNotFireWhenListeningFlowIsDisabled() {
        // Given
        Flow disabled = listeningFlowBuilder(flowTrigger().build()).disabled(true).build();

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstreamFlow(), State.Type.SUCCESS), disabled))
            .isEmpty();
    }

    @Test
    void shouldNotFireWhenTriggerIsDisabled() {
        // Given
        Flow listening = listeningFlow(flowTrigger().disabled(true).build());

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstreamFlow(), State.Type.SUCCESS), listening))
            .isEmpty();
    }

    // --- standard conditions: the `when` expression (real Pebble)

    @Test
    void shouldNotFireWhenWhenExpressionIsFalse() {
        // Given
        Flow listening = listeningFlow(flowTrigger().when("false").build());

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstreamFlow(), State.Type.SUCCESS), listening))
            .isEmpty();
    }

    @Test
    void shouldFireWhenWhenExpressionRendersTruthy() {
        // Given: a non-empty rendered string is truthy (kit Pebble ships built-ins only — Kestra
        // extension filters like startsWith are Micronaut Extension beans and aren't registered)
        Flow listening = listeningFlow(flowTrigger().when("{{ flow.id }}").build());

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstreamFlow(), State.Type.SUCCESS), listening))
            .hasSize(1);
    }

    @Test
    void shouldTreatInvalidWhenExpressionAsFalse() {
        // Given: a malformed Pebble expression — evaluation failure means "condition not met",
        // never a crash of the trigger evaluation loop
        Flow listening = listeningFlow(flowTrigger().when("{{ invalid-pebble-expression() }}").build());

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstreamFlow(), State.Type.SUCCESS), listening))
            .isEmpty();
    }

    // --- lane split: conditions-only vs dependsOn-only

    @Test
    void shouldIgnoreDependsOnTriggersWhenComputingStandardConditions() {
        // Given: a dependsOn trigger must not be evaluated twice — the conditions lane skips it
        Flow upstream = upstreamFlow();
        Flow listening = listeningFlow(dependsOnTrigger(upstream).build());

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerConditions(executionOf(upstream, State.Type.SUCCESS), listening))
            .isEmpty();
    }

    @Test
    void shouldIgnoreStandardTriggersWhenComputingDependsOn() {
        // Given: symmetrically, the dependsOn lane skips triggers without dependsOn
        Flow listening = listeningFlow(flowTrigger().build());

        // When / Then
        assertThat(flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(executionOf(upstreamFlow(), State.Type.SUCCESS), listening, multipleConditionStateStore))
            .isEmpty();
    }

    // --- dependsOn: multiple-condition window accumulation

    @Test
    void shouldFireOnlyWhenAllDependenciesSucceededWithinTheWindow() {
        // Given: a trigger depending on two upstream flows
        Flow upstreamA = upstreamFlow();
        Flow upstreamB = upstreamFlow();
        Flow listening = listeningFlow(dependsOnTrigger(upstreamA, upstreamB).build());

        // When: only upstream A terminates
        List<Execution> afterA = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore);

        // Then: nothing fires yet, but the window persisted A's satisfied condition
        assertThat(afterA).isEmpty();
        assertThat(multipleConditionStateStore.all()).hasSize(1);
        assertThat(satisfiedConditions(multipleConditionStateStore.all().getFirst())).isEqualTo(1);

        // When: upstream B terminates within the same window
        List<Execution> afterB = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamB, State.Type.SUCCESS), listening, multipleConditionStateStore);

        // Then: the trigger fires once, and with no explicit window the default is fire-once —
        // the satisfied window is reset (deleted) so the same pair must succeed again to re-fire
        assertThat(afterB).hasSize(1);
        assertThat(afterB.getFirst().getFlowId()).isEqualTo(listening.getId());
        assertThat(multipleConditionStateStore.all()).isEmpty();
    }

    @Test
    void shouldNotFireWhenOnlyOneDependencyKeepsSucceeding() {
        // Given
        Flow upstreamA = upstreamFlow();
        Flow upstreamB = upstreamFlow();
        Flow listening = listeningFlow(dependsOnTrigger(upstreamA, upstreamB).build());

        // When: upstream A succeeds twice, B never runs
        flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore);
        List<Execution> afterSecondA = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore);

        // Then: still waiting on B, single accumulated window
        assertThat(afterSecondA).isEmpty();
        assertThat(multipleConditionStateStore.all()).hasSize(1);
        assertThat(satisfiedConditions(multipleConditionStateStore.all().getFirst())).isEqualTo(1);
    }

    @Test
    void shouldKeepWindowWhenExplicitWindowIsNotFireOnce() {
        // Given: an explicit window defaults to fireOnce=false — the trigger may re-fire within
        // the same window, so a successful evaluation must NOT reset the window state
        Flow upstreamA = upstreamFlow();
        Flow upstreamB = upstreamFlow();
        Flow listening = listeningFlow(
            dependsOnTrigger(upstreamA, upstreamB).window(Window.builder().build()).build());

        // When: both dependencies succeed
        flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore);
        List<Execution> afterB = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamB, State.Type.SUCCESS), listening, multipleConditionStateStore);

        // Then: fires, and the fully-satisfied window survives
        assertThat(afterB).hasSize(1);
        assertThat(multipleConditionStateStore.all()).hasSize(1);
        assertThat(satisfiedConditions(multipleConditionStateStore.all().getFirst())).isEqualTo(2);
    }

    @Test
    void shouldPurgeExpiredWindowsWhenComputingDependsOn() {
        // Given: a stale window from a past evaluation window, plus a live dependsOn trigger
        MultipleConditionWindow expired = MultipleConditionWindow.builder()
            .tenantId(Flows.TENANT)
            .namespace(Flows.NAMESPACE)
            .flowId("some-old-flow")
            .conditionId("some-old-condition")
            .start(ZonedDateTime.parse("2020-01-01T00:00:00Z"))
            .end(ZonedDateTime.parse("2020-01-02T00:00:00Z"))
            .results(Map.of())
            .build();
        multipleConditionStateStore.save(expired);
        Flow upstreamA = upstreamFlow();
        Flow listening = listeningFlow(dependsOnTrigger(upstreamA, upstreamFlow()).build());

        // When: any dependsOn evaluation runs
        flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore);

        // Then: the expired window is gone; only the freshly-created one remains
        assertThat(multipleConditionStateStore.all()).hasSize(1);
        assertThat(multipleConditionStateStore.all().getFirst().getFlowId()).isEqualTo(listening.getId());
    }

    // --- fixtures

    private static Flow upstreamFlow() {
        return Flows.builder(logTask()).build();
    }

    private static Flow listeningFlow(io.kestra.plugin.core.trigger.Flow trigger) {
        return listeningFlowBuilder(trigger).build();
    }

    private static Flow.FlowBuilder<?, ?> listeningFlowBuilder(io.kestra.plugin.core.trigger.Flow trigger) {
        return Flows.builder(logTask()).triggers(List.of(trigger));
    }

    private static io.kestra.plugin.core.trigger.Flow.FlowBuilder<?, ?> flowTrigger() {
        return io.kestra.plugin.core.trigger.Flow.builder()
            .id("flow-trigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName());
    }

    private static io.kestra.plugin.core.trigger.Flow.FlowBuilder<?, ?> dependsOnTrigger(Flow... upstreams) {
        return flowTrigger()
            .dependsOn(java.util.Arrays.stream(upstreams)
                .map(upstream -> io.kestra.plugin.core.trigger.Flow.Dependency.builder()
                    .namespace(upstream.getNamespace())
                    .flowId(upstream.getId())
                    .build())
                .toList());
    }

    private static Execution executionOf(Flow flow, State.Type state) {
        return Execution.newExecution(flow, List.of()).withState(state);
    }

    private static long satisfiedConditions(MultipleConditionWindow window) {
        return window.getResults().values().stream().filter(Boolean::booleanValue).count();
    }

    private static Log logTask() {
        return Log.builder()
            .id("log")
            .type(Log.class.getName())
            .message("Hello World")
            .build();
    }
}
