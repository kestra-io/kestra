package io.kestra.executor.statemachine;

import java.time.Instant;
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
import io.kestra.core.runners.configuration.ExecutionDepthConfiguration;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import io.kestra.executor.FlowTriggerService;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.InMemoryMultipleConditionStateStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision matrix for {@link FlowTriggerService} — which upstream execution fires which Flow
 */
class FlowTriggerDecisionTest {

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final InMemoryMultipleConditionStateStore multipleConditionStateStore = new InMemoryMultipleConditionStateStore();
    private final FlowTriggerService flowTriggerService = new FlowTriggerService(
        new ConditionService(),
        harness.runContextFactory(),
        // FlowService is field-injected and repository-backed; the only method FlowTriggerService
        // calls is removeUnwanted, which is pure — same pattern as the harness's ExecutionService
        Mockito.mock(FlowService.class, Mockito.CALLS_REAL_METHODS),
        harness.flowMetaStore(),
        harness.executionOutputService(),
        new ExecutionDepthConfiguration(100)
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

        assertThat(executions).as("one CREATED execution of the listening flow, carrying the upstream execution in its trigger variables").hasSize(1);
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
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore
        );

        assertThat(afterA).as("nothing fires yet, but the window persisted A's satisfied condition").isEmpty();
        assertThat(multipleConditionStateStore.all()).hasSize(1);
        assertThat(satisfiedConditions(multipleConditionStateStore.all().getFirst())).isEqualTo(1);

        // When: upstream B terminates within the same window
        List<Execution> afterB = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamB, State.Type.SUCCESS), listening, multipleConditionStateStore
        );

        assertThat(afterB)
            .as("the trigger fires once, and with no explicit window the default is fire-once — the satisfied window is reset (deleted) so the same pair must succeed again to re-fire")
            .hasSize(1);
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
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore
        );
        List<Execution> afterSecondA = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore
        );

        assertThat(afterSecondA).as("still waiting on B, single accumulated window").isEmpty();
        assertThat(multipleConditionStateStore.all()).hasSize(1);
        assertThat(satisfiedConditions(multipleConditionStateStore.all().getFirst())).isEqualTo(1);
    }

    @Test
    void shouldResetWindowAfterFiringEvenWithAnExplicitWindow() {
        // Given: an explicit window — since window.fireOnce was removed, the stored dependency
        // results are always reset after firing, otherwise any later terminal execution of an
        // unrelated flow would re-fire the trigger from the kept, fully-satisfied window
        Flow upstreamA = upstreamFlow();
        Flow upstreamB = upstreamFlow();
        Flow listening = listeningFlow(
            dependsOnTrigger(upstreamA, upstreamB).window(Window.builder().build()).build()
        );

        // When: both dependencies succeed
        flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore
        );
        List<Execution> afterB = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamB, State.Type.SUCCESS), listening, multipleConditionStateStore
        );

        assertThat(afterB).as("fires, and the satisfied window is reset").hasSize(1);
        assertThat(multipleConditionStateStore.all()).isEmpty();
    }

    @Test
    void shouldLeaveExpiredWindowsToThePurgeLoopWhenComputingDependsOn() {
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

        // When: a dependsOn evaluation runs
        flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            executionOf(upstreamA, State.Type.SUCCESS), listening, multipleConditionStateStore
        );

        assertThat(multipleConditionStateStore.all()).as("evaluating a trigger no longer purges — the stale window stays next to the fresh one")
            .extracting(MultipleConditionWindow::getFlowId)
            .containsExactlyInAnyOrder("some-old-flow", listening.getId());

        // and the executor's purge loop is what removes it
        multipleConditionStateStore.purgeExpired(Instant.now());
        assertThat(multipleConditionStateStore.all())
            .extracting(MultipleConditionWindow::getFlowId)
            .containsExactly(listening.getId());
    }

    // --- fixtures

    private static Flow upstreamFlow() {
        return Flows.builder(Flows.log()).build();
    }

    private static Flow listeningFlow(io.kestra.plugin.core.trigger.Flow trigger) {
        return listeningFlowBuilder(trigger).build();
    }

    private static Flow.FlowBuilder<?, ?> listeningFlowBuilder(io.kestra.plugin.core.trigger.Flow trigger) {
        return Flows.builder(Flows.log()).triggers(List.of(trigger));
    }

    private static io.kestra.plugin.core.trigger.Flow.FlowBuilder<?, ?> flowTrigger() {
        return io.kestra.plugin.core.trigger.Flow.builder()
            .id("flow-trigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName());
    }

    private static io.kestra.plugin.core.trigger.Flow.FlowBuilder<?, ?> dependsOnTrigger(Flow... upstreams) {
        return flowTrigger()
            .dependsOn(
                java.util.Arrays.stream(upstreams)
                    .map(
                        upstream -> io.kestra.plugin.core.trigger.Flow.Dependency.builder()
                            .namespace(upstream.getNamespace())
                            .flowId(upstream.getId())
                            .build()
                    )
                    .toList()
            );
    }

    private static Execution executionOf(Flow flow, State.Type state) {
        return Execution.newExecution(flow, List.of()).withState(state);
    }

    private static long satisfiedConditions(MultipleConditionWindow window) {
        return window.getResults().values().stream().filter(Boolean::booleanValue).count();
    }
}
