package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;
import io.kestra.executor.testkit.Trace;
import io.kestra.plugin.core.flow.WorkingDirectory;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The children of a WorkingDirectory only run inside it, on its worker, so retrying a failed child means running the
 * whole WorkingDirectory again: the attempts a child's retry policy limits are the WorkingDirectory's.
 */
class WorkingDirectoryRetryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant AFTER_ANY_RETRY_DELAY = Instant.parse("2100-01-01T00:00:00Z");

    private static final String CHILD_RETRY_FLOW = """
        id: working-directory-child-retry
        namespace: io.kestra.tests
        concurrency:
          limit: 1
        tasks:
          - id: working-directory
            type: io.kestra.plugin.core.flow.WorkingDirectory
            tasks:
              - id: generate
                type: io.kestra.plugin.core.log.Log
                message: generate
                retry:
                  type: constant
                  interval: PT1S
                  maxAttempts: 2
              - id: ingest
                type: io.kestra.plugin.core.log.Log
                message: ingest
        """;
    private static final String FLOW_ERRORS = """
        errors:
          - id: handler
            type: io.kestra.plugin.core.log.Log
            message: handler
        """;
    private static final String FLOW_FINALLY = """
        finally:
          - id: cleanup
            type: io.kestra.plugin.core.log.Log
            message: cleanup
        """;

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<WorkerTask> dispatched = new ArrayList<>();
    private final FlowWithSource flow = Flows.yaml("""
        id: working-directory-retry
        namespace: io.kestra.tests
        concurrency:
          limit: 1
        retry:
          type: constant
          interval: PT1S
          maxAttempts: 2
        tasks:
          - id: working-directory
            type: io.kestra.plugin.core.flow.WorkingDirectory
            tasks:
              - id: generate
                type: io.kestra.plugin.core.log.Log
                message: generate
              - id: ingest
                type: io.kestra.plugin.core.log.Log
                message: ingest
        """);

    @Test
    void shouldRunTheWorkingDirectoryAgainWhenAChildIsRetried() {
        // Given: the first child fails, so the worker stops the WorkingDirectory there
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, recordingWorker(State.Type.FAILED));

        // When: the retry fires and the second run of the WorkingDirectory succeeds
        harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
        harness.run(List.of(), recordingWorker(State.Type.SUCCESS));
        harness.run(List.of(Results.success(childOf(dispatched.getLast(), "ingest"), T0)), recordingWorker(State.Type.SUCCESS));

        // Then
        assertThat(dispatchedTaskIds()).containsExactly("working-directory", "working-directory");
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS).hasRunning(flow, 0);
    }

    @Test
    void shouldFailWhenTheWorkingDirectoryRetriesAreExhausted() {
        // Given
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, recordingWorker(State.Type.FAILED));

        // When: every run of the WorkingDirectory fails on its first child
        for (int i = 0; i < 3; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), recordingWorker(State.Type.FAILED));
        }

        // Then
        assertThat(dispatchedTaskIds()).containsExactly("working-directory", "working-directory");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED).hasRunning(flow, 0);
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|', value = {
            "none              | working-directory working-directory",
            "errors            | working-directory working-directory handler",
            "finally           | working-directory working-directory cleanup",
            "errors and finally| working-directory working-directory handler cleanup"
        }
    )
    void shouldFailWhenAChildDeclaredRetriesAreExhausted(String flowHandlers, String expectedDispatches) {
        // Given: the flow's handlers, if any, follow the WorkingDirectory failure
        FlowWithSource childRetryFlow = Flows.yaml(
            CHILD_RETRY_FLOW
                + (flowHandlers.contains("errors") ? FLOW_ERRORS : "")
                + (flowHandlers.contains("finally") ? FLOW_FINALLY : "")
        );
        Execution created = runUntilQuiet(childRetryFlow, recordingWorker(State.Type.FAILED));

        // Then: maxAttempts 2 is two runs of the WorkingDirectory, and the child after the failing one never runs
        assertThat(dispatchedTaskIds()).containsExactly(expectedDispatches.split(" "));
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED).hasRunning(childRetryFlow, 0).hasPendingDelays(0);

        Execution persisted = harness.executionStateStore().findByIdWithoutAcl(created.getId());
        assertThat(persisted.findTaskRunsByTaskId("working-directory")).singleElement().satisfies(workingDirectory ->
        {
            assertThat(workingDirectory.getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(workingDirectory.getAttempts()).as("one attempt per run of the WorkingDirectory").hasSize(2);
            assertThat(workingDirectory.lastAttempt().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        });
        assertThat(persisted.findTaskRunsByTaskId("generate")).as("a rerun replaces the previous child task runs").singleElement().satisfies(generate ->
        {
            assertThat(generate.getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(generate.getAttempts()).hasSize(1);
        });
        assertThat(persisted.findTaskRunsByTaskId("ingest")).isEmpty();
    }

    @Test
    void shouldRunTheLocalHandlersAndWaitForThemBeforeFailingTheWorkingDirectory() {
        // Given: the WorkingDirectory has its own errors and finally handlers
        FlowWithSource childRetryFlow = Flows.yaml(
            CHILD_RETRY_FLOW
                + FLOW_ERRORS.indent(4)
                + FLOW_FINALLY.indent(4)
        );
        harness.registerFlow(childRetryFlow);
        Execution created = Executions.created(childRetryFlow);
        harness.run(created, recordingWorker(State.Type.FAILED));

        // When: the last attempt fails
        harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
        Trace lastAttempt = harness.run(List.of(), recordingWorker(State.Type.FAILED));

        // Then: the handlers ran after the last attempt, and the execution only ended once their results were in
        assertThat(dispatchedTaskIds()).containsExactly("working-directory", "working-directory", "handler", "cleanup");
        assertThat(lastAttempt.steps().subList(0, indexOfResultOf(lastAttempt, "cleanup")))
            .as("no delivery before the cleanup result ended the execution")
            .noneMatch(WorkingDirectoryRetryTest::announcesTermination);
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED).hasRunning(childRetryFlow, 0);
        assertThat(harness.executionStateStore().findByIdWithoutAcl(created.getId()).findTaskRunsByTaskId("working-directory"))
            .singleElement()
            .extracting(taskRun -> taskRun.getState().getCurrent())
            .isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldRunTheLocalFinallyWhenTheLocalErrorsAreDisabled() {
        FlowWithSource childRetryFlow = Flows.yaml(
            CHILD_RETRY_FLOW
                + """
                    errors:
                      - id: handler
                        type: io.kestra.plugin.core.log.Log
                        message: handler
                        disabled: true
                    """.indent(4)
                + FLOW_FINALLY.indent(4)
        );

        Execution created = runUntilQuiet(childRetryFlow, recordingWorker(State.Type.FAILED));

        assertThat(dispatchedTaskIds()).containsExactly("working-directory", "working-directory", "cleanup");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @Test
    void shouldRetryTheWorkingDirectoryWhileTheChildHasAttemptsLeft() {
        // Given: the first run fails on the retried child
        FlowWithSource childRetryFlow = Flows.yaml(CHILD_RETRY_FLOW + FLOW_ERRORS);
        harness.registerFlow(childRetryFlow);
        Execution created = Executions.created(childRetryFlow);
        harness.run(created, recordingWorker(State.Type.FAILED));

        // Then: a retry is pending and no handler ran
        assertThat(dispatchedTaskIds()).containsExactly("working-directory");
        assertThat(harness).hasExecutionInState(created, State.Type.RETRYING).hasPendingDelays(1);

        // When: the second run replays the WorkingDirectory from its first child and succeeds
        harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
        harness.run(List.of(), recordingWorker(State.Type.SUCCESS));
        harness.run(List.of(Results.success(childOf(dispatched.getLast(), "ingest"), T0)), recordingWorker(State.Type.SUCCESS));

        // Then
        assertThat(dispatchedTaskIds()).containsExactly("working-directory", "working-directory");
        assertThat(harness).hasExecutionInState(created, State.Type.SUCCESS).hasRunning(childRetryFlow, 0);
    }

    @ParameterizedTest
    @CsvSource({ "generate, 3", "ingest, 2" })
    void shouldSpendTheWorkingDirectoryAttemptsOnTheFailingChildPolicy(String failingOnRerun, int expectedRuns) {
        // Given: the second child allows fewer attempts than the first, and the first run fails on the first child
        FlowWithSource twoPoliciesFlow = Flows.yaml("""
            id: working-directory-two-policies
            namespace: io.kestra.tests
            tasks:
              - id: working-directory
                type: io.kestra.plugin.core.flow.WorkingDirectory
                tasks:
                  - id: generate
                    type: io.kestra.plugin.core.log.Log
                    message: generate
                    retry:
                      type: constant
                      interval: PT1S
                      maxAttempts: 3
                  - id: ingest
                    type: io.kestra.plugin.core.log.Log
                    message: ingest
                    retry:
                      type: constant
                      interval: PT1S
                      maxAttempts: 2
            """);
        harness.registerFlow(twoPoliciesFlow);
        Execution created = Executions.created(twoPoliciesFlow);
        harness.run(created, recordingWorker(State.Type.FAILED));

        // When: from the second run on, the given child is the one failing
        ScriptedWorker rerunWorker = task ->
        {
            dispatched.add(task);
            return "generate".equals(failingOnRerun)
                ? Results.failed(childOf(task, "generate"), T0)
                : Results.success(childOf(task, "generate"), T0);
        };
        for (int i = 0; i < 4; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), rerunWorker);
            if ("ingest".equals(failingOnRerun) && dispatched.size() == 2) {
                harness.run(List.of(Results.failed(childOf(dispatched.getLast(), "ingest"), T0)), rerunWorker);
            }
        }

        // Then: the failing child's own policy is checked against the attempts the WorkingDirectory accumulated
        assertThat(dispatchedTaskIds()).hasSize(expectedRuns).containsOnly("working-directory");
        assertThat(harness).hasExecutionInState(created, State.Type.FAILED);
    }

    @ParameterizedTest
    @CsvSource({ "false, WARNING", "true, SUCCESS" })
    void shouldSoftenTheExhaustedChildFailureWhenTheWorkingDirectoryAllowsIt(boolean allowWarning, State.Type expected) {
        FlowWithSource allowFailureFlow = Flows.yaml(
            CHILD_RETRY_FLOW.replace(
                "type: io.kestra.plugin.core.flow.WorkingDirectory\n", "type: io.kestra.plugin.core.flow.WorkingDirectory\n    allowFailure: true\n    allowWarning: " + allowWarning + "\n"
            )
                + """
                      - id: after
                        type: io.kestra.plugin.core.log.Log
                        message: after
                    """
                + FLOW_ERRORS
        );

        Execution created = runUntilQuiet(allowFailureFlow, recordingWorker(State.Type.FAILED));

        assertThat(dispatchedTaskIds()).containsExactly("working-directory", "working-directory", "after");
        assertThat(harness).hasExecutionInState(created, expected);
    }

    @Test
    void shouldReleaseTheConcurrencySlotToTheQueuedExecutionWhenTheRetriesAreExhausted() {
        // Given: the first execution holds the only slot while it retries, the second waits behind it
        FlowWithSource childRetryFlow = Flows.yaml(CHILD_RETRY_FLOW + FLOW_ERRORS);
        harness.registerFlow(childRetryFlow);
        Execution slotHolder = Executions.created(childRetryFlow);
        Execution queued = Executions.created(childRetryFlow);
        ScriptedWorker failsTheSlotHolderOnly = task ->
        {
            dispatched.add(task);
            if ("handler".equals(task.getTaskRun().getTaskId())) {
                return Results.success(task, T0);
            }
            return slotHolder.getId().equals(task.getTaskRun().getExecutionId())
                ? Results.failed(childOf(task, "generate"), T0)
                : Results.success(childOf(task, "generate"), T0);
        };
        harness.run(List.of(slotHolder, queued), failsTheSlotHolderOnly);
        assertThat(harness).as("while the retry is pending").hasExecutionInState(slotHolder, State.Type.RETRYING).hasQueuedExactly(queued).hasRunning(childRetryFlow, 1);

        // When: the last attempt fails
        harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
        harness.run(List.of(), failsTheSlotHolderOnly);
        harness.run(List.of(Results.success(childOf(dispatched.getLast(), "ingest"), T0)), failsTheSlotHolderOnly);

        // Then: the slot went to the queued execution, which ran to completion
        assertThat(harness).hasExecutionInState(slotHolder, State.Type.FAILED).hasExecutionInState(queued, State.Type.SUCCESS).hasNothingQueued().hasRunning(childRetryFlow, 0);
    }

    @Test
    void shouldFailAnExecutionPersistedBeforeTheFixWhoseChildRetriesWereAlreadyExhausted() throws Exception {
        // Given: the shape left behind by a pre-fix executor, with no retry pending: two runs, the latest child failed
        FlowWithSource childRetryFlow = Flows.yaml(CHILD_RETRY_FLOW + FLOW_ERRORS);
        harness.registerFlow(childRetryFlow);
        Execution stuck = stuckAfterTwoRuns(childRetryFlow);
        harness.executionStateStore().save(stuck);

        // When: anything makes the executor look at it again
        harness.run(List.of(new ExecutionEvent(stuck, ExecutionEventType.UPDATED)), recordingWorker(State.Type.FAILED));

        // Then
        assertThat(dispatchedTaskIds()).containsExactly("handler");
        assertThat(harness).hasExecutionInState(stuck, State.Type.FAILED).hasRunning(childRetryFlow, 0);
    }

    private Execution runUntilQuiet(FlowWithSource flow, ScriptedWorker worker) {
        harness.registerFlow(flow);
        Execution created = Executions.created(flow);
        harness.run(created, worker);
        // fire every retry; more ticks than maxAttempts so an extra retry would show in the dispatched tasks
        for (int i = 0; i < 4; i++) {
            harness.tickExecutionDelays(AFTER_ANY_RETRY_DELAY);
            harness.run(List.of(), worker);
        }
        return created;
    }

    private Execution stuckAfterTwoRuns(FlowWithSource flow) throws Exception {
        Execution execution = Executions.created(flow).withState(State.Type.RUNNING);
        WorkingDirectory workingDirectory = (WorkingDirectory) flow.findTaskByTaskId("working-directory");
        TaskRun secondRun = TaskRun.of(execution, ResolvedTask.of(workingDirectory))
            .withAttempts(List.of(runningAttempt(), runningAttempt()))
            .withState(State.Type.RUNNING);
        TaskRun failedChild = childOf(flow, execution, secondRun, "generate").getTaskRun()
            .withAttempts(List.of(runningAttempt()))
            .withStateAndAttempt(State.Type.FAILED);
        return execution.withTaskRunList(List.of(secondRun, failedChild));
    }

    private static TaskRunAttempt runningAttempt() {
        return TaskRunAttempt.builder().state(new State(State.Type.RUNNING)).build();
    }

    private List<String> dispatchedTaskIds() {
        return dispatched.stream().map(task -> task.getTaskRun().getTaskId()).toList();
    }

    /** Like the worker, reports the first child of each dispatched WorkingDirectory, in the given state. */
    private ScriptedWorker recordingWorker(State.Type generateState) {
        return task ->
        {
            dispatched.add(task);
            if (!(task.getTask() instanceof WorkingDirectory)) {
                return Results.success(task, T0);
            }
            WorkerTask generate = childOf(task, "generate");
            return generateState == State.Type.FAILED ? Results.failed(generate, T0) : Results.success(generate, T0);
        };
    }

    /** The worker task of a child, built as the worker builds it: under the WorkingDirectory's task run. */
    private WorkerTask childOf(WorkerTask workingDirectory, String taskId) {
        Execution execution = harness.executionStateStore().findByIdWithoutAcl(workingDirectory.getTaskRun().getExecutionId());
        FlowWithSource flow = harness.flowMetaStore().findByExecutionForRuntime(execution).orElseThrow();
        return childOf(flow, execution, workingDirectory.getTaskRun(), taskId);
    }

    private WorkerTask childOf(FlowWithSource flow, Execution execution, TaskRun workingDirectoryTaskRun, String taskId) {
        WorkingDirectory workingDirectory = (WorkingDirectory) flow.findTaskByTaskIdOrNull(workingDirectoryTaskRun.getTaskId());
        Task child = workingDirectory.getTasks().stream().filter(task -> task.getId().equals(taskId)).findFirst().orElseThrow();
        RunContext runContext = harness.runContextFactory().of(flow, workingDirectory, execution, workingDirectoryTaskRun);
        return workingDirectory.workerTask(workingDirectoryTaskRun, child, runContext);
    }

    private static int indexOfResultOf(Trace trace, String taskId) {
        return IntStream.range(0, trace.steps().size())
            .filter(i -> trace.steps().get(i).message() instanceof WorkerTaskResult result && taskId.equals(result.getTaskRun().getTaskId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no result of '" + taskId + "' was delivered"));
    }

    private static boolean announcesTermination(Trace.Step step) {
        return step.emitted("executionEvent").anyMatch(emission -> emission.as(ExecutionEvent.class).eventType() == ExecutionEventType.TERMINATED);
    }
}
