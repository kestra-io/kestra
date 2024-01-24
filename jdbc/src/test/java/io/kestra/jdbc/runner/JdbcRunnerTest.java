package io.kestra.jdbc.runner;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.*;
import io.kestra.core.tasks.flows.EachSequentialTest;
import io.kestra.core.tasks.flows.FlowCaseTest;
import io.kestra.core.tasks.flows.ForEachItemCaseTest;
import io.kestra.core.tasks.flows.PauseTest;
import io.kestra.core.tasks.flows.WorkingDirectoryTest;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.RetryingTest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
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
    private TaskDefaultsCaseTest taskDefaultsCaseTest;

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
    private FlowConcurrencyCaseTest flowConcurrencyCaseTest;

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(repositoryLoader);
        runner.setSchedulerEnabled(false);
        runner.run();
    }

    @Test
    void full() throws TimeoutException, QueueException, InternalException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "full", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(13));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat((String) execution.findTaskRunsByTaskId("t2").get(0).getOutputs().get("value"), containsString("value1"));
    }

    @Test
    void logs() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "logs", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(4));
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
    void eachParallelWithSubflowMissing() throws TimeoutException {
        Execution execution =  runnerUtils.runOne(null, "io.kestra.tests", "each-parallel-subflow-notfound");

        assertThat(execution, notNullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        // on JDBC, when using an each parallel, the flow is failed even if not all subtasks of the each parallel are ended as soon as
        // there is one failed task FIXME https://github.com/kestra-io/kestra/issues/2179
        // so instead of asserting that all tasks FAILED we assert that at least two failed (the each parallel and one of its subtasks)
        assertThat(execution.getTaskRunList().stream().filter(taskRun -> taskRun.getState().isFailed()).count(), greaterThanOrEqualTo(2L)); // Should be 3
    }

    @Test
    void eachSequentialNested() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-sequential-nested", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(23));
    }

    @Test
    void eachParallel() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-parallel", null, null, Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void eachParallelNested() throws TimeoutException {
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

    @RetryingTest(5)
    void eachWithNull() throws Exception {
        EachSequentialTest.eachNullTest(runnerUtils, logsQueue);
    }

    @Test
    void taskDefaults() throws TimeoutException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/task-defaults.yaml")));
        taskDefaultsCaseTest.taskDefaults();
    }

    @Test
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
    void executionDate() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "execution-start-date", null, null, Duration.ofSeconds(60));

        assertThat((String) execution.getTaskRunList().get(0).getOutputs().get("value"), matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"));
    }

    @Test
    void skipExecution() throws TimeoutException, InterruptedException {
        skipExecutionCaseTest.skipExecution();
    }

    @RetryingTest(5)
    void forEachItem() throws URISyntaxException, IOException, InterruptedException, TimeoutException {
        forEachItemCaseTest.forEachItem();
    }

    @Test
    void forEachItemNoWait() throws URISyntaxException, IOException, InterruptedException, TimeoutException {
        forEachItemCaseTest.forEachItemNoWait();
    }

    @Test
    void forEachItemFailed() throws URISyntaxException, IOException, InterruptedException, TimeoutException {
        forEachItemCaseTest.forEachItemFailed();
    }

    @Test
    void concurrencyCancel() throws TimeoutException, InterruptedException {
        flowConcurrencyCaseTest.flowConcurrencyCancel();
    }

    @Test
    void concurrencyFail() throws TimeoutException, InterruptedException  {
        flowConcurrencyCaseTest.flowConcurrencyFail();
    }

    @Test
    void concurrencyQueue() throws TimeoutException, InterruptedException  {
        flowConcurrencyCaseTest.flowConcurrencyQueue();
    }

    @Test
    void concurrencyQueuePause() throws TimeoutException, InterruptedException  {
        flowConcurrencyCaseTest.flowConcurrencyQueuePause();
    }

    @Test
    void concurrencyCancelPause() throws TimeoutException, InterruptedException  {
        flowConcurrencyCaseTest.flowConcurrencyCancelPause();
    }

    @Test
    void badExecutable() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "executable-fail");

        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
