package io.kestra.executor;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.kestra.core.repositories.AbstractFlowRepositoryTest.TEST_NAMESPACE;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class FlowTriggerServiceTest {
    private static final List<Label> EMPTY_LABELS = List.of();

    @Inject
    private TestRunContextFactory runContextFactory;
    @Inject
    private ConditionService conditionService;
    @Inject
    private FlowService flowService;
    private FlowTriggerService flowTriggerService;

    @BeforeEach
    void setUp() {
        flowTriggerService = new FlowTriggerService(conditionService, runContextFactory, flowService);
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

    private static Log simpleLogTask() {
        return Log.builder()
            .id(IdUtils.create())
            .type(Log.class.getName())
            .message("Hello World")
            .build();
    }
}