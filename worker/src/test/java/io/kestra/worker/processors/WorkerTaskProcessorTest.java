package io.kestra.worker.processors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.assets.External;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.AssetFailureBehavior;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskData;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.queues.InMemoryWorkerQueue;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.services.ExecutionKilledManager;

import jakarta.inject.Inject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component-level tests for {@link WorkerTaskProcessor}'s behavior when the worker is shutting down,
 * covering the regression from <a href="https://github.com/kestra-io/kestra/issues/17124">#17124</a>:
 * a task that fails on its own during the termination grace period must still have its terminal
 * result emitted, while a task the shutdown actually interrupted is deferred for resubmission.
 */
@KestraTest
class WorkerTaskProcessorTest {

    @Inject
    private ServerConfig serverConfig;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private WorkerSecurityService workerSecurityService;

    @Inject
    private TracerFactory tracerFactory;

    @Inject
    private RunContextInitializer runContextInitializer;

    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    private ExecutionKilledManager executionKilledManager;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Test
    void shouldEmitFailedResultWhenTaskFailsOnItsOwnDuringShutdownDrain() throws Exception {
        // Given a processor in the graceful drain window (stopped) that did NOT interrupt the task
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        processor.stop();

        // When the task reaches a FAILED state on its own during the drain window
        processor.process(failingWorkerTask());

        // Then its terminal FAILED result is emitted, not silently dropped
        List<WorkerTaskResult> results = drain(resultQueue);
        assertThat(results)
            .as("a genuine failure during the drain window must be emitted, not dropped (#17124)")
            .anyMatch(result -> result.getTaskRun().getState().isFailed());
    }

    @Test
    void shouldDropFailedResultWhenTaskWasInterruptedByShutdown() throws Exception {
        // Given a processor whose task is forcibly interrupted by the shutdown
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        processor.signalShutdownInterrupt();
        processor.stop();

        // When the interrupted task ends in a failed state
        processor.process(failingWorkerTask());

        // Then no terminal result is emitted (it will be resubmitted) — only the RUNNING preamble
        List<WorkerTaskResult> results = drain(resultQueue);
        assertThat(results)
            .as("an interrupted task's failure must be deferred for resubmission, not reported")
            .noneMatch(result -> result.getTaskRun().getState().isFailed());
    }

    @Test
    void shouldKeepDeclaredInputsWhenOutputsCannotBeRendered() throws Exception {
        // Given a task whose declared outputs cannot be rendered, while its declared inputs can
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);

        // When the task runs
        processor.process(unrenderableOutputsWorkerTask());

        // Then the inputs are still emitted, and the unrendered outputs only show up in the state
        List<WorkerTaskResult> results = drain(resultQueue);
        TaskRun taskRun = results.getLast().getTaskRun();
        assertThat(taskRun.getAssetEmits())
            .as("a failing output must not discard the declared inputs")
            .singleElement()
            .satisfies(bundle ->
            {
                assertThat(bundle.getInputs()).extracting(AssetIdentifier::id).containsExactly("declared-input");
                assertThat(bundle.getOutputs()).isEmpty();
            });
        assertThat(taskRun.getState().getCurrent())
            .as("a partially emitted declaration is still an emission failure, escalated per assetFailureBehavior")
            .isEqualTo(State.Type.WARNING);
    }

    @Test
    void shouldDefaultAssetNamespaceToTheFlowNamespaceWhenNotDeclared() throws Exception {
        // Given a task declaring an input and an output that carry no namespace
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);

        // When the task runs
        processor.process(namespacelessAssetsWorkerTask());

        // Then both inherit the namespace of the flow
        List<WorkerTaskResult> results = drain(resultQueue);
        TaskRun taskRun = results.getLast().getTaskRun();
        assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskRun.getAssetEmits())
            .singleElement()
            .satisfies(bundle ->
            {
                assertThat(bundle.getInputs()).extracting(AssetIdentifier::namespace).containsExactly("io.kestra.unit-test");
                assertThat(bundle.getOutputs()).extracting(Asset::getNamespace).containsExactly("io.kestra.unit-test");
            });
    }

    @Test
    void shouldKeepSuccessWhenAssetEmissionFailsAndBehaviorIsIgnore() throws Exception {
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        processor.process(assetEmissionFailureWorkerTask(AssetFailureBehavior.IGNORE));

        List<WorkerTaskResult> results = drain(resultQueue);
        String taskRunId = results.getLast().getTaskRun().getId();
        assertThat(results.getLast().getTaskRun().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        // state is unchanged, but the failed asset emission is still surfaced to the user
        LogEntry escalationLog = TestsUtils.awaitLog(logs, log -> taskRunId.equals(log.getTaskRunId()) && log.getMessage().contains("assetFailureBehavior"));
        assertThat(escalationLog).isNotNull();
        assertThat(escalationLog.getMessage()).contains("not changed").contains("IGNORE");
    }

    @Test
    void shouldClampToWarningWhenAssetFailureBehaviorIsFailAndAllowFailureIsSet() throws Exception {
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);

        processor.process(assetEmissionFailureWorkerTask(AssetFailureBehavior.FAIL, true, false));

        List<WorkerTaskResult> results = drain(resultQueue);
        // allowFailure applies uniformly to a FAILED state, whether genuine or escalated by assetFailureBehavior
        assertThat(results.getLast().getTaskRun().getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    void shouldClampToSuccessWhenAssetFailureBehaviorIsWarnAndAllowWarningIsSet() throws Exception {
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        processor.process(assetEmissionFailureWorkerTask(AssetFailureBehavior.WARN, false, true));

        List<WorkerTaskResult> results = drain(resultQueue);
        String taskRunId = results.getLast().getTaskRun().getId();
        // allowWarning has final say: a WARNING escalated purely from assetFailureBehavior is clamped down
        assertThat(results.getLast().getTaskRun().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        // the final state (SUCCESS) alone can't tell escalation-then-clamp apart from no escalation at
        // all — the log line is what actually proves assetFailureBehavior ran
        LogEntry escalationLog = TestsUtils.awaitLog(logs, log -> taskRunId.equals(log.getTaskRunId()) && log.getMessage().contains("assetFailureBehavior"));
        assertThat(escalationLog).isNotNull();
        assertThat(escalationLog.getMessage()).contains("SUCCESS").contains("WARNING").contains("WARN");
    }

    @Test
    void shouldNotEscalateWhenTaskAlreadyFailedOnItsOwn() throws Exception {
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        processor.process(taskFailsAndAssetEmissionFailsWorkerTask(AssetFailureBehavior.WARN));

        List<WorkerTaskResult> results = drain(resultQueue);
        String taskRunId = results.getLast().getTaskRun().getId();
        // a task that already terminated in error on its own is not touched by assetFailureBehavior
        assertThat(results.getLast().getTaskRun().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        LogEntry escalationLog = TestsUtils.awaitLog(logs, log -> taskRunId.equals(log.getTaskRunId()) && log.getMessage().contains("assetFailureBehavior"));
        assertThat(escalationLog).isNotNull();
        assertThat(escalationLog.getMessage()).contains("not changed");
    }

    @Test
    void shouldClampToWarningWhenTaskAlreadyFailedOnItsOwnAndAllowFailureIsSet() throws Exception {
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);

        processor.process(taskFailsAndAssetEmissionFailsWorkerTask(AssetFailureBehavior.FAIL, true));

        List<WorkerTaskResult> results = drain(resultQueue);
        // a genuine pre-existing failure is untouched by assetFailureBehavior (apply() no-ops on an already
        // terminated-in-error state), so allowFailure still applies its ordinary softening here
        assertThat(results.getLast().getTaskRun().getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    void shouldFailTaskWhenAssetEmissionFailsAndBehaviorIsFail() throws Exception {
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        processor.process(assetEmissionFailureWorkerTask(AssetFailureBehavior.FAIL));

        List<WorkerTaskResult> results = drain(resultQueue);
        String taskRunId = results.getLast().getTaskRun().getId();
        assertThat(results.getLast().getTaskRun().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        // the user must be able to tell FAILED came from the asset emission, not the task's own logic
        LogEntry escalationLog = TestsUtils.awaitLog(logs, log -> taskRunId.equals(log.getTaskRunId()) && log.getMessage().contains("assetFailureBehavior"));
        assertThat(escalationLog).isNotNull();
        assertThat(escalationLog.getMessage()).contains("SUCCESS").contains("FAILED").contains("FAIL");
    }

    @Test
    void shouldWarnTaskWhenAssetEmissionFailsAndBehaviorIsWarn() throws Exception {
        InMemoryWorkerQueue<WorkerTaskResult> resultQueue = new InMemoryWorkerQueue<>(100);
        WorkerTaskProcessor processor = newProcessor(resultQueue);
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        processor.process(assetEmissionFailureWorkerTask(AssetFailureBehavior.WARN));

        List<WorkerTaskResult> results = drain(resultQueue);
        String taskRunId = results.getLast().getTaskRun().getId();
        assertThat(results.getLast().getTaskRun().getState().getCurrent()).isEqualTo(State.Type.WARNING);
        LogEntry escalationLog = TestsUtils.awaitLog(logs, log -> taskRunId.equals(log.getTaskRunId()) && log.getMessage().contains("assetFailureBehavior"));
        assertThat(escalationLog).isNotNull();
        assertThat(escalationLog.getMessage()).contains("SUCCESS").contains("WARNING").contains("WARN");
    }

    private WorkerTaskProcessor newProcessor(WorkerQueue<WorkerTaskResult> resultQueue) {
        return new WorkerTaskProcessor(
            "test-worker",
            WorkerGroups.DEFAULT_ID,
            serverConfig,
            metricRegistry,
            workerSecurityService,
            tracerFactory.getTracer(Worker.class, "WORKER"),
            runContextInitializer,
            runContextLoggerFactory,
            resultQueue,
            new InMemoryWorkerQueue<>(100),
            executionKilledManager
        );
    }

    private WorkerTask failingWorkerTask() {
        AlwaysFail task = AlwaysFail.builder()
            .type(AlwaysFail.class.getName())
            .id("fail-task")
            .build();

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(List.of(task))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        ResolvedTask resolvedTask = ResolvedTask.of(task);

        return WorkerTask.builder()
            .data(WorkerTaskData.from(runContextFactory.of(Map.of("key", "value"))))
            .task(task)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }

    private WorkerTask unrenderableOutputsWorkerTask() {
        AssetEmissionFailure task = AssetEmissionFailure.builder()
            .type(AssetEmissionFailure.class.getName())
            .id("asset-task")
            .assets(
                new AssetsDeclaration(
                    Property.ofValue(false),
                    Property.ofValue(List.of(new AssetIdentifier(null, "io.kestra.unit-test", "declared-input", "MY_OWN_ASSET_TYPE"))),
                    // rendered value is a plain string, not JSON, so binding it as List<Asset> fails
                    Property.ofExpression("{{ 'not-json' }}"),
                    Property.ofValue(AssetFailureBehavior.WARN)
                )
            )
            .build();

        return workerTaskFor(task);
    }

    private WorkerTask namespacelessAssetsWorkerTask() {
        AssetEmissionFailure task = AssetEmissionFailure.builder()
            .type(AssetEmissionFailure.class.getName())
            .id("asset-task")
            .assets(
                new AssetsDeclaration(
                    Property.ofValue(false),
                    Property.ofValue(List.of(new AssetIdentifier(null, null, "declared-input", "MY_OWN_ASSET_TYPE"))),
                    Property.ofValue(List.<Asset> of(External.builder().id("declared-output").build())),
                    Property.ofValue(AssetFailureBehavior.WARN)
                )
            )
            .build();

        return workerTaskFor(task);
    }

    private WorkerTask assetEmissionFailureWorkerTask(AssetFailureBehavior assetFailureBehavior) {
        return assetEmissionFailureWorkerTask(assetFailureBehavior, false, false);
    }

    private WorkerTask assetEmissionFailureWorkerTask(AssetFailureBehavior assetFailureBehavior, boolean allowFailure, boolean allowWarning) {
        AssetEmissionFailure task = AssetEmissionFailure.builder()
            .type(AssetEmissionFailure.class.getName())
            .id("asset-task")
            .allowFailure(allowFailure)
            .allowWarning(allowWarning)
            // rendered value is a plain string, not JSON, so binding it as List<AssetIdentifier> fails
            .assets(new AssetsDeclaration(Property.ofValue(false), Property.ofExpression("{{ 'not-json' }}"), Property.ofValue(List.of()), Property.ofValue(assetFailureBehavior)))
            .build();

        return workerTaskFor(task);
    }

    private WorkerTask taskFailsAndAssetEmissionFailsWorkerTask(AssetFailureBehavior assetFailureBehavior) {
        return taskFailsAndAssetEmissionFailsWorkerTask(assetFailureBehavior, false);
    }

    private WorkerTask taskFailsAndAssetEmissionFailsWorkerTask(AssetFailureBehavior assetFailureBehavior, boolean allowFailure) {
        AlwaysFail task = AlwaysFail.builder()
            .type(AlwaysFail.class.getName())
            .id("failing-asset-task")
            .allowFailure(allowFailure)
            // rendered value is a plain string, not JSON, so binding it as List<AssetIdentifier> fails
            .assets(new AssetsDeclaration(Property.ofValue(false), Property.ofExpression("{{ 'not-json' }}"), Property.ofValue(List.of()), Property.ofValue(assetFailureBehavior)))
            .build();

        return workerTaskFor(task);
    }

    private WorkerTask workerTaskFor(Task task) {
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unit-test")
            .tasks(List.of(task))
            .build();

        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        ResolvedTask resolvedTask = ResolvedTask.of(task);

        return WorkerTask.builder()
            .data(WorkerTaskData.from(runContextFactory.of(Map.of("key", "value"))))
            .task(task)
            .taskRun(TaskRun.of(execution, resolvedTask))
            .build();
    }

    private static List<WorkerTaskResult> drain(WorkerQueue<WorkerTaskResult> queue) throws InterruptedException {
        List<WorkerTaskResult> results = new ArrayList<>();
        WorkerTaskResult result;
        while ((result = queue.poll(Duration.ZERO)) != null) {
            results.add(result);
        }
        return results;
    }

    /**
     * A task that always fails on its own (throws when run), modeling e.g. a script container exiting
     * non-zero. Constructed and executed directly by the processor, so no plugin registration is needed.
     */
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class AlwaysFail extends Task implements RunnableTask<VoidOutput> {
        @Override
        public VoidOutput run(RunContext runContext) {
            throw new RuntimeException("simulated task failure during shutdown drain");
        }
    }

    /**
     * A task that succeeds on its own, so only its asset declaration decides the outcome. Constructed and
     * executed directly by the processor, so no plugin registration is needed.
     */
    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class AssetEmissionFailure extends Task implements RunnableTask<VoidOutput> {
        @Override
        public VoidOutput run(RunContext runContext) {
            // null, not new VoidOutput(): the empty bean has no properties and Jackson's
            // FAIL_ON_EMPTY_BEANS would blow up when the processor serializes the output to a map
            return null;
        }
    }
}
