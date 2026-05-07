package io.kestra.core.runners;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.flow.*;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
// must be per-class to allow calling once init() which took a lot of time
public abstract class AbstractRunnerTest {
    public static final String NAMESPACE = "io.kestra.tests";
    public static final String TENANT_1 = "tenant1";
    public static final String TENANT_2 = "tenant2";
    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    protected DispatchQueueInterface<LogEntry> logsQueue;

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
    private IgnoreExecutionCaseTest ignoreExecutionCaseTest;

    @Inject
    protected LoopUntilCaseTest loopUntilTestCaseTest;

    @Inject
    protected LoopCaseTest loopCaseTest;

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

    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    protected DispatchQueueInterface<Execution> executionQueue;

    @Inject
    protected DispatchQueueInterface<ExecutionEvent> executionEventQueue;

    @Test
    @ExecuteFlow("flows/valids/full.yaml")
    void full(Execution execution) throws Exception {
        assertThat(execution.getTaskRunList()).hasSize(13);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat((String) taskOutputService.getOutputs(execution.findTaskRunsByTaskId("t2").getFirst()).get("value")).contains("value1");
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
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void restartFailed() throws Exception {
        restartCaseTest.restartFailedThenSuccess();
    }

    @Test
    @LoadFlows({ "flows/valids/replay.yaml" })
    void replay() throws Exception {
        restartCaseTest.replay();
    }

    @Test
    @LoadFlows({ "flows/valids/replay.yaml" })
    void replayFromTaskId() throws Exception {
        restartCaseTest.replayFromTaskId();
    }

    @Test
    @LoadFlows({"flows/valids/restart-loop.yaml"})
    void replayLoop() throws Exception {
        restartCaseTest.replayLoop();
    }

    @Test
    @LoadFlows({ "flows/valids/failed-first.yaml" })
    void restartMultiple() throws Exception {
        restartCaseTest.restartMultiple();
    }

    @Test
    @LoadFlows({ "flows/valids/restart_always_failed.yaml" })
    void restartFailedThenFailureWithGlobalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithGlobalErrors();
    }

    @Test
    @LoadFlows({ "flows/valids/restart_local_errors.yaml" })
    protected void restartFailedThenFailureWithLocalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithLocalErrors();
    }

    @Test
    @LoadFlows({"flows/valids/restart-parent-loop.yaml", "flows/valids/restart-child.yaml" })
    @Disabled("This is not implemented yet for loops")
    protected void restartSubflowWithLoop() throws Exception {
        restartCaseTest.restartSubflowWithLoop();
    }

    @Test
    @LoadFlows({ "flows/valids/restart-parent.yaml", "flows/valids/restart-child.yaml" })
    protected void restartSubflow() throws Exception {
        restartCaseTest.restartSubflow();
    }

    @Test
    @LoadFlows({ "flows/valids/restart-with-finally.yaml" })
    protected void restartFailedWithFinally() throws Exception {
        restartCaseTest.restartFailedWithFinally();
    }

    @Test
    @LoadFlows({ "flows/valids/restart-with-after-execution.yaml" })
    protected void restartFailedWithAfterExecution() throws Exception {
        restartCaseTest.restartFailedWithAfterExecution();
    }

    @Test
    @LoadFlows({ "flows/valids/loop-until-restart.yaml" })
    protected void restartOrReplayLoopUntil() throws Exception {
        restartCaseTest.restartOrReplayLoopUntil();
    }

    @Test
    @LoadFlows(
        value = { "flows/valids/trigger-flow-listener-no-inputs.yaml",
            "flows/valids/trigger-flow-listener.yaml",
            "flows/valids/trigger-flow-listener-namespace-condition.yaml",
            "flows/valids/trigger-flow.yaml" },
        tenantId = "listener-tenant"
    )
    void flowTrigger() throws Exception {
        flowTriggerCaseTest.trigger("listener-tenant");
    }

    @Test // flaky on CI but never fail locally
    @LoadFlows(
        { "flows/valids/trigger-flow-listener-with-pause.yaml",
            "flows/valids/trigger-flow-with-pause.yaml" }
    )
    void flowTriggerWithPause() throws Exception {
        flowTriggerCaseTest.triggerWithPause();
    }

    @Test
    @LoadFlows(
        value = { "flows/valids/trigger-flow-listener-with-concurrency-limit.yaml",
            "flows/valids/trigger-flow-with-concurrency-limit.yaml" },
        tenantId = "trigger-tenant"
    )
    protected void flowTriggerWithConcurrencyLimit() throws Exception {
        flowTriggerCaseTest.triggerWithConcurrencyLimit("trigger-tenant");
    }

    @Test
    @LoadFlows(
        { "flows/valids/flow-trigger-stable-condition-id-flow-listen.yaml",
            "flows/valids/flow-trigger-stable-condition-id-flow-a.yaml",
            "flows/valids/flow-trigger-stable-condition-id-flow-b.yaml" }
    )
    void flowTriggerDependsOnWithStableConditionId() throws Exception {
        flowTriggerCaseTest.triggerDependsOnWithStableConditionId();
    }

    @Test
    @LoadFlows({ "flows/valids/flow-trigger-paused-listen.yaml", "flows/valids/flow-trigger-paused-flow.yaml" })
    void flowTriggerOnPaused() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerOnPaused();
    }

    @Test
    @LoadFlows(
        { "flows/valids/flow-trigger-depends-on-flow-listen.yaml",
            "flows/valids/flow-trigger-depends-on-flow-a.yaml",
            "flows/valids/flow-trigger-depends-on-flow-b.yaml" }
    )
    void flowTriggerDependsOn() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerDependsOn();
    }

    @Test
    @LoadFlows({ "flows/valids/flow-trigger-multiple-depends-on-flow-a.yaml", "flows/valids/flow-trigger-fire-once-true-flow-b.yaml", "flows/valids/flow-trigger-multiple-depends-on-flow-listen.yaml" })
    void flowTriggerMultipleDependsOn() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerMultipleDependsOn();
    }

    @Test
    @LoadFlows({"flows/valids/flow-trigger-fire-once-true-flow-a.yaml", "flows/valids/flow-trigger-fire-once-true-flow-b.yaml", "flows/valids/flow-trigger-fire-once-true-flow-listen.yaml"})
    void flowTriggerDependsOnFireOnceTrue() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerDependsOnFireOnceTrue();
    }

    @Test
    @LoadFlows({ "flows/valids/flow-trigger-multiple-conditions-flow-a.yaml", "flows/valids/flow-trigger-multiple-conditions-flow-listen.yaml" })
    void flowTriggerMultipleConditions() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerMultipleConditions();
    }

    @Test
    @LoadFlows({ "flows/valids/flow-trigger-when-condition-flow-a.yaml", "flows/valids/flow-trigger-when-condition-flow-listen.yaml" })
    void flowTriggerWhenCondition() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerWhenCondition();
    }

    @Test
    @LoadFlows({ "flows/valids/flow-trigger-mixed-conditions-flow-a.yaml", "flows/valids/flow-trigger-mixed-conditions-flow-listen.yaml" })
    void flowTriggerMixedConditions() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerMixedConditions();
    }

    @Test
    @LoadFlows({
        "flows/valids/flow-trigger-any-mode-flow-a.yaml",
        "flows/valids/flow-trigger-any-mode-flow-b.yaml",
        "flows/valids/flow-trigger-any-mode-flow-listen.yaml"
    })
    void flowTriggerAnyMode() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerAnyMode();
    }

    @Test
    @LoadFlows({
        "flows/valids/flow-trigger-at-least-mode-flow-a.yaml",
        "flows/valids/flow-trigger-at-least-mode-flow-b.yaml",
        "flows/valids/flow-trigger-at-least-mode-flow-c.yaml",
        "flows/valids/flow-trigger-at-least-mode-flow-listen.yaml"
    })
    void flowTriggerAtLeastMode() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerAtLeastMode();
    }

    @Test
    @LoadFlows({
        "flows/valids/flow-trigger-invalid-inputs-flow-a.yaml",
        "flows/valids/flow-trigger-invalid-inputs-flow-listen.yaml"
    })
    void flowTriggerWithInvalidInputs() throws Exception {
        multipleConditionTriggerCaseTest.flowTriggerWithInvalidInputs();
    }

    @Test
    @LoadFlows({ "flows/tests/plugin-defaults.yaml" })
    void taskDefaults() throws Exception {
        pluginDefaultsCaseTest.pluginDefaults();
    }

    @Test
    @LoadFlows(
        value = { "flows/valids/switch.yaml",
            "flows/valids/task-flow.yaml",
            "flows/valids/task-flow-inherited-labels.yaml" },
        tenantId = "flowwaitsuccess"
    )
    protected void flowWaitSuccess() throws Exception {
        flowCaseTest.waitSuccess("flowwaitsuccess");
    }

    @Test
    @LoadFlows(
        value = { "flows/valids/switch.yaml",
            "flows/valids/task-flow.yaml",
            "flows/valids/task-flow-inherited-labels.yaml" },
        tenantId = TENANT_1
    )
    public void flowWaitFailed() throws Exception {
        flowCaseTest.waitFailed(TENANT_1);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory.yaml" })
    public void workingDirectorySuccess() throws Exception {
        workingDirectoryTest.success(runnerUtils);
    }

    @Test
    @LoadFlows(value = { "flows/valids/working-directory.yaml" }, tenantId = TENANT_1)
    public void workingDirectoryFailed() throws Exception {
        workingDirectoryTest.failed(TENANT_1, runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-loop.yaml"})
    public void workingDirectoryLoop() throws Exception {
        workingDirectoryTest.loop(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-cache.yml" })
    public void workingDirectoryCache() throws Exception {
        workingDirectoryTest.cache(runnerUtils);
    }

    @Test // flaky on MySQL
    @LoadFlows({ "flows/valids/pause-test.yaml" })
    public void pauseRun() throws Exception {
        pauseTest.run(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/pause-delay.yaml" })
    public void pauseRunDelay() throws Exception {
        pauseTest.runDelay(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/pause-duration-from-input.yaml" })
    public void pauseRunDurationFromInput() throws Exception {
        pauseTest.runDurationFromInput(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/pause-timeout.yaml" })
    public void pauseRunTimeout() throws Exception {
        pauseTest.runTimeout(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml" })
    void shouldIgnoreExecutionById() throws Exception {
        ignoreExecutionCaseTest.shouldIgnoreExecutionById();
    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml", "flows/valids/output-values.yml" })
    void shouldIgnoreExecutionByFlowId() throws Exception {
        ignoreExecutionCaseTest.shouldIgnoreExecutionByFlowId();
    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml", "flows/valids/minimal2.yaml" })
    void shouldIgnoreExecutionByNamespace() throws Exception {
        ignoreExecutionCaseTest.shouldIgnoreExecutionByNamespace();
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
    @LoadFlows(value = { "flows/valids/waitfor.yaml" }, tenantId = "waitfor")
    void waitFor() throws Exception {
        loopUntilTestCaseTest.waitfor("waitfor");
    }

    @Test
    @LoadFlows(value = { "flows/valids/waitfor-max-iterations.yaml" }, tenantId = "waitformaxiterations")
    void waitforMaxIterations() throws Exception {
        loopUntilTestCaseTest.waitforMaxIterations("waitformaxiterations");
    }

    @Test
    @LoadFlows(value = { "flows/valids/waitfor-max-duration.yaml" }, tenantId = "waitformaxduration")
    void waitforMaxDuration() throws Exception {
        loopUntilTestCaseTest.waitforMaxDuration("waitformaxduration");
    }

    @Test
    @LoadFlows(value = { "flows/valids/waitfor-no-success.yaml" }, tenantId = "waitfornosuccess")
    void waitforNoSuccess() throws Exception {
        loopUntilTestCaseTest.waitforNoSuccess("waitfornosuccess");
    }

    @Test
    @LoadFlows(value = { "flows/valids/waitfor-multiple-tasks.yaml" }, tenantId = "waitformultipletasks")
    void waitforMultipleTasks() throws Exception {
        loopUntilTestCaseTest.waitforMultipleTasks("waitformultipletasks");
    }

    @Test
    @LoadFlows(value = { "flows/valids/waitfor-multiple-tasks-failed.yaml" }, tenantId = "waitformultipletasksfailed")
    void waitforMultipleTasksFailed() throws Exception {
        loopUntilTestCaseTest.waitforMultipleTasksFailed("waitformultipletasksfailed");
    }

    @Test
    @ExecuteFlow("flows/valids/loop-serial.yaml")
    protected void loopSerial(Execution execution) throws Exception {
        loopCaseTest.loopSerial(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-serial-multiple-tasks.yaml")
    protected void loopSerialMultipleTasks(Execution execution) throws Exception {
        loopCaseTest.loopSerialMultipleTasks(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-failed.yaml")
    protected void loopFailed(Execution execution) throws Exception {
        loopCaseTest.loopFailed(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-failed-no-transmit.yaml")
    protected void loopTransmitFailedFalse(Execution execution) throws Exception {
        loopCaseTest.loopTransmitFailedFalse(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-parallel-unlimited.yaml")
    protected void loopParallelUnlimited(Execution execution) throws Exception {
        loopCaseTest.loopParallelUnlimited(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-parallel-equal.yaml")
    protected void loopParallelEqual(Execution execution) throws Exception {
        loopCaseTest.loopParallelEqual(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-parallel-more.yaml")
    protected void loopParallelMore(Execution execution) throws Exception {
        loopCaseTest.loopParallelMore(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-parallel-less.yaml")
    protected void loopParallelLess(Execution execution) throws Exception {
        loopCaseTest.loopParallelLess(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-flowable.yaml")
    protected void loopFlowable(Execution execution) throws Exception {
        loopCaseTest.loopFlowable(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-multiple.yaml")
    protected void loopMultiple(Execution execution) throws Exception {
        loopCaseTest.loopMultiple(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-nested.yaml")
    protected void loopNested(Execution execution) throws Exception {
        loopCaseTest.loopNested(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-map.yaml")
    protected void loopMap(Execution execution) throws Exception {
        loopCaseTest.loopMap(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-values-from-uri.yaml")
    protected void loopValuesFromUri(Execution execution) throws Exception {
        loopCaseTest.loopValuesFromUri(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-expression-context.yaml")
    protected void loopExecutionContext(Execution execution) throws Exception {
        loopCaseTest.loopExpressionContext(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-outputs.yaml")
    protected void loopOutputs(Execution execution) throws Exception {
        loopCaseTest.loopOutputs(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-outputs-store.yaml")
    protected void loopOutputsStore(Execution execution) throws Exception {
        loopCaseTest.loopOutputsStore(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-outputs-auto.yaml")
    protected void loopOutputsAuto(Execution execution) throws Exception {
        loopCaseTest.loopOutputsAuto(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-outputs-failed-render.yaml")
    protected void loopOutputsFailedRender(Execution execution) {
        loopCaseTest.loopOutputsFailedRender(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-empty.yaml")
    protected void loopEmpty(Execution execution) {
        loopCaseTest.loopEmpty(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-null.yaml" )
    protected void loopWithNull(Execution execution) {
        loopCaseTest.loopWithNull(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-object.yaml")
    public void loopObject(Execution execution) throws InternalException {
        loopCaseTest.loopObject(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-object-in-list.yaml")
    public void loopObjectInList(Execution execution) throws InternalException {
        loopCaseTest.loopObjectInList(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/loop-switch.yaml")
    public void loopSwitch(Execution execution) throws InternalException {
        loopCaseTest.loopSwitch(execution);
    }

    @Test
    @LoadFlows(value = { "flows/valids/minimal.yaml" }, tenantId = TENANT_1)
    void shouldScheduleOnDate() throws Exception {
        scheduleDateCaseTest.shouldScheduleOnDate(TENANT_1);
    }

    @Test
    @LoadFlows({ "flows/valids/sla-max-duration-fail.yaml" })
    void maxDurationSLAShouldFail() throws Exception {
        slaTestCase.maxDurationSLAShouldFail();
    }

    @Test
    @LoadFlows({ "flows/valids/sla-max-duration-ok.yaml" })
    void maxDurationSLAShouldPass() throws Exception {
        slaTestCase.maxDurationSLAShouldPass();
    }

    @Test
    @LoadFlows({ "flows/valids/sla-execution-condition.yaml" })
    void executionConditionSLAShouldPass() throws Exception {
        slaTestCase.executionConditionSLAShouldPass();
    }

    @Test
    @LoadFlows(value = { "flows/valids/sla-execution-condition.yaml" }, tenantId = TENANT_1)
    void executionConditionSLAShouldCancel() throws Exception {
        slaTestCase.executionConditionSLAShouldCancel(TENANT_1);
    }

    @Test
    @LoadFlows(value = { "flows/valids/sla-execution-condition.yaml" }, tenantId = TENANT_2)
    void executionConditionSLAShouldLabel() throws Exception {
        slaTestCase.executionConditionSLAShouldLabel(TENANT_2);
    }

    @Test
    @LoadFlows({ "flows/valids/sla-parent-flow.yaml", "flows/valids/sla-subflow.yaml" })
    void slaViolationOnSubflowMayEndTheParentFlow() throws Exception {
        slaTestCase.slaViolationOnSubflowMayEndTheParentFlow();
    }

    @Test
    @LoadFlows({ "flows/valids/if.yaml" })
    void multipleIf() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests", "if", null,
            (f, e) -> Map.of("if1", true, "if2", false, "if3", true)
        );

        assertThat(execution.getTaskRunList()).hasSize(12);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/failed-first.yaml", tenantId = TENANT_1)
    public void changeStateShouldEndsInSuccess(Execution execution) throws Exception {
        changeStateTestCase.changeStateShouldEndsInSuccess(execution);
    }

    @Test
    @LoadFlows(value = { "flows/valids/failed-first.yaml", "flows/valids/subflow-parent-of-failed.yaml" }, tenantId = TENANT_2)
    public void changeStateInSubflowShouldEndsParentFlowInSuccess() throws Exception {
        changeStateTestCase.changeStateInSubflowShouldEndsParentFlowInSuccess(TENANT_2);
    }

    @Test
    @ExecuteFlow("flows/valids/after-execution.yaml")
    public void shouldCallTasksAfterExecution(Execution execution) throws InternalException {
        afterExecutionTestCase.shouldCallTasksAfterExecution(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/after-execution-finally.yaml")
    public void shouldCallTasksAfterFinally(Execution execution) throws InternalException {
        afterExecutionTestCase.shouldCallTasksAfterFinally(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/after-execution-error.yaml")
    public void shouldCallTasksAfterError(Execution execution) throws InternalException {
        afterExecutionTestCase.shouldCallTasksAfterError(execution);
    }

    @Test
    @LoadFlows({ "flows/valids/workertask-result-too-large.yaml" })
    protected void workerTaskResultTooLarge() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logsQueue.addListener(l -> logs.add(l));

        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "workertask-result-too-large"
        );

        LogEntry matchingLog = TestsUtils.awaitLog(
            logs, log -> log.getMessage()
                .startsWith("Unable to emit the worker task result to the queue")
        );

        assertThat(matchingLog).isNotNull();
        assertThat(matchingLog.getLevel()).isEqualTo(Level.ERROR);
        // the size is different on all runs, so we cannot assert on the exact message size
        assertThat(matchingLog.getMessage()).contains("message of size");
        assertThat(matchingLog.getMessage()).contains("has exceeded the configured limit of 1048576");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
    }

    @Test
    void avoidInfiniteExecutionLoop() throws QueueException, InterruptedException {
        CopyOnWriteArrayList<ExecutionEvent> executions = new CopyOnWriteArrayList<>();
        executionEventQueue.addListener(e -> executions.add(e));

        Execution execution = Execution.newExecution(TestsUtils.mockFlow(), Collections.emptyList());
        executionQueue.emit(execution);

        // We expect the initial execution message only
        await()
            .during(Duration.ofMillis(500)) // Wait some time to ensure no infinite loop occurs
            .atMost(Duration.ofSeconds(10))
            .until(() -> executions.size() == 1);
    }

    @Test
    @LoadFlows(value = { "flows/valids/waitfor-child-task-warning.yaml" }, tenantId = "waitforchildtaskwarning")
    void waitForChildTaskWarning() throws Exception {
        loopUntilTestCaseTest.waitForChildTaskWarning("waitforchildtaskwarning");
    }

    @Test
    @LoadFlows("flows/valids/errors.yaml")
    void errors() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logsQueue.addListener(l -> logs.add(l));

        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, NAMESPACE, "errors", null, null,
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(7);

        LogEntry logEntry = TestsUtils.awaitLog(
            logs,
            log -> log.getMessage().contains("- task: failed, message: Task failure")
        );
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getMessage()).isEqualTo("- task: failed, message: Task failure");
    }

    @RetryingTest(5)
    @LoadFlows({ "flows/valids/execution.yaml" })
    void executionDate() throws Exception {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, NAMESPACE,
            "execution-start-date", null, null, Duration.ofSeconds(60)
        );

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.getTaskRunList().getFirst());
        assertThat((String) outputs.get("value")).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6,9}Z");
    }

}
