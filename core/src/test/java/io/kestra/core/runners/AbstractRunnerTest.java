package io.kestra.core.runners;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.plugin.core.flow.*;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
// must be per-class to allow calling once init() which took a lot of time
public abstract class AbstractRunnerTest {

    public static final String TENANT_1 = "tenant1";
    public static final String TENANT_2 = "tenant2";
    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logsQueue;

    @Inject
    protected RestartCaseTest restartCaseTest;

    @Inject
    protected FlowTriggerCaseTest flowTriggerCaseTest;

    @Inject
    protected MultipleConditionTriggerCaseTest multipleConditionTriggerCaseTest;

    @Inject
    private PluginDefaultsCaseTest pluginDefaultsCaseTest;

    @Inject
    protected FlowCaseTest flowCaseTest;

    @Inject
    private WorkingDirectoryTest.Suite workingDirectoryTest;

    @Inject
    protected PauseTest.Suite pauseTest;

    @Inject
    private SkipExecutionCaseTest skipExecutionCaseTest;

    @Inject
    protected ForEachItemCaseTest forEachItemCaseTest;

    @Inject
    protected LoopUntilCaseTest loopUntilTestCaseTest;

    @Inject
    protected ScheduleDateCaseTest scheduleDateCaseTest;

    @Inject
    protected FlowInputOutput flowIO;

    @Inject
    private SLATestCase slaTestCase;

    @Inject
    protected ChangeStateTestCase changeStateTestCase;

    @Inject
    private AfterExecutionTestCase afterExecutionTestCase;

    @Test
    @ExecuteFlow("flows/valids/full.yaml")
    void full(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(13);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat((String) execution.findTaskRunsByTaskId("t2").getFirst().getOutputs().get("value")).contains("value1");
    }

    @Test
    @ExecuteFlow("flows/valids/logs.yaml")
    void logs(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(5);
    }

    @Test
    @ExecuteFlow("flows/valids/sequential.yaml")
    void sequential(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(11);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel.yaml")
    void parallel(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(8);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-nested.yaml")
    void parallelNested(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(11);
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel-subflow-notfound.yml")
    void eachParallelWithSubflowMissing(Execution execution) {
        assertThat(execution).isNotNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        // on JDBC, when using an each parallel, the flow is failed even if not all subtasks of the each parallel are ended as soon as
        // there is one failed task FIXME https://github.com/kestra-io/kestra/issues/2179
        // so instead of asserting that all tasks FAILED we assert that at least two failed (the each parallel and one of its subtasks)
        assertThat(execution.getTaskRunList().stream().filter(taskRun -> taskRun.getState().isFailed())
            .count()).isGreaterThanOrEqualTo(2L); // Should be 3
    }

    @Test
    @ExecuteFlow("flows/valids/each-sequential-nested.yaml")
    void eachSequentialNested(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(23);
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel.yaml")
    void eachParallel(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(8);
    }

    @Test
    @ExecuteFlow("flows/valids/each-parallel-nested.yaml")
    void eachParallelNested(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(11);
    }

    @Test
    @LoadFlows({"flows/valids/restart_last_failed.yaml"})
    void restartFailed() throws Exception {
        restartCaseTest.restartFailedThenSuccess();
    }

    @Test
    @LoadFlows({"flows/valids/restart-each.yaml"})
    void replay() throws Exception {
        restartCaseTest.replay();
    }

    @Test
    @LoadFlows({"flows/valids/failed-first.yaml"})
    void restartMultiple() throws Exception {
        restartCaseTest.restartMultiple();
    }

    @Test
    @LoadFlows({"flows/valids/restart_always_failed.yaml"})
    void restartFailedThenFailureWithGlobalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithGlobalErrors();
    }

    @Test
    @LoadFlows({"flows/valids/restart_local_errors.yaml"})
    protected void restartFailedThenFailureWithLocalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithLocalErrors();
    }

    @Test
    @LoadFlows({"flows/valids/restart-parent.yaml", "flows/valids/restart-child.yaml"})
    protected void restartSubflow() throws Exception {
        restartCaseTest.restartSubflow();
    }

    @Test
    @LoadFlows({"flows/valids/restart-with-finally.yaml"})
    protected void restartFailedWithFinally() throws Exception {
        restartCaseTest.restartFailedWithFinally();
    }

    @Test
    @LoadFlows({"flows/valids/restart-with-after-execution.yaml"})
    protected void restartFailedWithAfterExecution() throws Exception {
        restartCaseTest.restartFailedWithAfterExecution();
    }

    @Test
    @LoadFlows(value = {"flows/valids/trigger-flow-listener-no-inputs.yaml",
        "flows/valids/trigger-flow-listener.yaml",
        "flows/valids/trigger-flow-listener-namespace-condition.yaml",
        "flows/valids/trigger-flow.yaml"}, tenantId = "listener-tenant")
    void flowTrigger() throws Exception {
        flowTriggerCaseTest.trigger("listener-tenant");
    }

    @Test // flaky on CI but never fail locally
    @LoadFlows({"flows/valids/trigger-flow-listener-with-pause.yaml",
        "flows/valids/trigger-flow-with-pause.yaml"})
    void flowTriggerWithPause() throws Exception {
        flowTriggerCaseTest.triggerWithPause();
    }

    @Test
    @LoadFlows(value = {"flows/valids/trigger-flow-listener-with-concurrency-limit.yaml",
        "flows/valids/trigger-flow-with-concurrency-limit.yaml"}, tenantId = "trigger-tenant")
    protected void flowTriggerWithConcurrencyLimit() throws Exception {
        flowTriggerCaseTest.triggerWithConcurrencyLimit("trigger-tenant");
    }

    @Test
    @LoadFlows({"flows/valids/trigger-multiplecondition-listener.yaml",
        "flows/valids/trigger-multiplecondition-flow-a.yaml",
        "flows/valids/trigger-multiplecondition-flow-b.yaml"})
    void multipleConditionTrigger() throws Exception {
        multipleConditionTriggerCaseTest.trigger();
    }

    @Test // Flaky on CI but never locally even with 100 repetitions
    @LoadFlows(value = {"flows/valids/trigger-flow-listener-namespace-condition.yaml",
        "flows/valids/trigger-multiplecondition-flow-c.yaml",
        "flows/valids/trigger-multiplecondition-flow-d.yaml"}, tenantId = "condition-tenant")
    void multipleConditionTriggerFailed() throws Exception {
        multipleConditionTriggerCaseTest.failed("condition-tenant");
    }

    @Test
    @LoadFlows({"flows/valids/flow-trigger-preconditions-flow-listen.yaml",
        "flows/valids/flow-trigger-preconditions-flow-a.yaml",
        "flows/valids/flow-trigger-preconditions-flow-b.yaml"})
    void flowTriggerPreconditions() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerPreconditions();
    }

    @Test
    @LoadFlows(value = {"flows/valids/flow-trigger-preconditions-flow-listen.yaml",
        "flows/valids/flow-trigger-preconditions-flow-a.yaml",
        "flows/valids/flow-trigger-preconditions-flow-b.yaml"}, tenantId = TENANT_1)
    void flowTriggerPreconditionsMergeOutputs() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerPreconditionsMergeOutputs(TENANT_1);
    }

    @Test
    @LoadFlows({"flows/valids/flow-trigger-paused-listen.yaml", "flows/valids/flow-trigger-paused-flow.yaml"})
    void flowTriggerOnPaused() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerOnPaused();
    }

    @Test
    @LoadFlows({"flows/valids/flow-trigger-for-each-item-parent.yaml", "flows/valids/flow-trigger-for-each-item-child.yaml", "flows/valids/flow-trigger-for-each-item-grandchild.yaml"})
    void forEachItemWithFlowTrigger() throws Exception {
        multipleConditionTriggerCaseTest.forEachItemWithFlowTrigger();
    }

    @Test
    @LoadFlows({"flows/valids/flow-trigger-multiple-preconditions-flow-a.yaml", "flows/valids/flow-trigger-multiple-preconditions-flow-listen.yaml"})
    void flowTriggerMultiplePreconditions() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerMultiplePreconditions();
    }

    @Test
    @LoadFlows({"flows/valids/flow-trigger-multiple-conditions-flow-a.yaml", "flows/valids/flow-trigger-multiple-conditions-flow-listen.yaml"})
    void flowTriggerMultipleConditions() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerMultipleConditions();
    }

    @Test
    @LoadFlows({"flows/valids/flow-trigger-mixed-conditions-flow-a.yaml", "flows/valids/flow-trigger-mixed-conditions-flow-listen.yaml"})
    void flowTriggerMixedConditions() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerMixedConditions();
    }

    @Test
    @LoadFlows({"flows/valids/each-null.yaml"})
    void eachWithNull() throws Exception {
        EachSequentialTest.eachNullTest(runnerUtils, logsQueue);
    }

    @Test
    @LoadFlows({"flows/tests/plugin-defaults.yaml"})
    void taskDefaults() throws Exception {
        pluginDefaultsCaseTest.taskDefaults();
    }

    @Test
    @LoadFlows({"flows/valids/switch.yaml",
        "flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml"})
    protected void flowWaitSuccess() throws Exception {
        flowCaseTest.waitSuccess();
    }

    @Test
    @LoadFlows(value = {"flows/valids/switch.yaml",
        "flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml"}, tenantId = TENANT_1)
    public void flowWaitFailed() throws Exception {
        flowCaseTest.waitFailed(TENANT_1);
    }

    @Test
    @LoadFlows(value = {"flows/valids/switch.yaml",
        "flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml"}, tenantId = TENANT_2)
    public void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs(TENANT_2);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory.yaml"})
    public void workerSuccess() throws Exception {
        workingDirectoryTest.success(runnerUtils);
    }

    @Test
    @LoadFlows(value = {"flows/valids/working-directory.yaml"}, tenantId = TENANT_1)
    public void workerFailed() throws Exception {
        workingDirectoryTest.failed(TENANT_1, runnerUtils);
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

    @Test // flaky on MySQL
    @LoadFlows({"flows/valids/pause-test.yaml"})
    public void pauseRun() throws Exception {
        pauseTest.run(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause-delay.yaml"})
    public void pauseRunDelay() throws Exception {
        pauseTest.runDelay(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause-duration-from-input.yaml"})
    public void pauseRunDurationFromInput() throws Exception {
        pauseTest.runDurationFromInput(runnerUtils);
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

    @Test
    @LoadFlows({"flows/valids/for-each-item-subflow.yaml",
        "flows/valids/for-each-item.yaml"})
    protected void forEachItem() throws Exception {
        forEachItemCaseTest.forEachItem();
    }

    @Test
    @LoadFlows(value = {"flows/valids/for-each-item.yaml"}, tenantId = TENANT_1)
    protected void forEachItemEmptyItems() throws Exception {
        forEachItemCaseTest.forEachItemEmptyItems(TENANT_1);
    }

    @Test
    @LoadFlows({"flows/valids/for-each-item-subflow-failed.yaml",
        "flows/valids/for-each-item-failed.yaml"})
    protected void forEachItemFailed() throws Exception {
        forEachItemCaseTest.forEachItemFailed();
    }

    @Test
    @LoadFlows({"flows/valids/for-each-item-outputs-subflow.yaml",
        "flows/valids/for-each-item-outputs.yaml"})
    protected void forEachItemSubflowOutputs() throws Exception {
        forEachItemCaseTest.forEachItemWithSubflowOutputs();
    }

    @Test // flaky on CI but always pass locally even with 100 iterations
    @LoadFlows(value = {"flows/valids/restart-for-each-item.yaml", "flows/valids/restart-child.yaml"}, tenantId = TENANT_1)
    void restartForEachItem() throws Exception {
        forEachItemCaseTest.restartForEachItem(TENANT_1);
    }

    @Test
    @LoadFlows(value = {"flows/valids/for-each-item-subflow.yaml",
        "flows/valids/for-each-item-in-if.yaml"}, tenantId = TENANT_1)
    protected void forEachItemInIf() throws Exception {
        forEachItemCaseTest.forEachItemInIf(TENANT_1);
    }

    @Test
    @LoadFlows({"flows/valids/for-each-item-subflow-after-execution.yaml",
        "flows/valids/for-each-item-after-execution.yaml"})
    protected void forEachItemWithAfterExecution() throws Exception {
        forEachItemCaseTest.forEachItemWithAfterExecution();
    }

    @Test
    @ExecuteFlow("flows/valids/executable-fail.yml")
    void badExecutable(Execution execution) {
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow("flows/valids/dynamic-task.yaml")
    void dynamicTask(Execution execution) {
        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/waitfor.yaml"})
    void waitFor() throws Exception {
        loopUntilTestCaseTest.waitfor();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-max-iterations.yaml"})
    void waitforMaxIterations() throws Exception {
        loopUntilTestCaseTest.waitforMaxIterations();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-max-duration.yaml"})
    void waitforMaxDuration() throws Exception {
        loopUntilTestCaseTest.waitforMaxDuration();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-no-success.yaml"})
    void waitforNoSuccess() throws Exception {
        loopUntilTestCaseTest.waitforNoSuccess();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-multiple-tasks.yaml"})
    void waitforMultipleTasks() throws Exception {
        loopUntilTestCaseTest.waitforMultipleTasks();
    }

    @Test
    @LoadFlows({"flows/valids/waitfor-multiple-tasks-failed.yaml"})
    void waitforMultipleTasksFailed() throws Exception {
        loopUntilTestCaseTest.waitforMultipleTasksFailed();
    }

    @Test
    @LoadFlows(value = {"flows/valids/minimal.yaml"}, tenantId = TENANT_1)
    void shouldScheduleOnDate() throws Exception {
        scheduleDateCaseTest.shouldScheduleOnDate(TENANT_1);
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
    @LoadFlows(value = {"flows/valids/sla-execution-condition.yaml"}, tenantId = TENANT_1)
    void executionConditionSLAShouldCancel() throws Exception {
        slaTestCase.executionConditionSLAShouldCancel(TENANT_1);
    }

    @Test
    @LoadFlows(value = {"flows/valids/sla-execution-condition.yaml"}, tenantId = TENANT_2)
    void executionConditionSLAShouldLabel() throws Exception {
        slaTestCase.executionConditionSLAShouldLabel(TENANT_2);
    }

    @Test
    @LoadFlows({"flows/valids/sla-parent-flow.yaml", "flows/valids/sla-subflow.yaml"})
    void slaViolationOnSubflowMayEndTheParentFlow() throws Exception {
        slaTestCase.slaViolationOnSubflowMayEndTheParentFlow();
    }

    @Test
    @LoadFlows({"flows/valids/if.yaml"})
    void multipleIf() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "if", null,
            (f, e) -> Map.of("if1", true, "if2", false, "if3", true));

        assertThat(execution.getTaskRunList()).hasSize(12);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/failed-first.yaml", tenantId = TENANT_1)
    public void changeStateShouldEndsInSuccess(Execution execution) throws Exception {
        changeStateTestCase.changeStateShouldEndsInSuccess(execution);
    }

    @Test
    @LoadFlows(value = {"flows/valids/failed-first.yaml", "flows/valids/subflow-parent-of-failed.yaml"}, tenantId = TENANT_2)
    public void changeStateInSubflowShouldEndsParentFlowInSuccess() throws Exception {
        changeStateTestCase.changeStateInSubflowShouldEndsParentFlowInSuccess(TENANT_2);
    }

    @Test
    @ExecuteFlow("flows/valids/after-execution.yaml")
    public void shouldCallTasksAfterExecution(Execution execution) {
        afterExecutionTestCase.shouldCallTasksAfterExecution(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/after-execution-finally.yaml")
    public void shouldCallTasksAfterFinally(Execution execution) {
        afterExecutionTestCase.shouldCallTasksAfterFinally(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/after-execution-error.yaml")
    public void shouldCallTasksAfterError(Execution execution) {
        afterExecutionTestCase.shouldCallTasksAfterError(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/after-execution-listener.yaml")
    public void shouldCallTasksAfterListener(Execution execution) {
        afterExecutionTestCase.shouldCallTasksAfterListener(execution);
    }
}
