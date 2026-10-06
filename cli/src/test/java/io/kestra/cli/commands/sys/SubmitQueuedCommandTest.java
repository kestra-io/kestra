package io.kestra.cli.commands.sys;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import io.kestra.jdbc.runner.AbstractJdbcExecutionQueuedStateStore;

import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitQueuedCommandTest {
    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private AbstractJdbcExecutionQueuedStateStore queuedStateStore;

    @Mock
    private DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @InjectMocks
    private SubmitQueuedCommand command;

    private ByteArrayOutputStream output;
    private PrintStream capturedOut;
    private PrintStream originalOut;
    private String originalThreadName;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        originalThreadName = Thread.currentThread().getName();
        output = new ByteArrayOutputStream();
        capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8);
        System.setOut(capturedOut);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        Thread.currentThread().setName(originalThreadName);
        capturedOut.close();
    }

    @Test
    void shouldSkipSubmissionWhenQueueTypeIsMissing() throws Exception {
        when(applicationContext.getProperty("kestra.queue.type", String.class)).thenReturn(Optional.empty());

        assertThat(command.call()).isZero();

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("'kestra.queue.type' configuration is not set");
        assertNoSubmission();
    }

    @Test
    void shouldRejectKafkaAndRecommendTheEeCommandWhenQueueTypeIsKafka() throws Exception {
        when(applicationContext.getProperty("kestra.queue.type", String.class)).thenReturn(Optional.of("kafka"));

        assertThat(command.call()).isEqualTo(1);

        assertThat(output.toString(StandardCharsets.UTF_8))
            .contains("configuration is set to 'kafka'", "use the corresponding sys-ee command");
        assertNoSubmission();
    }

    @Test
    void shouldRejectSubmissionWhenQueueTypeIsUnknown() throws Exception {
        when(applicationContext.getProperty("kestra.queue.type", String.class)).thenReturn(Optional.of("unknown"));

        assertThat(command.call()).isEqualTo(1);

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Unable to submit queued executions", "unknown type");
        assertNoSubmission();
    }

    @ParameterizedTest
    @ValueSource(strings = { "h2", "postgres", "mysql" })
    void shouldSubmitQueuedExecutionsAcrossTenantsWhenQueueTypeIsJdbc(String queueType) throws Exception {
        when(applicationContext.getProperty("kestra.queue.type", String.class)).thenReturn(Optional.of(queueType));
        when(applicationContext.getBean(AbstractJdbcExecutionQueuedStateStore.class)).thenReturn(queuedStateStore);
        when(queuedStateStore.getAllForAllTenants()).thenReturn(
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
    void shouldReportZeroSubmissionsWhenTheJdbcQueueIsEmpty() throws Exception {
        when(applicationContext.getProperty("kestra.queue.type", String.class)).thenReturn(Optional.of("h2"));
        when(applicationContext.getBean(AbstractJdbcExecutionQueuedStateStore.class)).thenReturn(queuedStateStore);
        when(queuedStateStore.getAllForAllTenants()).thenReturn(List.of());

        assertThat(command.call()).isZero();

        verifyNoInteractions(executionCommandQueue);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Successfully submitted 0 queued executions");
    }

    private void assertNoSubmission() {
        verify(applicationContext, never()).getBean(AbstractJdbcExecutionQueuedStateStore.class);
        verifyNoInteractions(queuedStateStore, executionCommandQueue);
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
