package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Replay;
import io.kestra.core.executor.command.Restart;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.plugin.core.flow.Approval;
import io.kestra.plugin.core.flow.OnChildFailureInterface.OnChildFailure;
import io.kestra.plugin.core.flow.Parallel;
import io.kestra.plugin.core.flow.PausableTask;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.flow.Sequential;

import static org.assertj.core.api.Assertions.assertThat;

class PauseLifecycleTest {
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Map<String, Object> INPUTS = Map.of("reason", "approved");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @ParameterizedTest
    @EnumSource(value = State.Type.class, names = {"RUNNING", "WARNING", "FAILED"})
    void shouldResumeLeafPauseWithoutWaitingForItsHook(State.Type target) throws Exception {
        Pause pause = Pause.builder().id("pause").type(Pause.class.getName()).onPause(Flows.log("notify")).build();
        FlowWithSource flow = Flows.of(Sequential.builder()
            .id("sequence")
            .type(Sequential.class.getName())
            .tasks(List.of(pause, Flows.log("next")))
            .build());
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        ScriptedWorker worker = task -> "notify".equals(task.getTaskRun().getTaskId())
            ? new WorkerTaskResult(task.getTaskRun().run())
            : Results.success(task, T0);

        harness.run(created, worker);
        Execution paused = harness.executionStateStore().findByIdWithoutAcl(created.getId());
        assertThat(paused.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
        assertThat(paused.findTaskRunsByTaskId("notify")).hasSize(1);

        Execution resumed = harness.executionService().resume(paused, flow, target, INPUTS, PausableTask.Resumed.now(target));
        State.Type terminalState = target == State.Type.RUNNING ? State.Type.SUCCESS : target;
        assertThat(resumed.findTaskRunsByTaskId("pause").getFirst().getState().getCurrent()).isEqualTo(terminalState);
        harness.run(resumed, worker);
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(created.getId());
        TaskRun pauseTaskRun = ended.findTaskRunsByTaskId("pause").getFirst();

        assertThat(ended.getState().getCurrent()).isEqualTo(terminalState);
        assertThat(pauseTaskRun.getState().getHistories()).extracting(State.History::getState)
            .containsExactly(State.Type.CREATED, State.Type.RUNNING, State.Type.PAUSED, terminalState);
        assertThat(pauseTaskRun.getAttempts()).singleElement()
            .satisfies(attempt -> assertThat(attempt.getState().getCurrent()).isEqualTo(terminalState));
        assertThat(ended.findTaskRunsByTaskId("notify")).singleElement().satisfies(hook -> {
            assertThat(hook.getParentTaskRunId()).isEqualTo(pauseTaskRun.getId());
            assertThat(hook.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        });
        assertThat(harness.workerJobEventQueue().emittedMessages())
            .allSatisfy(event -> assertThat(event.job()).isInstanceOfSatisfying(WorkerTask.class,
                task -> assertThat(task.getTaskRun().getTaskId()).isNotEqualTo("pause")));
        assertThat(harness.runContextFactory().of(flow, pause, ended, pauseTaskRun).currentOutput()).containsEntry("inputs", INPUTS);
    }

    @Test
    void shouldNotDispatchHookWhenPauseIsSkipped() {
        Pause pause = Pause.builder().id("pause").type(Pause.class.getName()).runIf("false").onPause(Flows.log("notify")).build();
        FlowWithSource flow = Flows.of(pause, Flows.log("next"));
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);

        harness.run(created, ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(created.getId());

        assertThat(ended.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(ended.findTaskRunsByTaskId("pause").getFirst().getState().getCurrent()).isEqualTo(State.Type.SKIPPED);
        assertThat(ended.findTaskRunsByTaskId("notify")).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = State.Type.class, names = {"RUNNING", "PAUSED"})
    void shouldKillExecutorManagedPauseWithoutWaitingForAWorker(State.Type state) {
        Pause pause = Pause.builder().id("pause").type(Pause.class.getName()).onPause(Flows.log("notify")).build();
        FlowWithSource flow = Flows.of(pause);
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        TaskRun taskRun = TaskRun.of(created, ResolvedTask.of(pause)).run();
        if (state == State.Type.PAUSED) {
            taskRun = taskRun.withState(state);
        }
        Execution saved = created.withTaskRunList(List.of(taskRun)).withState(state);
        harness.executionStateStore().save(saved);
        ExecutionKilledExecution kill = ExecutionKilledExecution.builder()
            .executionId(saved.getId())
            .tenantId(saved.getTenantId())
            .state(ExecutionKilled.State.REQUESTED)
            .isOnKillCascade(false)
            .build();

        harness.run(List.of(kill), ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(saved.getId());

        assertThat(ended.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(ended.findTaskRunsByTaskId("pause")).singleElement().satisfies(killed -> {
            assertThat(killed.getState().getCurrent()).isEqualTo(State.Type.KILLED);
            assertThat(killed.lastAttempt().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        });
        assertThat(ended.findTaskRunsByTaskId("notify")).isEmpty();
        assertThat(harness.workerJobEventQueue().emittedMessages()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = OnChildFailure.class, names = {"FAIL", "CANCEL"})
    void shouldInterruptPauseWithoutSendingItToAWorkerWhenItsSiblingFails(OnChildFailure onChildFailure) throws Exception {
        FlowWithSource flow = Flows.of(Parallel.builder()
            .id("parallel").type(Parallel.class.getName())
            .onChildFailure(Property.ofValue(onChildFailure))
            .tasks(List.of(Pause.builder().id("pause").type(Pause.class.getName()).build(), Flows.log("failing")))
            .build());
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        TaskRun parent = TaskRun.of(created, ResolvedTask.of(flow.getTasks().getFirst())).run();
        TaskRun pause = childTaskRun(flow, created, parent, "pause").run();
        TaskRun failed = childTaskRun(flow, created, parent, "failing").run().withStateAndAttempt(State.Type.FAILED);
        Execution saved = created.withTaskRunList(List.of(parent, pause, failed)).withState(State.Type.RUNNING);

        harness.run(saved, ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(saved.getId());

        assertThat(ended.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(ended.findTaskRunsByTaskId("pause")).singleElement().satisfies(interrupted -> {
            assertThat(interrupted.getState().getCurrent()).isEqualTo(onChildFailure.toTaskRunState());
            assertThat(interrupted.lastAttempt().getState().getCurrent()).isEqualTo(onChildFailure.toTaskRunState());
        });
        assertThat(harness.kills()).isEmpty();
        assertThat(harness.workerJobEventQueue().emittedMessages()).isEmpty();
    }

    @Test
    void shouldTerminatePauseWhenItsFlowableParentHasTerminated() throws Exception {
        Pause pause = Pause.builder().id("pause").type(Pause.class.getName()).build();
        FlowWithSource flow = Flows.of(Sequential.builder()
            .id("sequence")
            .type(Sequential.class.getName())
            .tasks(List.of(pause))
            .build());
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        TaskRun parent = TaskRun.of(created, ResolvedTask.of(flow.getTasks().getFirst())).run().withStateAndAttempt(State.Type.FAILED);
        TaskRun paused = childTaskRun(flow, created, parent, "pause").run().withState(State.Type.PAUSED);
        Execution saved = created.withTaskRunList(List.of(parent, paused)).withState(State.Type.RUNNING);

        harness.run(saved, ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(saved.getId());

        assertThat(ended.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(ended.findTaskRunsByTaskId("pause")).singleElement().satisfies(terminated -> {
            assertThat(terminated.getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(terminated.lastAttempt().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        });
    }

    @ParameterizedTest
    @EnumSource(value = Approval.Behavior.class, names = {"SUCCEED", "CANCEL"})
    void shouldTerminateSiblingPauseWhenApprovalEndsTheExecution(Approval.Behavior behavior) throws Exception {
        FlowWithSource flow = Flows.of(Parallel.builder()
            .id("parallel").type(Parallel.class.getName())
            .tasks(List.of(
                Approval.builder().id("approval").type(Approval.class.getName()).approveBehavior(Property.ofValue(behavior)).build(),
                Pause.builder().id("pause").type(Pause.class.getName()).build()
            ))
            .build());
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        TaskRun parent = TaskRun.of(created, ResolvedTask.of(flow.getTasks().getFirst())).run();
        TaskRun approval = childTaskRun(flow, created, parent, "approval").run().withState(State.Type.PAUSED);
        TaskRun pause = childTaskRun(flow, created, parent, "pause").run();
        Execution saved = created.withTaskRunList(List.of(parent, approval, pause)).withState(State.Type.PAUSED);
        harness.taskOutputRepository().save(new TaskOutput(approval.getId(), approval.getTenantId(), approval.getExecutionId(),
            JacksonMapper.ofIon().writeValueAsBytes(Map.of("url", "https://kestra.example/approval")), null));

        Execution decided = harness.executionService().decide(saved, flow, approval.getId(),
            new PausableTask.Decision(PausableTask.Decision.Type.APPROVED, null), Map.of());
        harness.run(decided, ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(saved.getId());
        State.Type terminalState = behavior == Approval.Behavior.SUCCEED ? State.Type.SUCCESS : State.Type.CANCELLED;

        assertThat(ended.getState().getCurrent()).isEqualTo(terminalState);
        assertThat(ended.findTaskRunsByTaskId("pause")).singleElement().satisfies(terminated -> {
            assertThat(terminated.getState().getCurrent()).isEqualTo(terminalState);
            assertThat(terminated.lastAttempt().getState().getCurrent()).isEqualTo(terminalState);
        });
        assertThat(harness.workerJobEventQueue().emittedMessages()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = State.Type.class, names = {"RUNNING", "WARNING", "FAILED", "CANCELLED"})
    void shouldCompleteLegacyPauseWithNoHookWhenResumingWithScopedErrors(State.Type target) throws Exception {
        FlowWithSource flow = Flows.yaml("""
            id: legacy-pause-errors
            namespace: io.kestra.tests
            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                errors:
                  - id: error
                    type: io.kestra.plugin.core.log.Log
                    message: Failed
            """);
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, ScriptedWorker.succeeding(T0));
        Execution paused = harness.executionStateStore().findByIdWithoutAcl(created.getId());

        Execution resumed = harness.executionService().resume(paused, flow, target, INPUTS, PausableTask.Resumed.now(target));
        harness.run(resumed, ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(created.getId());
        State.Type terminalState = target == State.Type.RUNNING ? State.Type.SUCCESS : target;

        assertThat(ended.getState().getCurrent()).isEqualTo(terminalState);
        assertThat(ended.findTaskRunsByTaskId("pause")).singleElement()
            .satisfies(taskRun -> assertThat(taskRun.getState().getCurrent()).isEqualTo(terminalState));
        assertThat(ended.findTaskRunsByTaskId("error")).hasSize(target == State.Type.FAILED ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRunLegacyHookOnceWhenResumingWithScopedFinally(boolean preUpgrade) throws Exception {
        FlowWithSource flow = legacyFlow();
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        Execution paused;
        if (preUpgrade) {
            TaskRun pause = TaskRun.of(created, ResolvedTask.of(flow.getTasks().getFirst())).run().withState(State.Type.PAUSED);
            TaskRun notify = TaskRun.of(created, ResolvedTask.of(flow.findTaskByTaskId("notify"))).run().withStateAndAttempt(State.Type.SUCCESS);
            paused = created.withTaskRunList(List.of(pause, notify)).withState(State.Type.PAUSED);
        } else {
            harness.run(created, ScriptedWorker.succeeding(T0));
            paused = harness.executionStateStore().findByIdWithoutAcl(created.getId());
        }

        Execution resumed = harness.executionService().resume(paused, flow, State.Type.RUNNING, INPUTS, PausableTask.Resumed.now());
        harness.run(resumed, ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(created.getId());

        assertThat(ended.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(ended.findTaskRunsByTaskId("notify")).containsExactlyElementsOf(paused.findTaskRunsByTaskId("notify"));
        assertThat(ended.findTaskRunsByTaskId("cleanup")).singleElement()
            .satisfies(taskRun -> assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS));
        assertThat(ended.findTaskRunsByTaskId("error")).isEmpty();
    }

    @Test
    void shouldFinishSavedLegacyPauseWhenAlreadyResumedWithPendingErrorTasks() throws Exception {
        FlowWithSource flow = legacyFlow();
        harness.registerFlow(flow);
        Execution paused = savedPausedExecution(flow, State.Type.SUCCESS);
        Execution resumed = harness.executionService().resume(paused, flow, State.Type.FAILED, INPUTS, PausableTask.Resumed.now(State.Type.FAILED));
        TaskRun pause = resumed.findTaskRunsByTaskId("pause").getFirst();
        TaskRun error = childTaskRun(flow, resumed, pause, "error").run();
        Execution saved = resumed.withTaskRunList(List.of(pause, resumed.findTaskRunsByTaskId("notify").getFirst(), error)).withState(State.Type.RUNNING);
        harness.executionStateStore().save(saved);

        harness.run(saved, ScriptedWorker.succeeding(T0));
        Execution waiting = harness.executionStateStore().findByIdWithoutAcl(saved.getId());
        assertThat(waiting.findTaskRunsByTaskId("pause").getFirst().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(waiting.findTaskRunsByTaskId("error")).singleElement().satisfies(taskRun -> assertThat(taskRun.getId()).isEqualTo(error.getId()));

        harness.run(List.of(new WorkerTaskResult(error.withStateAndAttempt(State.Type.SUCCESS))), ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(saved.getId());

        assertThat(ended.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(ended.findTaskRunsByTaskId("pause").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(ended.findTaskRunsByTaskId("error")).hasSize(1);
        assertThat(ended.findTaskRunsByTaskId("cleanup")).hasSize(1);
        assertThat(harness.runContextFactory().of(flow, flow.getTasks().getFirst(), ended, ended.findTaskRunsByTaskId("pause").getFirst()).currentOutput())
            .containsEntry("inputs", INPUTS);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCompleteSavedLegacyChildRunsWhenRestartingOrReplaying(boolean replay) throws Exception {
        FlowWithSource flow = legacyFlow();
        harness.registerFlow(flow);
        Execution paused = savedPausedExecution(flow, State.Type.FAILED);
        Execution resumed = harness.executionService().resume(paused, flow, State.Type.RUNNING, INPUTS, PausableTask.Resumed.now());
        TaskRun pause = resumed.findTaskRunsByTaskId("pause").getFirst().withStateAndAttempt(State.Type.FAILED);
        TaskRun notify = resumed.findTaskRunsByTaskId("notify").getFirst();
        TaskRun cleanup = childTaskRun(flow, resumed, pause, "cleanup").run().withStateAndAttempt(State.Type.SUCCESS);
        Execution saved = resumed.withTaskRunList(List.of(pause, notify, cleanup)).withState(State.Type.FAILED);
        harness.executionStateStore().save(saved);
        String executionId = replay ? IdUtils.create() : saved.getId();
        ExecutionCommand command = replay ? Replay.from(saved, executionId, notify.getId(), null, null) : Restart.from(saved, null);

        harness.run(List.of(command), ScriptedWorker.succeeding(T0));
        Execution ended = harness.executionStateStore().findByIdWithoutAcl(executionId);

        assertThat(ended.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(ended.findTaskRunsByTaskId("pause")).singleElement().satisfies(taskRun -> {
            assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(taskRun.getState().getHistories()).filteredOn(history -> history.getState() == State.Type.PAUSED).hasSize(1);
        });
        assertThat(ended.findTaskRunsByTaskId("notify")).hasSize(1);
        assertThat(ended.findTaskRunsByTaskId("cleanup")).hasSize(1);
    }

    private Execution savedPausedExecution(FlowWithSource flow, State.Type hookState) throws InternalException {
        Execution created = Executions.created(flow);
        TaskRun pause = TaskRun.of(created, ResolvedTask.of(flow.getTasks().getFirst())).run().withState(State.Type.PAUSED);
        TaskRun notify = childTaskRun(flow, created, pause, "notify").run().withStateAndAttempt(hookState);
        return created.withTaskRunList(List.of(pause, notify)).withState(State.Type.PAUSED);
    }

    private TaskRun childTaskRun(FlowWithSource flow, Execution execution, TaskRun parent, String taskId) throws InternalException {
        return TaskRun.of(execution, ResolvedTask.builder().task(flow.findTaskByTaskId(taskId)).parentId(parent.getId()).build());
    }

    private FlowWithSource legacyFlow() {
        return Flows.yaml("""
            id: legacy-pause
            namespace: io.kestra.tests
            tasks:
              - id: pause
                type: io.kestra.plugin.core.flow.Pause
                onPause:
                  id: notify
                  type: io.kestra.plugin.core.log.Log
                  message: Waiting for approval
                errors:
                  - id: error
                    type: io.kestra.plugin.core.log.Log
                    message: Failed
                finally:
                  - id: cleanup
                    type: io.kestra.plugin.core.log.Log
                    message: Finished
            """);
    }
}
