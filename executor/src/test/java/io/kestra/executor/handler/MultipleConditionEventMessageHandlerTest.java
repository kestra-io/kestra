package io.kestra.executor.handler;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.MultipleConditionEvent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

@KestraTest
class MultipleConditionEventMessageHandlerTest {
    @Inject
    private MultipleConditionEventMessageHandler multipleConditionEventMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldHandleAMessage() {
        var flow = Fixtures.flow();
        flowRepository.create(GenericFlow.of(flow));
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var multipleConditionEvent = new MultipleConditionEvent(flow, execution);

        multipleConditionEventMessageHandler.handle(multipleConditionEvent);
    }
}