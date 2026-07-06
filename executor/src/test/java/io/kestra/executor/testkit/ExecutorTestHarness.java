package io.kestra.executor.testkit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.configuration.LoggingConfiguration;
import io.kestra.core.runners.configuration.VariableConfiguration;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.services.configuration.TaskOutputConfiguration;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.services.WorkerQueueService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorService;
import io.kestra.executor.SLAService;

import io.kestra.core.assets.AssetService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import org.mockito.Mockito;

/**
 * Composition root for executor unit tests: wires a real {@link ExecutorService} over in-memory
 * fakes — no Micronaut context, no database, no queues, no threads.
 * <p>
 * The harness exposes the executor as a function: feed a (flow, execution) pair or a
 * {@link WorkerTaskResult} and assert on the returned {@link ExecutorContext} command object.
 * Each {@link #process} call mirrors the production {@code ExecutionEventMessageHandler} cycle:
 * a fresh {@code ExecutorContext} per event, {@code process()} then {@code onNexts()}, repeated
 * until the executor asks for external work (worker tasks, delays, subflows) or goes quiet.
 */
public final class ExecutorTestHarness {
    private static final int MAX_CYCLES = 100;

    private final ExecutorService executorService;
    private final InMemoryFlowMetaStore flowMetaStore;
    private final InMemoryTaskOutputRepository taskOutputRepository;
    private final RecordingBroadcastQueue<ExecutionKilled> killQueue;
    private final RecordingDispatchQueue<LoopExecutionEvent> loopExecutionEventQueue;
    private final RecordingLogEntryEmitter logEmitter;
    private final MetricRegistry metricRegistry;

    public static ExecutorTestHarness create() {
        return new ExecutorTestHarness();
    }

    private ExecutorTestHarness() {
        this.flowMetaStore = new InMemoryFlowMetaStore();
        this.taskOutputRepository = new InMemoryTaskOutputRepository();
        this.killQueue = new RecordingBroadcastQueue<>("kill");
        this.loopExecutionEventQueue = new RecordingDispatchQueue<>("loopExecutionEvent");
        this.logEmitter = new RecordingLogEntryEmitter();
        this.metricRegistry = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig(null, null, null, Map.of()));

        RunContextLoggerFactory runContextLoggerFactory = new RunContextLoggerFactory(logEmitter, new LoggingConfiguration(null));
        TaskOutputService taskOutputService = new TaskOutputService(
            taskOutputRepository,
            Mockito.mock(StorageInterface.class),
            new NamespaceFactory(Mockito.mock(NamespaceFileMetadataStateStore.class)),
            new TaskOutputConfiguration(-1)
        );
        // Real Pebble engine without Micronaut: the mocked ApplicationContext returns no Extension
        // beans, so only Pebble built-ins are available (same pattern as RunVariablesTest).
        VariableConfiguration variableConfiguration = new VariableConfiguration();
        VariableRenderer renderer = new VariableRenderer(
            new PebbleEngineFactory(Mockito.mock(ApplicationContext.class), variableConfiguration, new SimpleMeterRegistry()),
            variableConfiguration
        );
        KitRunContextFactory runContextFactory = new KitRunContextFactory(renderer, runContextLoggerFactory, metricRegistry, taskOutputService);

        this.executorService = new ExecutorService(
            runContextFactory,
            metricRegistry,
            flowMetaStore,
            // the executor-facing ExecutionService methods are pure and never touch its injected fields
            Mockito.mock(ExecutionService.class, Mockito.CALLS_REAL_METHODS),
            new WorkerQueueService.Default(),
            new SLAService(),
            Optional.empty(),
            killQueue,
            loopExecutionEventQueue,
            runContextLoggerFactory,
            new AssetService.NoopAssetService(),
            Mockito.mock(RunContextInitializer.class),
            taskOutputService
        );
    }

    /**
     * Register a flow so the executor can resolve it (subflow lookups, restarts).
     */
    public ExecutorTestHarness registerFlow(FlowWithSource flow) {
        flowMetaStore.register(flow);
        return this;
    }

    /**
     * Run execution-event cycles until the executor asks for external work (worker tasks,
     * delays, subflow or loop executions), the execution reaches a terminal or paused state,
     * or nothing changes anymore. Returns the {@link ExecutorContext} of the last cycle.
     */
    public ExecutorContext process(FlowWithSource flow, Execution execution) {
        Execution current = execution;

        for (int i = 0; i < MAX_CYCLES; i++) {
            ExecutorContext context = cycle(flow, current);

            boolean asksForExternalWork = !context.getWorkerTasks().isEmpty()
                || !context.getExecutionDelays().isEmpty()
                || !context.getSubflowExecutions().isEmpty()
                || !context.getLoopExecutions().isEmpty()
                || context.getException() != null;
            boolean settled = context.getExecution().getState().getCurrent().isTerminated()
                || context.getExecution().getState().isPaused()
                || !context.isExecutionUpdated();

            if (asksForExternalWork || settled) {
                return context;
            }
            current = context.getExecution();
        }

        throw new IllegalStateException("Executor did not quiesce after " + MAX_CYCLES + " cycles — possible execution loop");
    }

    /**
     * Merge a {@link WorkerTaskResult} into the previous cycle's execution (mirroring
     * {@code WorkerTaskResultMessageHandler}) then run the follow-up execution-event cycles.
     */
    public ExecutorContext processResult(FlowWithSource flow, ExecutorContext previous, WorkerTaskResult result) throws Exception {
        Execution execution = previous.getExecution();
        if (!execution.hasTaskRunJoinable(result.getTaskRun())) {
            throw new IllegalStateException("WorkerTaskResult for taskrun " + result.getTaskRun().getId() + " is not joinable");
        }

        ExecutorContext merge = new ExecutorContext(execution, flow);
        executorService.addWorkerTaskResult(merge, () -> flow, result);

        return process(flow, merge.getExecution());
    }

    /**
     * One production message cycle: fresh context, {@code process()}, then {@code onNexts()}.
     */
    private ExecutorContext cycle(FlowWithSource flow, Execution execution) {
        ExecutorContext context = new ExecutorContext(execution, flow);
        context = executorService.process(context);

        if (!context.getNexts().isEmpty()) {
            context.withExecution(
                executorService.onNexts(context.getExecution(), context.getNexts()),
                "onNexts"
            );
        }

        return context;
    }

    /**
     * Direct access to the real {@link ExecutorService} for single-method decision tests.
     */
    public ExecutorService executorService() {
        return executorService;
    }

    public List<ExecutionKilled> kills() {
        return killQueue.emitted();
    }

    public List<LoopExecutionEvent> loopEvents() {
        return loopExecutionEventQueue.emitted();
    }

    public List<LogEntry> logs() {
        return logEmitter.emitted();
    }
}
