package io.kestra.core.runners;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.MessageTooBigException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.flow.EachSequentialTest;
import io.kestra.plugin.core.flow.FlowCaseTest;
import io.kestra.plugin.core.flow.ForEachItemCaseTest;
import io.kestra.plugin.core.flow.PauseTest;
import io.kestra.plugin.core.flow.WaitForCaseTest;
import io.kestra.plugin.core.flow.WorkingDirectoryTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// must be per-class to allow calling once init() which took a lot of time
public abstract class AbstractRunnerTest {

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logsQueue;

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
    protected ForEachItemCaseTest forEachItemCaseTest;

    @Inject
    protected WaitForCaseTest waitForTestCaseTest;

    @Inject
    private FlowConcurrencyCaseTest flowConcurrencyCaseTest;

    @Inject
    private ScheduleDateCaseTest scheduleDateCaseTest;

    @Inject
    protected FlowInputOutput flowIO;

    @Inject
    private SLATestCase slaTestCase;

    @Inject
    private ChangeStateTestCase changeStateTestCase;

    @Test
    @ExecuteFlow("flows/valids/full.yaml")
    void full(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(13));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(
            (String) execution.findTaskRunsByTaskId("t2").getFirst().getOutputs().get("value"),
            containsString("value1"));
    }

    @Test
    @ExecuteFlow("flows/valids/logs.yaml")
    void logs(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(5));
    }

    @Test
    @ExecuteFlow("flows/valids/sequential.yaml")
    void sequential(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    @ExecuteFlow("flows/valids/parallel.yaml")
    void parallel(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @RetryingTest(5)
    @ExecuteFlow("flows/valids/parallel-nested.yaml")
    void parallelNested(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel-subflow-notfound.yml")
    void eachParallelWithSubflowMissing(Execution execution) {
        assertThat(execution, notNullValue());
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        // on JDBC, when using an each parallel, the flow is failed even if not all subtasks of the each parallel are ended as soon as
        // there is one failed task FIXME https://github.com/kestra-io/kestra/issues/2179
        // so instead of asserting that all tasks FAILED we assert that at least two failed (the each parallel and one of its subtasks)
        assertThat(
            execution.getTaskRunList().stream().filter(taskRun -> taskRun.getState().isFailed())
                .count(), greaterThanOrEqualTo(2L)); // Should be 3
    }

    @Test
    @ExecuteFlow("flows/valids/each-sequential-nested.yaml")
    void eachSequentialNested(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(23));
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel.yaml")
    void eachParallel(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel-nested.yaml")
    void eachParallelNested(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    @LoadFlows({"flows/valids/restart_last_failed.yaml"})
    void restartFailed() throws Exception {
        restartCaseTest.restartFailedThenSuccess();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/restart-each.yaml"})
    void replay() throws Exception {
        restartCaseTest.replay();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/failed-first.yaml"})
    void restartMultiple() throws Exception {
        restartCaseTest.restartMultiple();
    }

    @Test
    @LoadFlows({"flows/valids/restart_always_failed.yaml"})
    void restartFailedThenFailureWithGlobalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithGlobalErrors();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/restart_local_errors.yaml"})
    void restartFailedThenFailureWithLocalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithLocalErrors();
    }

    @Test
    @LoadFlows({"flows/valids/restart-parent.yaml", "flows/valids/restart-child.yaml"})
    void restartSubflow() throws Exception {
        restartCaseTest.restartSubflow();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/trigger-flow-listener-no-inputs.yaml",
        "flows/valids/trigger-flow-listener.yaml",
        "flows/valids/trigger-flow-listener-namespace-condition.yaml",
        "flows/valids/trigger-flow.yaml"})
    void flowTrigger() throws Exception {
        flowTriggerCaseTest.trigger();
    }

    @Test
    @LoadFlows({"flows/valids/trigger-flow-listener-with-pause.yaml",
        "flows/valids/trigger-flow-with-pause.yaml"})
    void flowTriggerWithPause() throws Exception {
        flowTriggerCaseTest.triggerWithPause();
    }

    @Test
    @LoadFlows({"flows/valids/trigger-multiplecondition-listener.yaml",
        "flows/valids/trigger-multiplecondition-flow-a.yaml",
        "flows/valids/trigger-multiplecondition-flow-b.yaml"})
    void multipleConditionTrigger() throws Exception {
        multipleConditionTriggerCaseTest.trigger();
    }

    @RetryingTest(5) // Flaky on CI but never locally even with 100 repetitions
    @LoadFlows({"flows/valids/trigger-flow-listener-namespace-condition.yaml",
        "flows/valids/trigger-multiplecondition-flow-c.yaml",
        "flows/valids/trigger-multiplecondition-flow-d.yaml"})
    void multipleConditionTriggerFailed() throws Exception {
            multipleConditionTriggerCaseTest.failed();
    }

    @Test
    @LoadFlows({"flows/valids/flow-trigger-preconditions-flow-listen.yaml",
        "flows/valids/flow-trigger-preconditions-flow-a.yaml",
        "flows/valids/flow-trigger-preconditions-flow-b.yaml"})
    void flowTriggerPreconditions() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerPreconditions();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/each-null.yaml"})
    void eachWithNull() throws Exception {
        EachSequentialTest.eachNullTest(runnerUtils,logsQueue);
    }

    @Test
    @LoadFlows({"flows/tests/plugin-defaults.yaml"})
    void taskDefaults() throws Exception {
        pluginDefaultsCaseTest.taskDefaults();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/switch.yaml",
        "flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml"})
    void flowWaitSuccess() throws Exception {
        flowCaseTest.waitSuccess();
    }

    @Test
    @LoadFlows({"flows/valids/switch.yaml",
        "flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml"})
    void flowWaitFailed() throws Exception {
        flowCaseTest.waitFailed();
    }

    @Test
    @LoadFlows({"flows/valids/switch.yaml",
        "flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml"})
    public void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs();
    }

    @Test
    @LoadFlows({"flows/valids/working-directory.yaml"})
    public void workerSuccess() throws Exception {
        workingDirectoryTest.success(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory.yaml"})
    public void workerFailed() throws Exception {
        workingDirectoryTest.failed(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-each.yaml"})
    public void workerEach() throws Exception {
        workingDirectoryTest.each(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-cache.yml"})
    public void workingDirectoryCache() throws Exception {
        workingDirectoryTest.cache(runnerUtils);
    }

    @RetryingTest(5) // flaky on MySQL
    @LoadFlows({"flows/valids/pause.yaml"})
    public void pauseRun() throws Exception {
        pauseTest.run(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause-delay.yaml"})
    public void pauseRunDelay() throws Exception {
        pauseTest.runDelay(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause-delay-from-input.yaml"})
    public void pauseRunDelayFromInput() throws Exception {
        pauseTest.runDelayFromInput(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/each-parallel-pause.yml"})
    public void pauseRunParallelDelay() throws Exception {
        pauseTest.runParallelDelay(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause-timeout.yaml"})
    public void pauseRunTimeout() throws Exception {
        pauseTest.runTimeout(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void skipExecution() throws Exception {
        skipExecutionCaseTest.skipExecution();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/for-each-item-subflow.yaml",
        "flows/valids/for-each-item.yaml"})
    protected void forEachItem() throws Exception {
        forEachItemCaseTest.forEachItem();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/for-each-item.yaml"})
    protected void forEachItemEmptyItems() throws Exception {
        forEachItemCaseTest.forEachItemEmptyItems();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/for-each-item-subflow-failed.yaml",
        "flows/valids/for-each-item-failed.yaml"})
    protected void forEachItemFailed() throws Exception {
        forEachItemCaseTest.forEachItemFailed();
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/for-each-item-outputs-subflow.yaml",
        "flows/valids/for-each-item-outputs.yaml"})
    protected void forEachItemSubflowOutputs() throws Exception {
        forEachItemCaseTest.forEachItemWithSubflowOutputs();
    }

    @Test
    @LoadFlows({"flows/valids/restart-for-each-item.yaml", "flows/valids/restart-child.yaml"})
    void restartForEachItem() throws Exception {
        forEachItemCaseTest.restartForEachItem();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-cancel.yml"})
    void concurrencyCancel() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyCancel();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-fail.yml"})
    void concurrencyFail() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyFail();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml"})
    void concurrencyQueue() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueue();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue-pause.yml"})
    void concurrencyQueuePause() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyQueuePause();
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-cancel-pause.yml"})
    void concurrencyCancelPause() throws Exception {
        flowConcurrencyCaseTest.flowConcurrencyCancelPause();
    }

    @Test
    @ExecuteFlow("flows/valids/executable-fail.yml")
    void badExecutable(Execution execution) {
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    @ExecuteFlow("flows/valids/dynamic-task.yaml")
    void dynamicTask(Execution execution) {
        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/waitfor.yaml"})
    void waitFor() throws Exception {
        waitForTestCaseTest.waitfor();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-max-iterations.yaml"})
    void waitforMaxIterations() throws Exception {
        waitForTestCaseTest.waitforMaxIterations();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-max-duration.yaml"})
    void waitforMaxDuration() throws Exception {
        waitForTestCaseTest.waitforMaxDuration();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-no-success.yaml"})
    void waitforNoSuccess() throws Exception {
        waitForTestCaseTest.waitforNoSuccess();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-multiple-tasks.yaml"})
    void waitforMultipleTasks() throws Exception {
        waitForTestCaseTest.waitforMultipleTasks();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-multiple-tasks-failed.yaml"})
    void waitforMultipleTasksFailed() throws Exception {
        waitForTestCaseTest.waitforMultipleTasksFailed();
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldScheduleOnDate() throws Exception {
        scheduleDateCaseTest.shouldScheduleOnDate();
    }

    @Test
    @LoadFlows({"flows/valids/sla-max-duration-fail.yaml"})
    void maxDurationSLAShouldFail() throws Exception {
        slaTestCase.maxDurationSLAShouldFail();
    }

    @Test
    @LoadFlows({"flows/valids/sla-max-duration-ok.yaml"})
    void maxDurationSLAShouldPass() throws Exception {
        slaTestCase.maxDurationSLAShouldPass();
    }

    @Test
    @LoadFlows({"flows/valids/sla-execution-condition.yaml"})
    void executionConditionSLAShouldPass() throws Exception {
        slaTestCase.executionConditionSLAShouldPass();
    }

    @Test
    @LoadFlows({"flows/valids/sla-execution-condition.yaml"})
    void executionConditionSLAShouldCancel() throws Exception {
        slaTestCase.executionConditionSLAShouldCancel();
    }

    @Test
    @LoadFlows({"flows/valids/sla-execution-condition.yaml"})
    void executionConditionSLAShouldLabel() throws Exception {
        slaTestCase.executionConditionSLAShouldLabel();
    }

    @Test
    @LoadFlows({"flows/valids/if.yaml"})
    void multipleIf() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "if", null,
            (f, e) -> Map.of("if1", true, "if2", false, "if3", true));

        assertThat(execution.getTaskRunList(), hasSize(12));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("flows/valids/failed-first.yaml")
    public void changeStateShouldEndsInSuccess(Execution execution) throws Exception {
        changeStateTestCase.changeStateShouldEndsInSuccess(execution);
    }

    @Test
    @LoadFlows({"flows/valids/failed-first.yaml", "flows/valids/subflow-parent-of-failed.yaml"})
    public void changeStateInSubflowShouldEndsParentFlowInSuccess() throws Exception {
        changeStateTestCase.changeStateInSubflowShouldEndsParentFlowInSuccess();
    }
}
