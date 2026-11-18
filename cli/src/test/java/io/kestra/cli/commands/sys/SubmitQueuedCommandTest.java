package io.kestra.cli.commands.sys;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Unqueue;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitQueuedCommandTest {
    @Mock
    private ExecutionQueuedStateStore executionQueuedStateStore;

    @Mock
    private DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @InjectMocks
    private SubmitQueuedCommand command;

    private ByteArrayOutputStream output;
    private PrintStream capturedOut;
    private PrintStream originalOut;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        output = new ByteArrayOutputStream();
        capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8);
        System.setOut(capturedOut);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        capturedOut.close();
    }

    @Test
    void shouldSubmitQueuedExecutionsAcrossTenantsWhenExecutionsAreQueued() throws Exception {
        when(executionQueuedStateStore.getAllForAllTenants()).thenReturn(
            List.of(
                queuedExecution("tenant-a", "namespace-a", "flow-a", "execution-a"),
                queuedExecution("tenant-b", "namespace-b", "flow-b", "execution-b")
            )
        );

        assertThat(command.call()).isZero();

        ArgumentCaptor<ExecutionCommand> captor = ArgumentCaptor.forClass(ExecutionCommand.class);
        verify(executionCommandQueue, times(2)).emit(captor.capture());
        verifyNoMoreInteractions(executionCommandQueue);
        assertThat(captor.getAllValues()).hasOnlyElementsOfType(Unqueue.class);
        assertThat(captor.getAllValues().stream().map(Unqueue.class::cast).toList())
            .extracting(Unqueue::tenantId, Unqueue::namespace, Unqueue::flowId, Unqueue::executionId, Unqueue::state)
            .containsExactlyInAnyOrder(
                tuple("tenant-a", "namespace-a", "flow-a", "execution-a", State.Type.RUNNING),
                tuple("tenant-b", "namespace-b", "flow-b", "execution-b", State.Type.RUNNING)
            );
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Successfully submitted 2 queued executions");
    }

    @Test
    void shouldReportZeroSubmissionsWhenNothingIsQueued() throws Exception {
        when(executionQueuedStateStore.getAllForAllTenants()).thenReturn(List.of());

        assertThat(command.call()).isZero();

        verifyNoInteractions(executionCommandQueue);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Successfully submitted 0 queued executions");
    }

    private static ExecutionQueued queuedExecution(String tenantId, String namespace, String flowId, String executionId) {
        Execution execution = Execution.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .flowId(flowId)
            .id(executionId)
            .flowRevision(1)
            .state(new State(State.Type.QUEUED))
            .build();
        return ExecutionQueued.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .flowId(flowId)
            .execution(execution)
            .date(Instant.now())
            .build();
    }
}
