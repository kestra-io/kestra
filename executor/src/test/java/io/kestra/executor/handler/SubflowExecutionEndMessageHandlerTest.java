package io.kestra.executor.handler;

import java.util.Collections;

import io.micronaut.test.annotation.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.SubflowExecutionEnd;

import jakarta.inject.Inject;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class SubflowExecutionEndMessageHandlerTest {
    @Inject
    private SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    KillSwitchService killSwitchService;

    @MockBean(KillSwitchService.class)
    KillSwitchService killSwitchService() {
        return mock(KillSwitchService.class);
    }

    @BeforeEach
    void setUp() {
        when(killSwitchService.evaluate(any(Execution.class))).thenReturn(EvaluationType.PASS);
        when(killSwitchService.evaluate(anyString())).thenReturn(EvaluationType.PASS);
    }

    @Test
    void shouldHandleAMessage() {
        var flow = flowRepository.create(GenericFlow.of(Fixtures.flow()));
        var parentExecution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(parentExecution);
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);

        var subflowExecutionEnd = new SubflowExecutionEnd(
            execution,
            parentExecution.getId(),
            "task",
            "taskRun",
            State.Type.SUCCESS
        );

        subflowExecutionEndMessageHandler.handle(subflowExecutionEnd);
    }

    @Test
    void shouldNotCallLockWhenChildExecutionKillSwitched() {
        // Given — child execution is kill-switched
        var flow = Fixtures.flow();
        var childExecution = Execution.newExecution(flow, Collections.emptyList());
        var message = new SubflowExecutionEnd(childExecution, "parent-exec-1", "taskrun-1", "task-1", State.Type.SUCCESS);
        when(killSwitchService.evaluate(any(Execution.class))).thenReturn(EvaluationType.IGNORE);

        // When — handler returns early, no exception
        subflowExecutionEndMessageHandler.handle(message);
    }

    @Test
    void shouldNotCallLockWhenParentExecutionKillSwitched() {
        // Given — child passes but parent is kill-switched
        var flow = Fixtures.flow();
        var childExecution = Execution.newExecution(flow, Collections.emptyList());
        var message = new SubflowExecutionEnd(childExecution, "parent-exec-1", "taskrun-1", "task-1", State.Type.SUCCESS);
        when(killSwitchService.evaluate("parent-exec-1")).thenReturn(EvaluationType.IGNORE);

        // When — handler returns early, no exception
        subflowExecutionEndMessageHandler.handle(message);
    }
}
