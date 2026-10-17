package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.executor.command.Replay;
import io.kestra.core.executor.command.Restart;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;

import static org.assertj.core.api.Assertions.assertThat;

class DagReplayTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void shouldRedispatchRunningSiblingAndCompleteWhenReplayingDagTask() {
        // Given
        Execution source = failedDagWithRunningSibling();

        // When: the failed task is replayed, one delivery of the Replay command
        TaskRun failed = source.findTaskRunsByTaskId("a").getFirst();
        String replayId = IdUtils.create();
        List<Trace.Emission> emitted = harness.step(Replay.from(source, replayId, failed.getId(), null, null));

        harness.run(List.of(), ScriptedWorker.succeeding(T0));

        // Then
        assertThat(dispatchedTaskIds(emitted)).as("the replay sends the running sibling to a worker again instead of inheriting it as RUNNING").containsExactlyInAnyOrder("a", "b");
        assertThat(harness.executionStateStore().findByIdWithoutAcl(replayId).getState().getCurrent()).as("the replay ran to the end").isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldRedispatchRunningSiblingAndCompleteWhenRestartingDag() {
        // Given
        Execution source = failedDagWithRunningSibling();

        // When: the execution is restarted in place, one delivery of the Restart command
        List<Trace.Emission> emitted = harness.step(Restart.from(source, null));

        Trace trace = harness.run(List.of(), ScriptedWorker.succeeding(T0));

        // Then
        List<String> dispatched = new ArrayList<>(dispatchedTaskIds(emitted));
        trace.steps().forEach(step -> dispatched.addAll(dispatchedTaskIds(step.emitted())));
        assertThat(dispatched).as("the restart sends the running sibling to a worker again instead of carrying it over as RUNNING; the whole run also dispatches the downstream task").contains("a", "b");
        assertThat(harness.executionStateStore().findByIdWithoutAcl(source.getId()).getState().getCurrent()).as("the restart ran to the end").isEqualTo(State.Type.SUCCESS);
    }

    /** "a" failed while its independent sibling "b" was still running, and the execution was then terminated. */
    private Execution failedDagWithRunningSibling() {
        FlowWithSource flow = Flows.yaml("""
            id: dag-replay
            namespace: io.kestra.tests
            tasks:
              - id: dag
                type: io.kestra.plugin.core.flow.Dag
                tasks:
                  - task:
                      id: a
                      type: io.kestra.plugin.core.log.Log
                      message: hello
                  - task:
                      id: b
                      type: io.kestra.plugin.core.log.Log
                      message: hello
                  - task:
                      id: c
                      type: io.kestra.plugin.core.log.Log
                      message: hello
                    dependsOn:
                      - a
                      - b
            """);
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, task ->
        {
            if ("a".equals(task.getTaskRun().getTaskId())) {
                return Results.failed(task, T0);
            }
            return new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
        });
        // the executor-error path terminates the execution without touching "b"
        Execution running = harness.executionStateStore().findByIdWithoutAcl(created.getId());
        List<TaskRun> taskRuns = running.getTaskRunList().stream()
            .map(taskRun -> "dag".equals(taskRun.getTaskId()) ? taskRun.withState(State.Type.FAILED) : taskRun)
            .toList();
        Execution source = running.withTaskRunList(taskRuns).withState(State.Type.FAILED);
        harness.executionStateStore().save(source);
        assertThat(source.findTaskRunsByTaskId("b").getFirst().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        return source;
    }

    private static List<String> dispatchedTaskIds(List<Trace.Emission> emitted) {
        return emitted.stream()
            .filter(emission -> Trace.WORKER_JOB_EVENT.equals(emission.queue()))
            .map(emission -> ((WorkerTask) emission.as(WorkerJobEvent.class).job()).getTaskRun().getTaskId())
            .toList();
    }
}
