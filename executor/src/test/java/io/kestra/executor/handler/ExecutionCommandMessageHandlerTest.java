package io.kestra.executor.handler;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.debug.Breakpoint;
import io.kestra.core.events.EventId;
import io.kestra.core.executor.command.*;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.ExecutorContext;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
class ExecutionCommandMessageHandlerTest {
    @Inject
    private ExecutionCommandMessageHandler executionCommandMessageHandler;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TaskOutputRepositoryInterface taskOutputRepository;

    @Inject
    private BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationProcessedEventQueue;

    @Test
    @ExecuteFlow("flows/valids/failed-first.yaml")
    void restart(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        var command = Restart.from(execution, null);

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void pause() {
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.RUNNING);
        executionRepository.save(execution);
        var command = Pause.from(execution);

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.PAUSED);
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void unqueue() {
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.QUEUED);
        executionRepository.save(execution);
        var command = Unqueue.from(execution, State.Type.RUNNING);

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void forceRun() {
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var command = ForceRun.from(execution);

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
    }

    @Test
    @ExecuteFlow("flows/valids/failed-first.yaml")
    void changeTaskRunState(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        var command = ChangeTaskRunState.from(execution, execution.getTaskRunList().getFirst().getId(), State.Type.SUCCESS);

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
    }

    @Test
    void changeTaskRunStateShouldReturnEmptyWhenNoFlowFound() {
        var flow = Flow.builder()
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.tests")
            .id("not-found")
            .build();
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var command = ChangeTaskRunState.from(execution, "ignored", State.Type.SUCCESS);

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isEmpty();
    }

    @Test
    @ExecuteFlow("flows/valids/failed-first.yaml")
    void updateStatus(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        var command = UpdateStatus.from(execution, State.Type.SUCCESS);

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void resumeFromBreakpoint() {
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList())
            .withBreakpoints(List.of(Breakpoint.of("date")))
            .withTaskRunList(
                List.of(
                    TaskRun.builder()
                        .id("taskrun")
                        .state(new State(State.Type.BREAKPOINT))
                        .build()
                )
            )
            .withState(State.Type.BREAKPOINT);
        executionRepository.save(execution);
        var command = ResumeFromBreakpoint.from(execution, Optional.empty());

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
    }

    @Test
    @LoadFlows("flows/valids/pause-test.yaml")
    void resume() {
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "pause-test").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList())
            .withTaskRunList(
                List.of(
                    TaskRun.builder()
                        .id(IdUtils.create())
                        .taskId("pause")
                        .executionId("execution")
                        .namespace(flow.getNamespace())
                        .tenantId(flow.getTenantId())
                        .flowId(flow.getId())
                        .state(new State().withState(State.Type.PAUSED))
                        .build()
                )
            )
            .withState(State.Type.PAUSED);
        executionRepository.save(execution);
        var command = Resume.from(execution, io.kestra.plugin.core.flow.Pause.Resumed.now());

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
    }

    @Test
    void resumeShouldReturnEmptyWhenNoFlowFound() {
        var flow = Flow.builder()
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.tests")
            .id("not-found")
            .build();
        var execution = Execution.newExecution(flow, Collections.emptyList());
        executionRepository.save(execution);
        var command = Resume.from(execution, io.kestra.plugin.core.flow.Pause.Resumed.now());

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation")
    @LoadFlows("flows/valids/minimal.yaml")
    void handleShouldMigrateInlineTaskRunOutputsToRepository() throws Exception {
        // Given: an execution with a task run carrying deprecated inline outputs (pre-2.0 format)
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.RUNNING);

        String taskRunId = IdUtils.create();
        Map<String, Object> inlineOutputs = Map.of("value", "migrated");
        var taskRun = TaskRun.builder()
            .id(taskRunId)
            .taskId("date")
            .executionId(execution.getId())
            .namespace(flow.getNamespace())
            .tenantId(flow.getTenantId())
            .flowId(flow.getId())
            .state(new State())
            .outputs(Variables.inMemory(inlineOutputs))
            .build();

        execution = execution.withTaskRunList(List.of(taskRun));
        executionRepository.save(execution);
        var command = Pause.from(execution);

        // When
        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        // Then: inline outputs were persisted to the task output repository
        var savedOutput = taskOutputRepository.findById(flow.getTenantId(), taskRunId);
        assertThat(savedOutput).isPresent();
        assertThat(savedOutput.get().taskRunId()).isEqualTo(taskRunId);
        assertThat(savedOutput.get().executionId()).isEqualTo(execution.getId());

        // And: the returned execution has the deprecated outputs field cleared
        assertThat(handle).isPresent();
        var migratedTaskRun = handle.get().getExecution().findTaskRunByTaskRunId(taskRunId);
        assertThat(migratedTaskRun.getOutputs()).isNull();
    }

    @Test
    void invalidShouldReturnEmpty() {
        var command = new ExecutionCommand.Invalid("tenant", "namespace", "flow", IdUtils.create(), Instant.now(), EventId.create());

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isEmpty();
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void shouldReturnEmptyWhenNoExecutionFound() {
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.RUNNING);
        // we don't save the execution so it would not be found inside the message handler
        var command = Pause.from(execution);

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isEmpty();
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void shouldEmitSucceededProcessedEventWhenCommandCarriesOperationId() throws Exception {
        // Given: a running execution and a Pause command carrying an operationId
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.RUNNING);
        executionRepository.save(execution);
        var operationId = IdUtils.create();
        var command = Pause.from(execution).withOperationId(operationId);
        var future = subscribeForOperation(operationId);

        // When
        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        // Then: handler succeeds and emits a SUCCEEDED processed event for the same operation.
        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.PAUSED);
        AsyncOperationProcessedEvent event = future.get(5, TimeUnit.SECONDS);
        assertThat(event.operationId()).isEqualTo(operationId);
        assertThat(event.tenantId()).isEqualTo(execution.getTenantId());
        assertThat(event.itemId()).isEqualTo(execution.getId());
        assertThat(event.outcome()).isEqualTo(AsyncOperationProcessedEvent.Outcome.SUCCEEDED);
        assertThat(event.error()).isNull();
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void shouldNotEmitProcessedEventWhenCommandHasNoOperationId() throws Exception {
        // Given: a running execution and a Pause command WITHOUT operationId
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.RUNNING);
        executionRepository.save(execution);
        var command = Pause.from(execution);
        // Capture any event emitted during this test; if none is emitted, future will remain unresolved.
        CompletableFuture<AsyncOperationProcessedEvent> future = new CompletableFuture<>();
        QueueSubscriber<AsyncOperationProcessedEvent> subscriber = asyncOperationProcessedEventQueue.subscriber().subscribe(either -> {
            if (either.isLeft() && either.getLeft().itemId() != null && either.getLeft().itemId().equals(execution.getId())) {
                future.complete(either.getLeft());
            }
        });
        try {
            // When
            Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

            // Then: handler succeeds and NO processed event is emitted for this execution.
            assertThat(handle).isPresent();
            assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
        } finally {
            subscriber.close();
        }
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void shouldEmitFailedProcessedEventWhenBusinessLogicFails() throws Exception {
        // Given: an execution already in SUCCESS state — pausing it fails inside ExecutionService.pause(...).
        var flow = flowRepository.findById(TenantService.MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        var execution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.SUCCESS);
        executionRepository.save(execution);
        var operationId = IdUtils.create();
        var command = Pause.from(execution).withOperationId(operationId);
        var future = subscribeForOperation(operationId);

        // When: handler swallows the business-logic error and reports it via the processed event.
        executionCommandMessageHandler.handle(command);

        // Then: a FAILED processed event is emitted carrying the error message.
        AsyncOperationProcessedEvent event = future.get(5, TimeUnit.SECONDS);
        assertThat(event.operationId()).isEqualTo(operationId);
        assertThat(event.itemId()).isEqualTo(execution.getId());
        assertThat(event.outcome()).isEqualTo(AsyncOperationProcessedEvent.Outcome.FAILED);
        assertThat(event.error()).isNotNull();
    }

    private CompletableFuture<AsyncOperationProcessedEvent> subscribeForOperation(String operationId) {
        CompletableFuture<AsyncOperationProcessedEvent> future = new CompletableFuture<>();
        QueueSubscriber<AsyncOperationProcessedEvent> subscriber = asyncOperationProcessedEventQueue.subscriber().subscribe(either -> {
            if (either.isLeft() && operationId.equals(either.getLeft().operationId())) {
                future.complete(either.getLeft());
            }
        });
        future.whenComplete((e, t) -> subscriber.close());
        return future.orTimeout(10, TimeUnit.SECONDS);
    }
}