package io.kestra.core.services;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.kestra.core.repositories.AbstractFlowRepositoryTest.TEST_NAMESPACE;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class FlowTriggerServiceTest {
    public static final List<Label> EMPTY_LABELS = List.of();
    public static final Optional<MultipleConditionStorageInterface> EMPTY_MULTIPLE_CONDITION_STORAGE = Optional.empty();

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
            .triggers(List.of(
                flowTriggerWithNoConditions()
            ))
            .build();

        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.SUCCESS);

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggers(
            simpleFlowExecution,
            List.of(simpleFlow, flowWithFlowTrigger),
            EMPTY_MULTIPLE_CONDITION_STORAGE
        );

        assertThat(resultingExecutionsToRun).size().isEqualTo(1);
        assertThat(resultingExecutionsToRun.get(0).getFlowId()).isEqualTo(flowWithFlowTrigger.getId());
    }

    @Test
    void computeExecutionsFromFlowTriggers_filteringOutCreatedExecutions() {
        var simpleFlow = aSimpleFlow();
        var flowWithFlowTrigger = Flow.builder()
            .id("flow-with-flow-trigger")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .triggers(List.of(
                flowTriggerWithNoConditions()
            ))
            .build();

        var simpleFlowExecution = Execution.newExecution(simpleFlow, EMPTY_LABELS).withState(State.Type.CREATED);

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggers(
            simpleFlowExecution,
            List.of(simpleFlow, flowWithFlowTrigger),
            EMPTY_MULTIPLE_CONDITION_STORAGE
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
            .triggers(List.of(
                flowTriggerWithNoConditions()
            ))
            .build();

        var simpleFlowExecutionComingFromATest = Execution.newExecution(simpleFlow, EMPTY_LABELS)
            .withState(State.Type.SUCCESS)
            .toBuilder()
            .kind(ExecutionKind.TEST)
            .build();

        var resultingExecutionsToRun = flowTriggerService.computeExecutionsFromFlowTriggers(
            simpleFlowExecutionComingFromATest,
            List.of(simpleFlow, flowWithFlowTrigger),
            EMPTY_MULTIPLE_CONDITION_STORAGE
        );

        assertThat(resultingExecutionsToRun).size().isEqualTo(0);
    }

    private static Flow aSimpleFlow() {
        return Flow.builder()
            .id("simple-flow")
            .namespace(TEST_NAMESPACE)
            .tenantId(MAIN_TENANT)
            .tasks(List.of(simpleLogTask()))
            .build();
    }

    private static io.kestra.plugin.core.trigger.Flow flowTriggerWithNoConditions() {
        return io.kestra.plugin.core.trigger.Flow.builder()
            .id("flowTrigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName())
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