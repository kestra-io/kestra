package io.kestra.executor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.kestra.core.repositories.AbstractFlowRepositoryTest.TEST_NAMESPACE;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MicronautTest
class FlowTriggerServiceTest {
    private static final List<Label> EMPTY_LABELS = List.of();

    @Inject
    private TestRunContextFactory runContextFactory;
    @Inject
    private ConditionService conditionService;
    @Inject
    private FlowService flowService;
    private FlowMetaStoreInterface flowMetaStore;
    private FlowTriggerService flowTriggerService;

    @BeforeEach
    void setUp() {
        flowMetaStore = mock(FlowMetaStoreInterface.class);
        flowTriggerService = new FlowTriggerService(conditionService, runContextFactory, flowService, flowMetaStore);
    }

    @Test
    void computeExecutionsFromFlowTriggers_ok() {
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(
                List.of(
                    flowTriggerWithNoConditions()
                )
            )
            .build();

        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            flowWithFlowTrigger
        );

        assertThat(resultingExecutionsToRun).size().isEqualTo(1);
        assertThat(resultingExecutionsToRun.getFirst().getFlowId()).isEqualTo(flowWithFlowTrigger.getId());
    }

    @Test
    void computeExecutionsFromFlowTriggersShouldReturnForNormalCondition() {
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(
                List.of(
                    flowTriggerWithNoConditions()
                )
            )
            .build();

        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS).toBuilder().kind(ExecutionKind.NORMAL).build();

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            flowWithFlowTrigger
        );

        assertThat(resultingExecutionsToRun).size().isEqualTo(1);
        assertThat(resultingExecutionsToRun.getFirst().getFlowId()).isEqualTo(flowWithFlowTrigger.getId());
    }

    @Test
    void computeExecutionsFromFlowTriggers_none() {
        var simpleFlow = aSimpleFlow();

        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            simpleFlow
        );

        assertThat(resultingExecutionsToRun).isEmpty();
    }

    @Test
    void computeExecutionsFromFlowTriggers_filteringOutCreatedExecutions() {
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(
                List.of(
                    flowTriggerWithNoConditions()
                )
            )
            .build();

        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.CREATED);

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            flowWithFlowTrigger
        );

        assertThat(resultingExecutionsToRun).size().isEqualTo(0);
    }

    @Test
    void computeExecutionsFromFlowTriggers_filteringOutTestExecutions() {
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(
                List.of(
                    flowTriggerWithNoConditions()
                )
            )
            .build();

        var simpleFlowExecutionComingFromATest = Execution.newExecution(simpleFlow, EMPTY_LABELS)
            .withState(State.Type.SUCCESS)
            .toBuilder()
            .kind(ExecutionKind.TEST)
            .build();

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecutionComingFromATest,
            flowWithFlowTrigger
        );

        assertThat(resultingExecutionsToRun).size().isEqualTo(0);
    }

    @Test
    void computeExecutionsFromFlowTriggers_filteringOutDraftFlows() {
        // A draft flow must never be triggered implicitly. A Flow trigger defined on a flow whose
        // latest revision is a draft should not fire, mirroring webhooks/schedules/subflows which
        // all resolve to the latest non-draft revision. Reproduces the gap: FlowTriggerService
        // filters disabled flows but not drafts, so a draft's Flow trigger currently fires.
        var simpleFlow = aSimpleFlow();
        var draftFlowWithFlowTrigger = Flow.builder()
            .id("draft-flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .draft(true)
            .tasks(List.of(simpleLogTask()))
            .triggers(
                List.of(
                    flowTriggerWithNoConditions()
                )
            )
            .build();

        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            draftFlowWithFlowTrigger
        );

        assertThat(resultingExecutionsToRun).isEmpty();
    }

    @Test
    void shouldBuildExecutionFromFlowResolvedForRuntimeWhenAFlowTriggerMatches() {
        // Given a flow whose trigger matches, resolved for runtime with an extra label and variable
        FlowWithSource raw = flowWithFlowTriggerSource();
        FlowWithSource resolved = raw.toBuilder()
            .labels(List.of(new Label("team", "platform")))
            .variables(Map.of("env", "prod"))
            .build();
        when(flowMetaStore.findByIdForRuntime(MAIN_TENANT, TEST_NAMESPACE, raw.getId(), Optional.of(1)))
            .thenReturn(Optional.of(resolved));
        var simpleFlowExecution = Execution.newExecution(aSimpleFlow(), EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(simpleFlowExecution, raw);

        // Then the execution snapshots the resolved flow, not the raw one
        assertThat(resultingExecutionsToRun).hasSize(1);
        assertThat(resultingExecutionsToRun.getFirst().getLabels()).contains(new Label("team", "platform"));
        assertThat(resultingExecutionsToRun.getFirst().getVariables()).containsEntry("env", "prod");
    }

    @Test
    void shouldNotResolveFlowForRuntimeWhenNoFlowTriggerMatches() {
        // Given a flow whose trigger condition does not match
        FlowWithSource neverMatching = flowWithFlowTriggerSource().toBuilder()
            .triggers(List.of(flowTriggerWithWhen("false")))
            .build();
        var simpleFlowExecution = Execution.newExecution(aSimpleFlow(), EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(simpleFlowExecution, neverMatching);

        // Then nothing is parsed for runtime: this runs on every state transition of every execution
        assertThat(resultingExecutionsToRun).isEmpty();
        verify(flowMetaStore, never()).findByIdForRuntime(any(), any(), any(), any());
    }

    @Test
    void shouldStillCreateExecutionWhenResolvedFlowIsBlocked() {
        // Given a flow whose trigger matches but which governance blocks at runtime
        FlowWithSource raw = flowWithFlowTriggerSource();
        when(flowMetaStore.findByIdForRuntime(MAIN_TENANT, TEST_NAMESPACE, raw.getId(), Optional.of(1)))
            .thenReturn(Optional.of(FlowWithException.from(raw, new FlowBlockedException("Blocked by governance policy"))));
        var simpleFlowExecution = Execution.newExecution(aSimpleFlow(), EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(simpleFlowExecution, raw);

        // Then the trigger still fires so the executor fails the execution, rather than going silent
        assertThat(resultingExecutionsToRun).hasSize(1);
    }

    @Test
    void shouldSnapshotFlowVariablesOnExecutionsComputedFromFlowTriggers() {
        // Given a triggered flow declaring variables
        var simpleFlow = aSimpleFlow();
        var triggeredFlow = flowWithFlowTrigger().toBuilder()
            .variables(Map.of("env", "prod"))
            .build();
        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            triggeredFlow
        );

        // Then the created execution carries them, like every other execution created from a flow
        assertThat(resultingExecutionsToRun).hasSize(1);
        assertThat(resultingExecutionsToRun.getFirst().getVariables()).containsEntry("env", "prod");
    }

    @Test
    void shouldPreferResolvedLabelOverRawOneWhenAFlowTriggerMatches() {
        // Given a flow whose raw label governance overrides at runtime
        FlowWithSource raw = flowWithFlowTriggerSource().toBuilder()
            .labels(List.of(new Label("env", "dev")))
            .build();
        FlowWithSource resolved = raw.toBuilder()
            .labels(List.of(new Label("env", "prod")))
            .build();
        when(flowMetaStore.findByIdForRuntime(MAIN_TENANT, TEST_NAMESPACE, raw.getId(), Optional.of(1)))
            .thenReturn(Optional.of(resolved));
        var simpleFlowExecution = Execution.newExecution(aSimpleFlow(), EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(simpleFlowExecution, raw);

        // Then the override wins, so the raw value never pins itself back onto the execution
        assertThat(resultingExecutionsToRun).hasSize(1);
        assertThat(resultingExecutionsToRun.getFirst().getLabels()).contains(new Label("env", "prod"));
        assertThat(resultingExecutionsToRun.getFirst().getLabels()).doesNotContain(new Label("env", "dev"));
    }

    @Test
    void shouldBuildExecutionFromFlowResolvedForRuntimeWhenADependsOnFlowTriggerMatches() {
        // Given a dependsOn trigger whose window is satisfied, on a flow governance mutates at runtime
        FlowWithSource raw = flowWithFlowTriggerSource().toBuilder()
            .labels(List.of(new Label("env", "dev")))
            .triggers(List.of(flowTriggerDependingOn(aSimpleFlow())))
            .build();
        FlowWithSource resolved = raw.toBuilder()
            .labels(List.of(new Label("env", "prod"), new Label("team", "platform")))
            .variables(Map.of("env", "prod"))
            .build();
        when(flowMetaStore.findByIdForRuntime(MAIN_TENANT, TEST_NAMESPACE, raw.getId(), Optional.of(1)))
            .thenReturn(Optional.of(resolved));
        var simpleFlowExecution = Execution.newExecution(aSimpleFlow(), EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            simpleFlowExecution,
            raw,
            new SatisfiedWindowStateStore()
        );

        // Then this route snapshots the resolved flow too, though its execution is created through a command
        assertThat(resultingExecutionsToRun).hasSize(1);
        assertThat(resultingExecutionsToRun.getFirst().getLabels()).contains(new Label("team", "platform"));
        assertThat(resultingExecutionsToRun.getFirst().getLabels()).contains(new Label("env", "prod"));
        assertThat(resultingExecutionsToRun.getFirst().getLabels()).doesNotContain(new Label("env", "dev"));
        assertThat(resultingExecutionsToRun.getFirst().getVariables()).containsEntry("env", "prod");
    }

    @Test
    void shouldNotResolveFlowForRuntimeWhenNoDependsOnFlowTriggerMatches() {
        // Given a dependsOn trigger whose standard condition does not match
        FlowWithSource neverMatching = flowWithFlowTriggerSource().toBuilder()
            .triggers(List.of(flowTriggerDependingOn(aSimpleFlow(), "false")))
            .build();
        var simpleFlowExecution = Execution.newExecution(aSimpleFlow(), EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(
            simpleFlowExecution,
            neverMatching,
            new SatisfiedWindowStateStore()
        );

        // Then the flow is never parsed for runtime, as a window is processed far more often than it fires
        assertThat(resultingExecutionsToRun).isEmpty();
        verify(flowMetaStore, never()).findByIdForRuntime(any(), any(), any(), any());
    }

    @Test
    void shouldAssignCorrelationIdOnExecutionsComputedFromFlowTriggersWhenUpstreamCarriesNone() {
        // Given an upstream execution stripped of its correlation id
        var upstream = Execution.newExecution(aSimpleFlow(), EMPTY_LABELS)
            .withState(State.Type.SUCCESS)
            .withLabels(List.of(new Label("env", "dev")));

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(upstream, flowWithFlowTrigger());

        // Then the triggered execution opens its own correlation rather than going without one
        assertThat(resultingExecutionsToRun).hasSize(1);
        assertThat(resultingExecutionsToRun.getFirst().getLabels())
            .anyMatch(label -> Label.CORRELATION_ID.equals(label.key()) && label.value() != null);
    }

    @Test
    void shouldCarryParentCorrelationIdOnExecutionsComputedFromFlowTriggers() {
        // Given an upstream execution already carrying a correlation id
        var upstream = Execution.newExecution(aSimpleFlow(), EMPTY_LABELS).withState(State.Type.SUCCESS);
        String correlationId = upstream.getLabels().stream()
            .filter(label -> Label.CORRELATION_ID.equals(label.key()))
            .findFirst()
            .orElseThrow()
            .value();

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(upstream, flowWithFlowTrigger());

        // Then the triggered execution stays on the upstream correlation instead of starting a new one
        assertThat(resultingExecutionsToRun).hasSize(1);
        assertThat(resultingExecutionsToRun.getFirst().getLabels()).contains(new Label(Label.CORRELATION_ID, correlationId));
    }

    private static Flow flowWithFlowTrigger() {
        return Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(List.of(flowTriggerWithNoConditions()))
            .build();
    }

    private static FlowWithSource flowWithFlowTriggerSource() {
        return FlowWithSource.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .revision(1)
            .tasks(List.of(simpleLogTask()))
            .triggers(List.of(flowTriggerWithNoConditions()))
            .build();
    }

    private static Flow aSimpleFlow() {
        return Flow.builder()
            .id("simple-flow")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .build();
    }

    @Test
    void computeExecutionsFromFlowTriggers_whenFalse() {
        // Given
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(List.of(flowTriggerWithWhen("false")))
            .build();
        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            flowWithFlowTrigger
        );

        // Then
        assertThat(resultingExecutionsToRun).isEmpty();
    }

    @Test
    void computeExecutionsFromFlowTriggers_whenExpressionTruthy() {
        // Given - 'when' renders to the flow ID (a non-empty string, truthy)
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(List.of(flowTriggerWithWhen("{{ flow.id }}")))
            .build();
        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            flowWithFlowTrigger
        );

        // Then
        assertThat(resultingExecutionsToRun).hasSize(1);
        assertThat(resultingExecutionsToRun.getFirst().getFlowId()).isEqualTo(flowWithFlowTrigger.getId());
    }

    @Test
    void computeExecutionsFromFlowTriggers_whenInvalidExpression() {
        // Given - malformed Pebble expression causes IllegalVariableEvaluationException, treated as false
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(List.of(flowTriggerWithWhen("{{ invalid-pebble-expression() }}")))
            .build();
        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            flowWithFlowTrigger
        );

        // Then
        assertThat(resultingExecutionsToRun).isEmpty();
    }

    @Test
    void computeExecutionsFromFlowTriggers_whenStartWithNamespace() {
        // Given - malformed Pebble expression causes IllegalVariableEvaluationException, treated as false
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(List.of(flowTriggerWithWhen("{{ flow.namespace | startsWith('io.kestra') }}")))
            .build();
        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        // When
        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggerConditions(
            simpleFlowExecution,
            flowWithFlowTrigger
        );

        // Then
        assertThat(resultingExecutionsToRun).hasSize(1);
        assertThat(resultingExecutionsToRun.getFirst().getFlowId()).isEqualTo(flowWithFlowTrigger.getId());
    }

    private static io.kestra.plugin.core.trigger.Flow flowTriggerWithNoConditions() {
        return io.kestra.plugin.core.trigger.Flow.builder()
            .id("flowTrigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName())
            .build();
    }

    private static io.kestra.plugin.core.trigger.Flow flowTriggerWithWhen(String when) {
        return io.kestra.plugin.core.trigger.Flow.builder()
            .id("flowTrigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName())
            .when(when)
            .build();
    }

    private static io.kestra.plugin.core.trigger.Flow flowTriggerDependingOn(Flow upstream) {
        return flowTriggerDependingOn(upstream, null);
    }

    private static io.kestra.plugin.core.trigger.Flow flowTriggerDependingOn(Flow upstream, String when) {
        return io.kestra.plugin.core.trigger.Flow.builder()
            .id("flowTrigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName())
            .when(when)
            .dependsOn(
                List.of(
                    io.kestra.plugin.core.trigger.Flow.Dependency.builder()
                        .namespace(upstream.getNamespace())
                        .flowId(upstream.getId())
                        .build()
                )
            )
            .build();
    }

    private static Log simpleLogTask() {
        return Log.builder()
            .id(IdUtils.create())
            .type(Log.class.getName())
            .message("Hello World")
            .build();
    }

    /**
     * Hands the trigger a fresh window and runs the consumer inline, so a single upstream execution is enough
     * to satisfy the dependsOn and fire.
     */
    private static class SatisfiedWindowStateStore implements MultipleConditionStateStore {
        @Override
        public Optional<MultipleConditionWindow> get(FlowId flow, String conditionId) {
            return Optional.empty();
        }

        @Override
        public List<MultipleConditionWindow> expired(String tenantId) {
            return List.of();
        }

        @Override
        public Execution process(FlowId flow, MultipleCondition multipleCondition, Map<String, Object> outputs,
            BiFunction<TransactionContext, MultipleConditionWindow, Execution> consumer) {
            return consumer.apply(null, create(flow, multipleCondition, outputs));
        }

        @Override
        public void save(TransactionContext txContext, MultipleConditionWindow multipleConditionWindow) {
        }

        @Override
        public void save(MultipleConditionWindow multipleConditionWindow) {
        }

        @Override
        public void delete(MultipleConditionWindow multipleConditionWindow) {
        }
    }
}