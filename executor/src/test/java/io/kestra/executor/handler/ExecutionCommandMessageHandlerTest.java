package io.kestra.executor.handler;

import io.kestra.core.debug.Breakpoint;
import io.kestra.core.events.EventId;
import io.kestra.core.executor.command.*;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.ExecutorContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class ExecutionCommandMessageHandlerTest {
    @Inject
    private ExecutionCommandMessageHandler executionCommandMessageHandler;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

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
    @ExecuteFlow("flows/valids/minimal.yaml")
    void replay(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        var command = Replay.from(execution, null, null, Optional.empty());

        Optional<ExecutorContext> handle = executionCommandMessageHandler.handle(command);

        assertThat(handle).isPresent();
        assertThat(handle.get().getExecution().getId()).isNotEqualTo(execution.getId());
        assertThat(handle.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.CREATED);
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
            .withTaskRunList(List.of(TaskRun.builder()
                .id("taskrun")
                .state(new State(State.Type.BREAKPOINT))
                .build())
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
            .withTaskRunList(List.of(
                TaskRun.builder()
                    .id(IdUtils.create())
                    .taskId("pause")
                    .executionId("execution")
                    .namespace(flow.getNamespace())
                    .tenantId(flow.getTenantId())
                    .flowId(flow.getId())
                    .state(new State().withState(State.Type.PAUSED))
                    .build()
            ))
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
}