package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.plugin.core.flow.AllowFailure;
import io.kestra.plugin.core.flow.Parallel;
import io.kestra.plugin.core.flow.Sequential;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.plugin.core.log.Log;

/**
 * Reproduction probes for kestra-ee#9796: the final task of a flow FAILED but the execution
 * stayed RUNNING and kept occupying its concurrency slots.
 * <p>
 * Each probe drives a "final task fails" shape end-to-end through the real handlers over a
 * flow with a concurrency limit, then asserts the execution reached a terminal state and the
 * slot was released. A probe that fails is the reproducer.
 */
class FinalTaskFailureReproTest {

    private static final Instant ATTEMPT_END = Instant.parse("2026-08-11T10:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void plainFinalTaskFailure() throws Exception {
        // control: single task fails → execution FAILED, slot released by the terminal cycle
        FlowWithSource flow = Flows.of(
            Flows.builder(log("final"))
                .concurrency(queueLimit(2))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);

        ExecutorContext done = harness.processResult(flow, started, Results.failed(workerTask(started, "final"), ATTEMPT_END));

        assertTerminalAndReleased(flow, done, State.Type.FAILED);
    }

    @Test
    void finalTaskFailureWithFlowErrorsBranch() throws Exception {
        // flow-level errors branch: failure → errors task runs → execution FAILED at the end
        FlowWithSource flow = Flows.of(
            Flows.builder(log("final"))
                .concurrency(queueLimit(2))
                .errors(List.of(log("notify")))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);

        ExecutorContext failed = harness.processResult(flow, started, Results.failed(workerTask(started, "final"), ATTEMPT_END));
        ExecutorContext done = completeIfDispatched(flow, failed, "notify", true);

        assertTerminalAndReleased(flow, done, State.Type.FAILED);
    }

    @Test
    void finalTaskFailureWithFailingFlowErrorsBranch() throws Exception {
        // the errors task itself fails
        FlowWithSource flow = Flows.of(
            Flows.builder(log("final"))
                .concurrency(queueLimit(2))
                .errors(List.of(log("notify")))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);

        ExecutorContext failed = harness.processResult(flow, started, Results.failed(workerTask(started, "final"), ATTEMPT_END));
        ExecutorContext done = completeIfDispatched(flow, failed, "notify", false);

        assertTerminalAndReleased(flow, done, State.Type.FAILED);
    }

    @Test
    void finalSequentialChildFailure() throws Exception {
        // the failing final task is the last child of a Sequential parent
        FlowWithSource flow = Flows.of(
            Flows.builder(
                Sequential.builder()
                    .id("seq")
                    .type(Sequential.class.getName())
                    .tasks(List.of(log("first"), log("final")))
                    .build()
            )
                .concurrency(queueLimit(2))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);
        ExecutorContext seqRunning = startFlowable(flow, started, "seq");
        ExecutorContext firstDone = harness.processResult(flow, seqRunning, Results.success(workerTask(seqRunning, "first"), ATTEMPT_END));

        ExecutorContext done = harness.processResult(flow, firstDone, Results.failed(workerTask(firstDone, "final"), ATTEMPT_END.plusSeconds(5)));

        assertTerminalAndReleased(flow, done, State.Type.FAILED);
    }

    @Test
    void lastParallelChildFailureUnderConcurrentLimit() throws Exception {
        // Parallel with concurrent=1: children run one by one, the LAST one fails
        FlowWithSource flow = Flows.of(
            Flows.builder(
                Parallel.builder()
                    .id("par")
                    .type(Parallel.class.getName())
                    .concurrent(Property.ofValue(1))
                    .tasks(List.of(log("child-1"), log("final")))
                    .build()
            )
                .concurrency(queueLimit(2))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);
        ExecutorContext parRunning = startFlowable(flow, started, "par");
        ExecutorContext firstDone = harness.processResult(flow, parRunning, Results.success(workerTask(firstEmitted(parRunning)), ATTEMPT_END));

        ExecutorContext done = harness.processResult(flow, firstDone, Results.failed(workerTask(firstEmitted(firstDone)), ATTEMPT_END.plusSeconds(5)));

        assertTerminalAndReleased(flow, done, State.Type.FAILED);
    }

    @Test
    void finalWorkingDirectoryChildFailure() throws Exception {
        // the failing task is the last child of a WorkingDirectory (the ce74935bcf shape)
        FlowWithSource flow = Flows.of(
            Flows.builder(
                WorkingDirectory.builder()
                    .id("wdir")
                    .type(WorkingDirectory.class.getName())
                    .tasks(List.of(log("first"), log("final")))
                    .build()
            )
                .concurrency(queueLimit(2))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);

        // a WorkingDirectory is sent to the worker whole — its failure comes back as one result
        ExecutorContext done = harness.processResult(flow, started, Results.failed(workerTask(started, "wdir"), ATTEMPT_END));

        assertTerminalAndReleased(flow, done, State.Type.FAILED);
    }

    @Test
    void retryExhaustedThenFlowErrorsBranch() throws Exception {
        // task retry exhausted on the final task + flow errors branch
        FlowWithSource flow = Flows.of(
            Flows.builder(
                Log.builder()
                    .id("final")
                    .type(Log.class.getName())
                    .message("boom")
                    .retry(
                        Constant.builder()
                            .interval(Duration.ofMinutes(1))
                            .maxAttempts(2)
                            .behavior(AbstractRetry.Behavior.RETRY_FAILED_TASK)
                            .build()
                    )
                    .build()
            )
                .concurrency(queueLimit(2))
                .errors(List.of(log("notify")))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);

        // exhausted straight away: the result already carries maxAttempts failed attempts
        ExecutorContext failed = harness.processResult(flow, started, Results.failedAttempts(workerTask(started, "final"), 2, ATTEMPT_END));
        ExecutorContext done = completeIfDispatched(flow, failed, "notify", true);

        assertTerminalAndReleased(flow, done, State.Type.FAILED);
    }

    @Test
    void retryGatedOnFlowableErrorsBranchThatItselfFails() throws Exception {
        // the 2759bae59e gating shape, but the on-error task FAILS: the retry must still
        // un-gate (the branch is terminal) instead of waiting forever
        AllowFailure parent = AllowFailure.builder()
            .id("parent")
            .type(AllowFailure.class.getName())
            .tasks(List.of(log("final")))
            .errors(List.of(log("on-error")))
            .retry(
                Constant.builder()
                    .interval(Duration.ofMinutes(1))
                    .maxAttempts(3)
                    .behavior(AbstractRetry.Behavior.RETRY_FAILED_TASK)
                    .build()
            )
            .build();
        FlowWithSource flow = Flows.of(
            Flows.builder(parent)
                .concurrency(queueLimit(2))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);
        ExecutorContext parentRunning = startFlowable(flow, started, "parent");
        ExecutorContext childFailed = harness.processResult(flow, parentRunning, Results.failed(workerTask(parentRunning, "final"), ATTEMPT_END));

        // the errors branch fails too
        ExecutorContext done = completeIfDispatched(flow, childFailed, "on-error", false);

        // the execution must not stay wedged RUNNING: either the retry is armed (RETRYING with
        // a delay) or the failure propagated — never RUNNING with nothing in flight
        State.Type state = done.getExecution().getState().getCurrent();
        boolean somethingInFlight = !done.getWorkerTasks().isEmpty() || !done.getExecutionDelays().isEmpty();
        Assertions.assertThat(state.isTerminated() || State.Type.RETRYING.equals(state) || somethingInFlight)
            .as("execution wedged: state %s with nothing in flight (ee#9796 shape) — from: %s", state, done.getFrom())
            .isTrue();
    }

    @Test
    void redeliveredResultOfTheFailedFinalTaskDoesNotHealTheExecution() {
        // Given: a single-slot flow whose only (final) task is running
        FlowWithSource flow = Flows.of(
            Flows.builder(log("final"))
                .concurrency(queueLimit(1))
                .build()
        );
        harness.registerFlow(flow);
        ExecutorContext started = startExecution(flow);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);

        // When: the final task's FAILED result is joined by the real handler
        WorkerTaskResult failedResult = Results.failed(workerTask(started, "final"), ATTEMPT_END);
        Optional<ExecutorContext> joined = harness.workerTaskResultMessageHandler().handle(failedResult);

        // Then: the join persists the FAILED taskrun but the execution is STILL RUNNING — the
        // terminal transition belongs to the follow-up UPDATED event cycle, which DefaultExecutor
        // emits only AFTER the join transaction committed (not atomically with it)
        Assertions.assertThat(joined).isPresent();
        Execution afterJoin = harness.executionStateStore().findById(started.getExecution().getId());
        Assertions.assertThat(afterJoin.findTaskRunsByTaskId("final").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.FAILED);
        Assertions.assertThat(afterJoin.getState().getCurrent()).isEqualTo(State.Type.RUNNING);

        // ee#9796 crash window: the executor dies between the join commit and the UPDATED
        // emission — the event is lost. At-least-once delivery redelivers the WorkerTaskResult:
        // this is the execution's ONLY chance to heal.

        // When: the same result is redelivered
        Optional<ExecutorContext> redelivered = harness.workerTaskResultMessageHandler().handle(failedResult);

        // Then: the redelivery dedup returns empty — DefaultExecutor calls toExecution only on a
        // present context, so NO event is ever emitted again: the execution is wedged RUNNING
        // forever with its final task FAILED, and its concurrency slot is held forever
        Assertions.assertThat(redelivered).isEmpty();
        Execution stored = harness.executionStateStore().findById(started.getExecution().getId());
        Assertions.assertThat(stored.getState().getCurrent())
            .as("the execution can never leave RUNNING: no event, no delay, nothing in flight targets it anymore")
            .isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(stored.findTaskRunsByTaskId("final").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.FAILED);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow))
            .as("the slot stays claimed, so with limit 1 every future execution queues/cancels forever")
            .isEqualTo(1);
    }

    // --- helpers

    private void assertTerminalAndReleased(FlowWithSource flow, ExecutorContext done, State.Type expected) {
        Assertions.assertThat(done.getExecution().getState().getCurrent())
            .as("execution should be terminal (taskruns: %s)", describeTaskRuns(done))
            .isEqualTo(expected);

        // the terminal cycle releases the slot exactly like DefaultExecutor's terminal block
        Optional<Execution> popped = harness.concurrencySlotReleaseProcessor().release(done, true);
        Assertions.assertThat(popped).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow))
            .as("concurrency slot must be released after the terminal cycle")
            .isEqualTo(0);
    }

    /** Completes (or fails) the given task if a worker task was dispatched for it — probes assert the outcome either way. */
    private ExecutorContext completeIfDispatched(FlowWithSource flow, ExecutorContext context, String taskId, boolean success) throws Exception {
        Optional<ExecutorContext.ExecutorWorkerTask> dispatched = context.getWorkerTasks().stream()
            .filter(workerTask -> taskId.equals(workerTask.workerTask().getTaskRun().getTaskId()))
            .findFirst();
        if (dispatched.isEmpty()) {
            return context;
        }
        return harness.processResult(
            flow,
            context,
            success
                ? Results.success(dispatched.get(), ATTEMPT_END.plusSeconds(30))
                : Results.failed(dispatched.get(), ATTEMPT_END.plusSeconds(30))
        );
    }

    private ExecutorContext startExecution(FlowWithSource flow) {
        Execution execution = Executions.created(flow);
        harness.executionStateStore().save(execution);
        return harness.executionEventMessageHandler()
            .handle(new ExecutionEvent(execution, ExecutionEventType.CREATED))
            .orElseThrow();
    }

    private ExecutorContext startFlowable(FlowWithSource flow, ExecutorContext previous, String taskId) throws Exception {
        // with same-cycle dispatch the handler may already have flipped the pseudo worker task
        // RUNNING; the children are then resolved on the next event cycle (production re-emits
        // the updated execution)
        TaskRun inExecution = previous.getExecution().getTaskRunList().stream()
            .filter(taskRun -> taskId.equals(taskRun.getTaskId()))
            .findFirst()
            .orElseThrow();
        if (!State.Type.CREATED.equals(inExecution.getState().getCurrent())) {
            return harness.process(flow, previous.getExecution());
        }
        TaskRun running = workerTask(previous, taskId).workerTask().getTaskRun()
            .withAttempts(List.of(TaskRunAttempt.builder().state(new State().withState(State.Type.RUNNING)).build()))
            .withState(State.Type.RUNNING);
        return harness.processResult(flow, previous, new WorkerTaskResult(running));
    }

    private static ExecutorContext.ExecutorWorkerTask workerTask(ExecutorContext context, String taskId) {
        return context.getWorkerTasks().stream()
            .filter(workerTask -> taskId.equals(workerTask.workerTask().getTaskRun().getTaskId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no worker task emitted for <" + taskId + "> (emitted: " + emittedTaskIds(context) + ")"));
    }

    private static ExecutorContext.ExecutorWorkerTask workerTask(ExecutorContext.ExecutorWorkerTask workerTask) {
        return workerTask;
    }

    private static ExecutorContext.ExecutorWorkerTask firstEmitted(ExecutorContext context) {
        return context.getWorkerTasks().stream()
            .findFirst()
            .orElseThrow(() -> new AssertionError("no worker task emitted"));
    }

    private static List<String> emittedTaskIds(ExecutorContext context) {
        return context.getWorkerTasks().stream()
            .map(workerTask -> workerTask.workerTask().getTaskRun().getTaskId())
            .toList();
    }

    private static List<String> describeTaskRuns(ExecutorContext context) {
        return context.getExecution().getTaskRunList() == null ? List.of()
            : context.getExecution().getTaskRunList().stream()
                .map(taskRun -> taskRun.getTaskId() + "=" + taskRun.getState().getCurrent())
                .toList();
    }

    private static Concurrency queueLimit(int limit) {
        return Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(limit).build();
    }

    private static Log log(String id) {
        return Log.builder().id(id).type(Log.class.getName()).message("hello").build();
    }
}
