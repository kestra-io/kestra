package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTask;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import reactor.core.publisher.Flux;

/**
 * An SLA that fails an execution fails its last task run still waiting for a worker. The afterExecution task runs
 * are forced, so the worker still runs that job when it comes back: its late result must not override the FAILED
 * state the SLA set, or the errors branch started for it never resolves its flowable (kestra-io/kestra#21337).
 */
class SLAFailedTaskRunLateResultTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_THE_SLA = Instant.parse("2100-01-01T00:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final Map<String, WorkerTask> heldJobs = new HashMap<>();

    @BeforeEach
    void noChildExecutionsToCascadeTheKillTo() {
        doReturn(Flux.empty()).when(harness.executionService()).killSubflowExecutions(any(), any());
        doReturn(List.of()).when(harness.executionService()).killLoopSubExecutions(any(), any());
    }

    @Test
    void aLateWorkerResultDoesNotOverrideTheTaskRunTheSLAFailed() {
        FlowWithSource flow = Flows.yaml("""
            id: sla-after-execution
            namespace: io.kestra.tests
            sla:
              - id: maxDuration
                type: MAX_DURATION
                duration: PT60S
                behavior: FAIL
            tasks:
              - id: main
                type: io.kestra.plugin.core.log.Log
                message: main
            afterExecution:
              - id: finishExecution
                type: io.kestra.plugin.core.flow.Sequential
                tasks:
                  - id: endExecution
                    type: io.kestra.plugin.core.log.Log
                    message: end
                errors:
                  - id: sendErrorMessage
                    type: io.kestra.plugin.core.log.Log
                    message: error
            """);
        harness.registerFlow(flow);
        // MaxDurationSLA measures against the wall clock, so the execution starts an hour ago
        Execution execution = Executions.created(flow).toBuilder()
            .state(new State(State.Type.CREATED, List.of(new State.History(State.Type.CREATED, Instant.now().minus(Duration.ofHours(1))))))
            .build();

        // main fails, then the worker goes down: endExecution stays SUBMITTED
        harness.clock().set(T0);
        harness.run(execution, mainFailsThenTheWorkerIsDown());
        assertThat(taskRun(execution, "endExecution").getState().getCurrent()).as("before the SLA").isEqualTo(State.Type.SUBMITTED);

        // the SLA fails endExecution, which starts the errors branch of finishExecution
        harness.tickSLAMonitors(AFTER_THE_SLA);
        harness.run(List.of(), mainFailsThenTheWorkerIsDown());
        assertThat(taskRun(execution, "endExecution").getState().getCurrent()).as("after the SLA").isEqualTo(State.Type.FAILED);
        assertThat(heldJobs).as("the errors branch waits for the worker too").containsKey("sendErrorMessage");

        // the worker comes back, runs the old endExecution job, then the error handler
        harness.run(List.of(Results.success(heldJobs.get("endExecution"), AFTER_THE_SLA)), mainFailsThenTheWorkerIsDown());
        harness.run(List.of(Results.failed(heldJobs.get("sendErrorMessage"), AFTER_THE_SLA)), mainFailsThenTheWorkerIsDown());

        assertThat(taskRun(execution, "endExecution").getState().getCurrent()).as("the SLA decision stands").isEqualTo(State.Type.FAILED);
        assertThat(taskRun(execution, "finishExecution").getState().getCurrent()).as("the afterExecution flowable ends").isEqualTo(State.Type.FAILED);
        assertThat(harness).hasExecutionInState(execution, State.Type.FAILED);
        assertThat(harness.executionStateStore().findByIdWithoutAcl(execution.getId()).getTaskRunList())
            .as("nothing is left running, so a parent waiting on this execution is notified")
            .allMatch(taskRun -> taskRun.getState().isTerminated());
    }

    private ScriptedWorker mainFailsThenTheWorkerIsDown() {
        return task ->
        {
            if ("main".equals(task.getTaskRun().getTaskId())) {
                return Results.failed(task, T0);
            }
            heldJobs.put(task.getTaskRun().getTaskId(), task);
            return null;
        };
    }

    private TaskRun taskRun(Execution execution, String taskId) {
        return harness.executionStateStore().findByIdWithoutAcl(execution.getId()).getTaskRunList().stream()
            .filter(taskRun -> taskId.equals(taskRun.getTaskId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no task run for " + taskId));
    }
}
