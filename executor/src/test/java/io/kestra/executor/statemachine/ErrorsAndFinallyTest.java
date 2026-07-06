package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;

/**
 * Layer-1 errors / finally / afterExecution decision matrix, driven as explicit sagas —
 * process → succeed/fail the emitted worker task → assert the ExecutorContext command object
 * and the intermediate cycles.
 * Twins the decision logic behind FinallyTest#flowWithoutErrors / #flowWithErrors /
 * #flowErrorBlockWithErrors, AbstractRunnerTest#errors and
 * AbstractRunnerTest#shouldCallTasksAfterExecution (AfterExecutionTestCase), each of which
 * boots a full StandAloneRunner over H2. The decision code under test is
 * ExecutorService#handleEnd/#onEnd (final-state computation over errors/finally),
 * ExecutorService#handleNext (errors/finally branch resolution) and
 * ExecutorService#handleAfterExecution + ExecutionService#resolveAfterExecutionTasks.
 * No Micronaut, no database, no queues.
 */
class ErrorsAndFinallyTest {

    private static final Instant ATTEMPT_END = Instant.parse("2026-07-06T10:00:00Z");
    private static final String MAIN_TASK = "main-task";
    private static final String ERROR_TASK = "error-task";
    private static final String FINALLY_TASK = "finally-task";
    private static final String AFTER_TASK = "after-task";

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- errors branch

    @Test
    void shouldRunErrorsBranchThenFailWhenMainTaskFails() throws Exception {
        // Given: a flow with an errors branch — the executor first emits the main task
        FlowWithSource flow = flowWithErrors();
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        assertThat(cycle1)
            .hasWorkerTaskFor(MAIN_TASK)
            .executionInState(State.Type.RUNNING);

        // When: the "worker" fails the main task
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then (intermediate cycle): the error task is emitted as the next worker task while
        // the execution keeps RUNNING — the errors branch runs before the execution ends
        assertThat(cycle2)
            .hasWorkerTaskFor(ERROR_TASK)
            .hasTaskRunInState(MAIN_TASK, State.Type.FAILED)
            .executionInState(State.Type.RUNNING)
            .hasNoExecutionDelays()
            .hasNoSubflowExecutions();

        // When: the error task itself succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(cycle2.getWorkerTasks().getFirst(), ATTEMPT_END.plusSeconds(60))
        );

        // Then: the execution still ends FAILED — a successful errors branch does not rescue it
        assertThat(cycle3)
            .hasTaskRunInState(ERROR_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.FAILED)
            .transitioned(State.Type.RUNNING, State.Type.FAILED)
            .updatedFrom("onEnd")
            .hasNoWorkerTasks();

        // side channels stayed clean — everything went through the command object
        Assertions.assertThat(harness.kills()).isEmpty();
        Assertions.assertThat(harness.loopEvents()).isEmpty();
    }

    @Test
    void shouldNotRunErrorsBranchWhenMainTaskSucceeds() throws Exception {
        // Given: the same errors flow
        FlowWithSource flow = flowWithErrors();
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTaskFor(MAIN_TASK);

        // When: the main task succeeds
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.success(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then: straight to SUCCESS — the error task is never emitted and no taskrun exists for it
        assertThat(cycle2)
            .hasTaskRunInState(MAIN_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .transitioned(State.Type.RUNNING, State.Type.SUCCESS)
            .hasNoWorkerTasks()
            .hasNoNexts();
        Assertions.assertThat(cycle2.getExecution().findTaskRunsByTaskId(ERROR_TASK)).isEmpty();
        Assertions.assertThat(cycle2.getExecution().getTaskRunList()).hasSize(1);
    }

    // --- finally

    @Test
    void shouldRunFinallyTaskThenSucceedWhenMainTaskSucceeds() throws Exception {
        // Given: a flow with a finally block
        FlowWithSource flow = flowWithFinally();
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTaskFor(MAIN_TASK);

        // When: the main task succeeds
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.success(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then (intermediate cycle): the finally task runs before the execution may end
        assertThat(cycle2)
            .hasWorkerTaskFor(FINALLY_TASK)
            .hasTaskRunInState(MAIN_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.RUNNING);

        // When: the finally task succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(cycle2.getWorkerTasks().getFirst(), ATTEMPT_END.plusSeconds(60))
        );

        // Then: everything green — SUCCESS
        assertThat(cycle3)
            .hasTaskRunInState(FINALLY_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .transitioned(State.Type.RUNNING, State.Type.SUCCESS)
            .hasNoWorkerTasks();
    }

    @Test
    void shouldRunFinallyTaskThenFailWhenMainTaskFails() throws Exception {
        // Given: a flow with a finally block
        FlowWithSource flow = flowWithFinally();
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTaskFor(MAIN_TASK);

        // When: the main task fails
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then (intermediate cycle): finally still runs even after a failure
        assertThat(cycle2)
            .hasWorkerTaskFor(FINALLY_TASK)
            .hasTaskRunInState(MAIN_TASK, State.Type.FAILED)
            .executionInState(State.Type.RUNNING);

        // When: the finally task succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(cycle2.getWorkerTasks().getFirst(), ATTEMPT_END.plusSeconds(60))
        );

        // Then: the main-task failure still decides the final state — FAILED
        assertThat(cycle3)
            .hasTaskRunInState(FINALLY_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.FAILED)
            .transitioned(State.Type.RUNNING, State.Type.FAILED);
    }

    // --- afterExecution

    @Test
    void shouldEmitAfterExecutionTaskOnlyAfterExecutionReachesTerminalState() throws Exception {
        // Given: a flow with an afterExecution block
        FlowWithSource flow = flowWithAfterExecution();
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTaskFor(MAIN_TASK);
        // before the terminal state nothing afterExecution-related exists
        Assertions.assertThat(cycle1.getExecution().findTaskRunsByTaskId(AFTER_TASK)).isEmpty();

        // When: the main task succeeds — within this single cycle handleEnd turns the execution
        // terminal FIRST, then handleAfterExecution emits the afterExecution taskrun
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.success(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then: the state transition ordering proves terminal-before-afterExecution — the cycle
        // transitioned RUNNING → SUCCESS and the `from` audit trail shows onEnd contributed
        // before handleAfterExecution did
        assertThat(cycle2)
            .executionInState(State.Type.SUCCESS)
            .transitioned(State.Type.RUNNING, State.Type.SUCCESS)
            .updatedFrom("onEnd")
            // note: production audit string carries a trailing space (ExecutorService#handleAfterExecution)
            .updatedFrom("handleAfterExecution ")
            // the afterExecution taskrun exists AFTER the terminal transition, still to be run
            .hasTaskRunInState(AFTER_TASK, State.Type.CREATED)
            // no worker task in the terminal cycle itself: the taskrun is only materialized by
            // onNexts at the end of the cycle, so the emission needs a follow-up event
            .hasNoWorkerTasks();
        List<String> from = cycle2.getFrom();
        Assertions.assertThat(from.indexOf("onEnd"))
            .as("terminal transition (onEnd) must precede afterExecution resolution (from: %s)", from)
            .isLessThan(from.indexOf("handleAfterExecution "));

        // When: the terminal execution is re-processed (production re-emits the execution message)
        ExecutorContext cycle3 = harness.process(flow, cycle2.getExecution());

        // Then: only now is the afterExecution worker task emitted, on an already-SUCCESS execution
        assertThat(cycle3)
            .hasWorkerTaskFor(AFTER_TASK)
            .executionInState(State.Type.SUCCESS);

        // When: the afterExecution task completes
        ExecutorContext cycle4 = harness.processResult(
            flow,
            cycle3,
            Results.success(cycle3.getWorkerTasks().getFirst(), ATTEMPT_END.plusSeconds(60))
        );

        // Then: the execution state is untouched by the afterExecution outcome
        assertThat(cycle4)
            .hasTaskRunInState(AFTER_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .hasNoWorkerTasks();
    }

    // --- errors + finally together

    @Test
    void shouldRunErrorsThenFinallyThenFailWhenMainTaskFailsWithBothBranches() throws Exception {
        // Given: a flow carrying BOTH an errors branch and a finally block
        FlowWithSource flow = flowWithErrorsAndFinally();
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTaskFor(MAIN_TASK);

        // When: the main task fails
        ExecutorContext cycle2 = harness.processResult(
            flow,
            cycle1,
            Results.failed(cycle1.getWorkerTasks().getFirst(), ATTEMPT_END)
        );

        // Then (ordering, step 1): the errors branch runs first
        assertThat(cycle2)
            .hasWorkerTaskFor(ERROR_TASK)
            .executionInState(State.Type.RUNNING);
        Assertions.assertThat(cycle2.getExecution().findTaskRunsByTaskId(FINALLY_TASK))
            .as("finally must not start before the errors branch completed")
            .isEmpty();

        // When: the error task succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(cycle2.getWorkerTasks().getFirst(), ATTEMPT_END.plusSeconds(60))
        );

        // Then (ordering, step 2): only then does the finally task run
        assertThat(cycle3)
            .hasWorkerTaskFor(FINALLY_TASK)
            .hasTaskRunInState(ERROR_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.RUNNING);

        // When: the finally task succeeds
        ExecutorContext cycle4 = harness.processResult(
            flow,
            cycle3,
            Results.success(cycle3.getWorkerTasks().getFirst(), ATTEMPT_END.plusSeconds(120))
        );

        // Then (ordering, step 3): the execution ends FAILED — error and finally successes
        // never rescue the failed main task
        assertThat(cycle4)
            .hasTaskRunInState(MAIN_TASK, State.Type.FAILED)
            .hasTaskRunInState(ERROR_TASK, State.Type.SUCCESS)
            .hasTaskRunInState(FINALLY_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.FAILED)
            .transitioned(State.Type.RUNNING, State.Type.FAILED)
            .hasNoWorkerTasks();
        // full saga ordering over the taskrun list: main, then error, then finally
        Assertions.assertThat(cycle4.getExecution().getTaskRunList())
            .extracting(taskRun -> taskRun.getTaskId())
            .containsExactly(MAIN_TASK, ERROR_TASK, FINALLY_TASK);
    }

    // --- fixtures (inline YAML — same graph shapes as core/src/test/resources/flows/valids/
    // finally-flow.yaml / finally-flow-error.yaml / after-execution.yaml, with Log tasks
    // everywhere: in Layer-1 sagas the worker is the test, outcomes are scripted via Results)

    private FlowWithSource flowWithErrors() {
        FlowWithSource flow = Flows.yaml("""
            id: errors-branch
            namespace: io.kestra.tests

            tasks:
              - id: main-task
                type: io.kestra.plugin.core.log.Log
                message: main

            errors:
              - id: error-task
                type: io.kestra.plugin.core.log.Log
                message: error handler
            """);
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource flowWithFinally() {
        FlowWithSource flow = Flows.yaml("""
            id: finally-block
            namespace: io.kestra.tests

            tasks:
              - id: main-task
                type: io.kestra.plugin.core.log.Log
                message: main

            finally:
              - id: finally-task
                type: io.kestra.plugin.core.log.Log
                message: always runs
            """);
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource flowWithAfterExecution() {
        FlowWithSource flow = Flows.yaml("""
            id: after-execution-block
            namespace: io.kestra.tests

            tasks:
              - id: main-task
                type: io.kestra.plugin.core.log.Log
                message: main

            afterExecution:
              - id: after-task
                type: io.kestra.plugin.core.log.Log
                message: after the execution ended
            """);
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource flowWithErrorsAndFinally() {
        FlowWithSource flow = Flows.yaml("""
            id: errors-and-finally
            namespace: io.kestra.tests

            tasks:
              - id: main-task
                type: io.kestra.plugin.core.log.Log
                message: main

            errors:
              - id: error-task
                type: io.kestra.plugin.core.log.Log
                message: error handler

            finally:
              - id: finally-task
                type: io.kestra.plugin.core.log.Log
                message: always runs
            """);
        harness.registerFlow(flow);
        return flow;
    }
}
