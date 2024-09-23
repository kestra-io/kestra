package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.*;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
public abstract class JdbcRunnerTest {
    @Inject
    private StandAloneRunner runner;

    @Inject
    JdbcTestUtils jdbcTestUtils;

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
    private MultipleConditionTriggerCaseTest multipleConditionTriggerCaseTest;

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

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(repositoryLoader);
        runner.setSchedulerEnabled(false);
        runner.run();
    }

    @Test
    void full() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "full", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(13));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat((String) execution.findTaskRunsByTaskId("t2").getFirst().getOutputs().get("value"), containsString("value1"));
    }

    @Test
    void logs() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "logs", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(5));
    }

    @Test
    void errors() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "errors", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    void sequential() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sequential", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void parallel() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "parallel", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @RetryingTest(5)
    void parallelNested() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "parallel-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void eachParallelWithSubflowMissing() throws TimeoutException, QueueException {
        Execution execution =  runnerUtils.runOne(null, "io.kestra.tests", "each-parallel-subflow-notfound");

        assertThat(execution, notNullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        // on JDBC, when using an each parallel, the flow is failed even if not all subtasks of the each parallel are ended as soon as
        // there is one failed task FIXME https://github.com/kestra-io/kestra/issues/2179
        // so instead of asserting that all tasks FAILED we assert that at least two failed (the each parallel and one of its subtasks)
        assertThat(execution.getTaskRunList().stream().filter(taskRun -> taskRun.getState().isFailed()).count(), greaterThanOrEqualTo(2L)); // Should be 3
    }

    @Test
    void eachSequentialNested() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-sequential-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(23));
    }

    @Test
    void eachParallel() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-parallel", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void eachParallelNested() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-parallel-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void restartFailed() throws Exception {
        restartCaseTest.restartFailedThenSuccess();
    }

    @RetryingTest(5)
    void replay() throws Exception {
        restartCaseTest.replay();
    }

    @RetryingTest(5)
    void restartMultiple() throws Exception {
        restartCaseTest.restartMultiple();
    }

    @RetryingTest(5)
    void flowTrigger() throws Exception {
        flowTriggerCaseTest.trigger();
    }

    @Test
    void multipleConditionTrigger() throws Exception {
        multipleConditionTriggerCaseTest.trigger();
    }

    @Test
    void multipleConditionTriggerFailed() throws Exception {
        multipleConditionTriggerCaseTest.failed();
    }

    @RetryingTest(5)
    void eachWithNull() throws Exception {
        EachSequentialTest.eachNullTest(runnerUtils, logsQueue);
    }

    @Test
    void taskDefaults() throws TimeoutException, QueueException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/plugin-defaults.yaml")));
        pluginDefaultsCaseTest.taskDefaults();
    }

    @RetryingTest(5)
    void flowWaitSuccess() throws Exception {
        flowCaseTest.waitSuccess();
    }

    @Test
    void flowWaitFailed() throws Exception {
        flowCaseTest.waitFailed();
    }

    @Test
    public void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs();
    }

    @Test
    public void workerSuccess() throws Exception {
        workingDirectoryTest.success(runnerUtils);
    }

    @Test
    public void workerFailed() throws Exception {
        workingDirectoryTest.failed(runnerUtils);
    }

    @Test
    public void workerEach() throws Exception {
        workingDirectoryTest.each(runnerUtils);
    }

    @RetryingTest(5) // flaky on MySQL
    public void pauseRun() throws Exception {
        pauseTest.run(runnerUtils);
    }

    @Test
    public void pauseRunDelay() throws Exception {
        pauseTest.runDelay(runnerUtils);
    }

    @Test
    public void pauseRunParallelDelay() throws Exception {
        pauseTest.runParallelDelay(runnerUtils);
    }

    @Test
    public void pauseRunTimeout() throws Exception {
        pauseTest.runTimeout(runnerUtils);
    }

    @RetryingTest(5)
    void executionDate() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "execution-start-date", null, null, Duration.ofSeconds(60));

        assertThat((String) execution.getTaskRunList().getFirst().getOutputs().get("value"), matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"));
    }

    @Test
    void skipExecution() throws TimeoutException, QueueException, InterruptedException {
        skipExecutionCaseTest.skipExecution();
    }

    @RetryingTest(5)
    protected void forEachItem() throws URISyntaxException, IOException, InterruptedException, TimeoutException, QueueException {
        forEachItemCaseTest.forEachItem();
    }

    @RetryingTest(5)
    protected void forEachItemEmptyItems() throws URISyntaxException, IOException, TimeoutException, QueueException {
        forEachItemCaseTest.forEachItemEmptyItems();
    }

    @RetryingTest(5)
    protected void forEachItemNoWait() throws URISyntaxException, IOException, InterruptedException, TimeoutException, QueueException {
        forEachItemCaseTest.forEachItemNoWait();
    }

    @RetryingTest(5)
    protected void forEachItemFailed() throws URISyntaxException, IOException, InterruptedException, TimeoutException, QueueException {
        forEachItemCaseTest.forEachItemFailed();
    }

    @RetryingTest(5)
    protected void forEachItemSubflowOutputs() throws URISyntaxException, IOException, InterruptedException, TimeoutException, QueueException {
        forEachItemCaseTest.forEachItemWithSubflowOutputs();
    }

    @Test
    void concurrencyCancel() throws TimeoutException, QueueException, InterruptedException {
        flowConcurrencyCaseTest.flowConcurrencyCancel();
    }

    @Test
    void concurrencyFail() throws TimeoutException, QueueException, InterruptedException  {
        flowConcurrencyCaseTest.flowConcurrencyFail();
    }

    @Test
    void concurrencyQueue() throws TimeoutException, QueueException, InterruptedException  {
        flowConcurrencyCaseTest.flowConcurrencyQueue();
    }

    @Test
    void concurrencyQueuePause() throws TimeoutException, QueueException, InterruptedException  {
        flowConcurrencyCaseTest.flowConcurrencyQueuePause();
    }

    @Test
    void concurrencyCancelPause() throws TimeoutException, QueueException, InterruptedException  {
        flowConcurrencyCaseTest.flowConcurrencyCancelPause();
    }

    @Test
    void badExecutable() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "executable-fail");

        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void dynamicTask() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "dynamic-task");

        assertThat(execution.getTaskRunList().size(), is(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void waitFor() throws TimeoutException, QueueException{
        waitForTestCaseTest.waitfor();
    }

    @Test
    void waitforMaxIterations() throws TimeoutException, QueueException{
        waitForTestCaseTest.waitforMaxIterations();
    }

    @Test
    void waitforMaxDuration() throws TimeoutException, QueueException{
        waitForTestCaseTest.waitforMaxDuration();
    }

    @Test
    void waitforNoSuccess() throws TimeoutException, QueueException{
        waitForTestCaseTest.waitforNoSuccess();
    }

    @Test
    void waitforMultipleTasks() throws TimeoutException, QueueException{
        waitForTestCaseTest.waitforMultipleTasks();
    }

    @Test
    void waitforMultipleTasksFailed() throws TimeoutException, QueueException{
        waitForTestCaseTest.waitforMultipleTasksFailed();
    }

    @Test
    void flowTooLarge() throws TimeoutException, IOException, URISyntaxException, QueueException {
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

        assertThat(execution.getTaskRunList().size(), greaterThanOrEqualTo(6)); // the exact number is test-run-dependent.
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        // To avoid flooding the database with big messages, we re-init it
        init();
    }

    @Test
    void queueMessageTooLarge() {
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
        assertThat(exception.getMessage(), containsString("has exceeded the configured limit of 1048576"));
        assertThat(exception, instanceOf(MessageTooBigException.class));
    }

    @Test
    void workerTaskResultTooLarge() throws TimeoutException, QueueException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logsQueue, either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "workertask-result-too-large"
        );

        LogEntry matchingLog = TestsUtils.awaitLog(logs, log -> log.getMessage().startsWith("Unable to emit the worker task result to the queue"));
        receive.blockLast();

        assertThat(matchingLog, notNullValue());
        assertThat(matchingLog.getLevel(), is(Level.ERROR));
        // the size is different on all runs, so we cannot assert on the exact message size
        assertThat(matchingLog.getMessage(), containsString("Message of size"));
        assertThat(matchingLog.getMessage(), containsString("has exceeded the configured limit of 1048576"));

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().size(), is(1));

    }

    @Test
    void shouldScheduleOnDate() throws QueueException, InterruptedException {
        scheduleDateCaseTest.shouldScheduleOnDate();
    }
}
