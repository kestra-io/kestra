package io.kestra.executor.handler;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.executor.command.Create;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MultipleConditionEventMessageHandlerTest {
    private ExecutorTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = ExecutorTestHarness.create();
    }

    @Test
    void shouldEmitExecutionCommandWhenFlowTriggerComputesAnExecution() {
        // Given
        var flow = Fixtures.flow();
        harness.registerFlow(Flows.of(flow));
        var execution = Execution.newExecution(flow, List.of());
        harness.executionStateStore().save(execution);

        var triggeredFlow = Fixtures.flow();
        var triggered = Execution.newExecution(triggeredFlow, List.of());
        when(harness.flowTriggerService().computeExecutionsFromFlowTriggerDependsOn(execution, flow, harness.multipleConditionStateStore()))
            .thenReturn(List.of(triggered));

        // When
        harness.multipleConditionEventMessageHandler().handle(new MultipleConditionEvent(flow, execution));

        // Then
        assertThat(harness.executionCommandQueue().emitted()).hasSize(1);
        var command = harness.executionCommandQueue().emitted().getFirst();
        assertThat(command).isInstanceOf(Create.class);
        var create = (Create) command;
        assertThat(create.tenantId()).isEqualTo(triggered.getTenantId());
        assertThat(create.namespace()).isEqualTo(triggered.getNamespace());
        assertThat(create.flowId()).isEqualTo(triggered.getFlowId());
        assertThat(create.executionId()).isEqualTo(triggered.getId());
        assertThat(create.flowRevision()).isEqualTo(triggered.getFlowRevision());
        assertThat(create.kind()).isEqualTo(triggered.getKind());
        assertThat(create.trigger()).isEqualTo(triggered.getTrigger());
        assertThat(create.labels()).isEqualTo(triggered.getLabels());
        assertThat(create.inputs()).isEqualTo(triggered.getInputs());
        // the triggered execution is not terminated, so its state is not carried over
        assertThat(create.stateType()).isNull();
    }

    @Test
    void shouldPreserveTerminalStateWhenComputedExecutionIsTerminated() {
        // Given
        var flow = Fixtures.flow();
        harness.registerFlow(Flows.of(flow));
        var execution = Execution.newExecution(flow, List.of());
        harness.executionStateStore().save(execution);

        var triggeredFlow = Fixtures.flow();
        var triggered = Execution.newExecution(triggeredFlow, List.of()).withState(State.Type.FAILED);
        when(harness.flowTriggerService().computeExecutionsFromFlowTriggerDependsOn(execution, flow, harness.multipleConditionStateStore()))
            .thenReturn(List.of(triggered));

        // When
        harness.multipleConditionEventMessageHandler().handle(new MultipleConditionEvent(flow, execution));

        // Then
        assertThat(harness.executionCommandQueue().emitted()).hasSize(1);
        var create = (Create) harness.executionCommandQueue().emitted().getFirst();
        assertThat(create.executionId()).isEqualTo(triggered.getId());
        assertThat(create.stateType()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldEmitNothingWhenNoExecutionIsComputed() {
        // Given
        var flow = Fixtures.flow();
        harness.registerFlow(Flows.of(flow));
        var execution = Execution.newExecution(flow, List.of());
        harness.executionStateStore().save(execution);

        when(harness.flowTriggerService().computeExecutionsFromFlowTriggerDependsOn(execution, flow, harness.multipleConditionStateStore()))
            .thenReturn(List.of());

        // When
        harness.multipleConditionEventMessageHandler().handle(new MultipleConditionEvent(flow, execution));

        // Then
        assertThat(harness.executionCommandQueue().emitted()).isEmpty();
    }
}
