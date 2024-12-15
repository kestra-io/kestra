package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.*;
import io.kestra.core.utils.Rethrow.RunnableChecked;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.plugin.core.flow.*;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// must be per-class to allow calling once init() which took a lot of time
public abstract class JdbcRunnerTest {

    @Inject
    private StandAloneRunner runner;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logsQueue;

    @Inject
    private RestartCaseTest restartCaseTest;

    @Inject
    private FlowTriggerCaseTest flowTriggerCaseTest;

    @Inject
    protected MultipleConditionTriggerCaseTest multipleConditionTriggerCaseTest;

    @Inject
    private PluginDefaultsCaseTest pluginDefaultsCaseTest;

    @Inject
    private FlowCaseTest flowCaseTest;

    @Inject
    private WorkingDirectoryTest.Suite workingDirectoryTest;

    @Inject
    private PauseTest.Suite pauseTest;

    @Inject
    private SkipExecutionCaseTest skipExecutionCaseTest;

    @Inject
    private ForEachItemCaseTest forEachItemCaseTest;

    @Inject
    private WaitForCaseTest waitForTestCaseTest;

    @Inject
    private FlowConcurrencyCaseTest flowConcurrencyCaseTest;

    @Inject
    private ScheduleDateCaseTest scheduleDateCaseTest;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private SLATestCase slaTestCase;

    @BeforeAll
    void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        runner.setSchedulerEnabled(false);
        runner.run();
    }

    @Test
    void full() throws Exception {
        executeWithFlow(List.of("flows/valids/full.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "full", null, null,
                Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(13));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat(
                (String) execution.findTaskRunsByTaskId("t2").getFirst().getOutputs().get("value"),
                containsString("value1"));
        });
    }

    @Test
    void logs() throws Exception {
        executeWithFlow(List.of("flows/valids/logs.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "logs", null, null,
                Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(5));
        });
    }

    @Test
    void errors() throws Exception {
        executeWithFlow(List.of("flows/valids/errors.yaml"), () -> {
            List<LogEntry> logs = new CopyOnWriteArrayList<>();
            Flux<LogEntry> receive = TestsUtils.receive(logsQueue,
                either -> logs.add(either.getLeft()));

            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "errors", null, null,
                Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(7));

            receive.blockLast();
            LogEntry logEntry = TestsUtils.awaitLog(logs,
                log -> log.getMessage().contains("- task: failed, message: Task failure"));
            assertThat(logEntry, notNullValue());
            assertThat(logEntry.getMessage(), is("- task: failed, message: Task failure"));
        });
    }

    @Test
    void sequential() throws Exception {
        executeWithFlow(List.of("flows/valids/sequential.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sequential", null,
                null, Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(11));
        });
    }

    @Test
    void parallel() throws Exception {
        executeWithFlow(List.of("flows/valids/parallel.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "parallel", null,
                null, Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(8));
        });
    }

    @RetryingTest(5)
    void parallelNested() throws Exception {
        executeWithFlow(List.of("flows/valids/parallel-nested.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "parallel-nested",
                null, null, Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(11));
        });
    }

    @Test
    void eachParallelWithSubflowMissing() throws Exception {
        executeWithFlow(List.of("flows/valids/each-parallel-subflow-notfound.yml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests",
                "each-parallel-subflow-notfound");

            assertThat(execution, notNullValue());
            assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
            // on JDBC, when using an each parallel, the flow is failed even if not all subtasks of the each parallel are ended as soon as
            // there is one failed task FIXME https://github.com/kestra-io/kestra/issues/2179
            // so instead of asserting that all tasks FAILED we assert that at least two failed (the each parallel and one of its subtasks)
            assertThat(
                execution.getTaskRunList().stream().filter(taskRun -> taskRun.getState().isFailed())
                    .count(), greaterThanOrEqualTo(2L)); // Should be 3
        });
    }

    @Test
    void eachSequentialNested() throws Exception {
        executeWithFlow(List.of("flows/valids/each-sequential-nested.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests",
                "each-sequential-nested", null, null, Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(23));
        });
    }

    @Test
    void eachParallel() throws Exception {
        executeWithFlow(List.of("flows/valids/each-parallel.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-parallel", null,
                null, Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(8));
        });
    }

    @Test
    void eachParallelNested() throws Exception {
        executeWithFlow(List.of("flows/valids/each-parallel-nested.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests",
                "each-parallel-nested", null, null, Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(11));
        });
    }

    @Test
    void restartFailed() throws Exception {
        executeWithFlow(List.of("flows/valids/restart_last_failed.yaml"),
            () -> restartCaseTest.restartFailedThenSuccess());
    }

    @RetryingTest(5)
    void replay() throws Exception {
        executeWithFlow(List.of("flows/valids/restart-each.yaml"), () ->
            restartCaseTest.replay());
    }

    @RetryingTest(5)
    void restartMultiple() throws Exception {
        executeWithFlow(List.of("flows/valids/failed-first.yaml"), () ->
            restartCaseTest.restartMultiple());
    }

    @RetryingTest(5)
    void flowTrigger() throws Exception {
        executeWithFlow(List.of("flows/valids/trigger-flow-listener-no-inputs.yaml",
            "flows/valids/trigger-flow-listener.yaml",
            "flows/valids/trigger-flow-listener-namespace-condition.yaml",
            "flows/valids/trigger-flow.yaml"), () ->
            flowTriggerCaseTest.trigger());
    }

    @Test
    void flowTriggerWithPause() throws Exception {
        executeWithFlow(List.of("flows/valids/trigger-flow-listener-with-pause.yaml",
            "flows/valids/trigger-flow-with-pause.yaml"), () ->
            flowTriggerCaseTest.triggerWithPause());
    }

    @Test
    void multipleConditionTrigger() throws Exception {
        executeWithFlow(List.of("flows/valids/trigger-multiplecondition-listener.yaml",
            "flows/valids/trigger-multiplecondition-flow-a.yaml",
            "flows/valids/trigger-multiplecondition-flow-b.yaml"), () ->
            multipleConditionTriggerCaseTest.trigger());
    }

    @Test
    void multipleConditionTriggerFailed() throws Exception {
        executeWithFlow(
            List.of("flows/valids/trigger-flow-listener-namespace-condition.yaml",
                "flows/valids/trigger-multiplecondition-flow-c.yaml",
                "flows/valids/trigger-multiplecondition-flow-d.yaml"), () ->
                multipleConditionTriggerCaseTest.failed());
    }

    @Test
    void flowTriggerPreconditions() throws Exception {
        executeWithFlow(List.of("flows/valids/flow-trigger-preconditions-flow-listen.yaml",
            "flows/valids/flow-trigger-preconditions-flow-a.yaml",
            "flows/valids/flow-trigger-preconditions-flow-b.yaml"), () ->
            multipleConditionTriggerCaseTest.flowTriggerPreconditions());
    }

    @RetryingTest(5)
    void eachWithNull() throws Exception {
        executeWithFlow(List.of("flows/valids/each-null.yaml"), () ->
            EachSequentialTest.eachNullTest(runnerUtils, logsQueue));
    }

    @Test
    void taskDefaults() throws Exception {
        executeWithFlow(List.of("flows/tests/plugin-defaults.yaml"), () ->
            pluginDefaultsCaseTest.taskDefaults());
    }

    @RetryingTest(5)
    void flowWaitSuccess() throws Exception {
        executeWithFlow(List.of("flows/valids/switch.yaml",
            "flows/valids/task-flow.yaml",
            "flows/valids/task-flow-inherited-labels.yaml"), () ->
            flowCaseTest.waitSuccess());
    }

    @Test
    void flowWaitFailed() throws Exception {
        executeWithFlow(List.of("flows/valids/switch.yaml",
            "flows/valids/task-flow.yaml",
            "flows/valids/task-flow-inherited-labels.yaml"), () ->
            flowCaseTest.waitFailed());
    }

    @Test
    public void invalidOutputs() throws Exception {
        executeWithFlow(List.of("flows/valids/switch.yaml",
            "flows/valids/task-flow.yaml",
            "flows/valids/task-flow-inherited-labels.yaml"), () ->
            flowCaseTest.invalidOutputs());
    }

    @Test
    public void workerSuccess() throws Exception {
        executeWithFlow(List.of("flows/valids/working-directory.yaml"), () ->
            workingDirectoryTest.success(runnerUtils));
    }

    @Test
    public void workerFailed() throws Exception {
        executeWithFlow(List.of("flows/valids/working-directory.yaml"), () ->
            workingDirectoryTest.failed(runnerUtils));
    }

    @Test
    public void workerEach() throws Exception {
        executeWithFlow(List.of("flows/valids/working-directory-each.yaml"), () ->
            workingDirectoryTest.each(runnerUtils));
    }

    @RetryingTest(5) // flaky on MySQL
    public void pauseRun() throws Exception {
        executeWithFlow(List.of("flows/valids/pause.yaml"), () ->
            pauseTest.run(runnerUtils));
    }

    @Test
    public void pauseRunDelay() throws Exception {
        executeWithFlow(List.of("flows/valids/pause-delay.yaml"), () ->
            pauseTest.runDelay(runnerUtils));
    }

    @Test
    public void pauseRunDelayFromInput() throws Exception {
        executeWithFlow(List.of("flows/valids/pause-delay-from-input.yaml"), () ->
            pauseTest.runDelayFromInput(runnerUtils));
    }

    @Test
    public void pauseRunParallelDelay() throws Exception {
        executeWithFlow(List.of("flows/valids/each-parallel-pause.yml"), () ->
            pauseTest.runParallelDelay(runnerUtils));
    }

    @Test
    public void pauseRunTimeout() throws Exception {
        executeWithFlow(List.of("flows/valids/pause-timeout.yaml"), () ->
            pauseTest.runTimeout(runnerUtils));
    }

    @RetryingTest(5)
    void executionDate() throws Exception {
        executeWithFlow(List.of("flows/valids/execution.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests",
                "execution-start-date", null, null, Duration.ofSeconds(60));

            assertThat((String) execution.getTaskRunList().getFirst().getOutputs().get("value"),
                matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"));
        });
    }

    @Test
    void skipExecution() throws Exception {
        executeWithFlow(List.of("flows/valids/minimal.yaml"), () ->
            skipExecutionCaseTest.skipExecution());
    }

    @RetryingTest(5)
    protected void forEachItem() throws Exception {
        executeWithFlow(List.of("flows/valids/for-each-item-subflow.yaml",
            "flows/valids/for-each-item.yaml"), () ->
            forEachItemCaseTest.forEachItem());
    }

    @RetryingTest(5)
    protected void forEachItemEmptyItems() throws Exception {
        executeWithFlow(List.of("flows/valids/for-each-item.yaml"), () ->
            forEachItemCaseTest.forEachItemEmptyItems());
    }

    @RetryingTest(5)
    protected void forEachItemNoWait()
        throws Exception {
        executeWithFlow(List.of("flows/valids/for-each-item-subflow.yaml",
            "flows/valids/for-each-item-no-wait.yaml"), () ->
            forEachItemCaseTest.forEachItemNoWait()
        );
    }

    @RetryingTest(5)
    protected void forEachItemFailed()
        throws Exception {
        executeWithFlow(List.of("flows/valids/for-each-item-subflow-failed.yaml",
            "flows/valids/for-each-item-failed.yaml"), () ->
            forEachItemCaseTest.forEachItemFailed()
        );
    }

    @RetryingTest(5)
    protected void forEachItemSubflowOutputs()
        throws Exception {
        executeWithFlow(List.of("flows/valids/for-each-item-outputs-subflow.yaml",
            "flows/valids/for-each-item-outputs.yaml"), () ->
            forEachItemCaseTest.forEachItemWithSubflowOutputs()
        );
    }

    @Test
    void concurrencyCancel() throws Exception {
        executeWithFlow(List.of("flows/valids/flow-concurrency-cancel.yml"), () ->
            flowConcurrencyCaseTest.flowConcurrencyCancel()
        );
    }

    @Test
    void concurrencyFail() throws Exception {
        executeWithFlow(List.of("flows/valids/flow-concurrency-fail.yml"), () ->
            flowConcurrencyCaseTest.flowConcurrencyFail()
        );
    }

    @Test
    void concurrencyQueue() throws Exception {
        executeWithFlow(List.of("flows/valids/flow-concurrency-queue.yml"), () ->
            flowConcurrencyCaseTest.flowConcurrencyQueue()
        );
    }

    @Test
    void concurrencyQueuePause() throws Exception {
        executeWithFlow(List.of("flows/valids/flow-concurrency-queue-pause.yml"), () ->
            flowConcurrencyCaseTest.flowConcurrencyQueuePause()
        );
    }

    @Test
    void concurrencyCancelPause() throws Exception {
        executeWithFlow(List.of("flows/valids/flow-concurrency-cancel-pause.yml"), () ->
            flowConcurrencyCaseTest.flowConcurrencyCancelPause()
        );
    }

    @Test
    void badExecutable() throws Exception {
        executeWithFlow(List.of("flows/valids/executable-fail.yml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "executable-fail");

            assertThat(execution.getTaskRunList().size(), is(1));
            assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(),
                is(State.Type.FAILED));
            assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        });
    }

    @Test
    void dynamicTask() throws Exception {
        executeWithFlow(List.of("flows/valids/dynamic-task.yaml"), () -> {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "dynamic-task");

            assertThat(execution.getTaskRunList().size(), is(3));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        });
    }

    @Test
    void waitFor() throws Exception {
        executeWithFlow(List.of("flows/valids/waitfor.yaml"), () ->
            waitForTestCaseTest.waitfor()
        );
    }

    @Test
    void waitforMaxIterations() throws Exception {
        executeWithFlow(List.of("flows/valids/waitfor-max-iterations.yaml"), () ->
            waitForTestCaseTest.waitforMaxIterations()
        );
    }

    @Test
    void waitforMaxDuration() throws Exception {
        executeWithFlow(List.of("flows/valids/waitfor-max-duration.yaml"), () ->
            waitForTestCaseTest.waitforMaxDuration()
        );
    }

    @Test
    void waitforNoSuccess() throws Exception {
        executeWithFlow(List.of("flows/valids/waitfor-no-success.yaml"), () ->
            waitForTestCaseTest.waitforNoSuccess()
        );
    }

    @Test
    void waitforMultipleTasks() throws Exception {
        executeWithFlow(List.of("flows/valids/waitfor-multiple-tasks.yaml"), () ->
            waitForTestCaseTest.waitforMultipleTasks()
        );
    }

    @Test
    void waitforMultipleTasksFailed() throws Exception {
        executeWithFlow(List.of("flows/valids/waitfor-multiple-tasks-failed.yaml"), () ->
            waitForTestCaseTest.waitforMultipleTasksFailed()
        );
    }

    @Test
    void flowTooLarge() throws Exception {
        executeWithFlow(List.of("flows/valids/inputs-large.yaml"), () -> {
            char[] chars = new char[200000];
            Arrays.fill(chars, 'a');

            Map<String, Object> inputs = new HashMap<>(InputsTest.inputs);
            inputs.put("string", new String(chars));

            Execution execution = runnerUtils.runOne(
                null,
                "io.kestra.tests",
                "inputs-large",
                null,
                (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
                Duration.ofSeconds(120)
            );

            assertThat(execution.getTaskRunList().size(),
                greaterThanOrEqualTo(6)); // the exact number is test-run-dependent.
            assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

            // To avoid flooding the database with big messages, we re-init it
            init();
        });
    }

    @Test
    void queueMessageTooLarge() throws Exception {
        executeWithFlow(List.of("flows/valids/inputs-large.yaml"), () -> {
            char[] chars = new char[1100000];
            Arrays.fill(chars, 'a');

            Map<String, Object> inputs = new HashMap<>(InputsTest.inputs);
            inputs.put("string", new String(chars));

            var exception = assertThrows(QueueException.class, () -> runnerUtils.runOne(
                null,
                "io.kestra.tests",
                "inputs-large",
                null,
                (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
                Duration.ofSeconds(60)
            ));

            // the size is different on all runs, so we cannot assert on the exact message size
            assertThat(exception.getMessage(), containsString("Message of size"));
            assertThat(exception.getMessage(),
                containsString("has exceeded the configured limit of 1048576"));
            assertThat(exception, instanceOf(MessageTooBigException.class));
        });
    }

    @Test
    void workerTaskResultTooLarge() throws Exception {
        executeWithFlow(List.of("flows/valids/workertask-result-too-large.yaml"), () -> {
            List<LogEntry> logs = new CopyOnWriteArrayList<>();
            Flux<LogEntry> receive = TestsUtils.receive(logsQueue,
                either -> logs.add(either.getLeft()));

            Execution execution = runnerUtils.runOne(
                null,
                "io.kestra.tests",
                "workertask-result-too-large"
            );

            LogEntry matchingLog = TestsUtils.awaitLog(logs, log -> log.getMessage()
                .startsWith("Unable to emit the worker task result to the queue"));
            receive.blockLast();

            assertThat(matchingLog, notNullValue());
            assertThat(matchingLog.getLevel(), is(Level.ERROR));
            // the size is different on all runs, so we cannot assert on the exact message size
            assertThat(matchingLog.getMessage(), containsString("Message of size"));
            assertThat(matchingLog.getMessage(),
                containsString("has exceeded the configured limit of 1048576"));

            assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
            assertThat(execution.getTaskRunList().size(), is(1));
        });

    }

    @Test
    void shouldScheduleOnDate() throws Exception {
        executeWithFlow(List.of("flows/valids/minimal.yaml"), () ->
            scheduleDateCaseTest.shouldScheduleOnDate()
        );
    }

    @Test
    void maxDurationSLAShouldFail() throws Exception {
        executeWithFlow(List.of("flows/valids/sla-max-duration-fail.yaml"), () ->
            slaTestCase.maxDurationSLAShouldFail()
        );
    }

    @Test
    void maxDurationSLAShouldPass() throws Exception {
        executeWithFlow(List.of("flows/valids/sla-max-duration-ok.yaml"), () ->
            slaTestCase.maxDurationSLAShouldPass()
        );
    }

    @Test
    void executionConditionSLAShouldPass() throws Exception {
        executeWithFlow(List.of("flows/valids/sla-execution-condition.yaml"), () ->
            slaTestCase.executionConditionSLAShouldPass()
        );
    }

    @Test
    void executionConditionSLAShouldCancel() throws Exception {
        executeWithFlow(List.of("flows/valids/sla-execution-condition.yaml"), () ->
            slaTestCase.executionConditionSLAShouldCancel()
        );
    }

    @Test
    void executionConditionSLAShouldLabel() throws Exception {
        executeWithFlow(List.of("flows/valids/sla-execution-condition.yaml"), () ->
            slaTestCase.executionConditionSLAShouldLabel()
        );
    }

    private void executeWithFlow(List<String> flowPaths, RunnableChecked runnableChecked)
        throws Exception {

        for (String path : flowPaths) {
            TestsUtils.loads(repositoryLoader, Objects.requireNonNull(TestsUtils.class.getClassLoader().getResource(path)));
        }

        try {
            runnableChecked.run();
        } catch (Exception e){
            throw e;
        } finally {
            flowRepository.findAllForAllTenants().forEach(flow -> flowRepository.delete(
                FlowWithSource.of(flow, "unused")));
        }
    }
}
