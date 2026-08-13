package io.kestra.executor.testkit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.Mockito;

import io.kestra.core.assets.AssetService;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.encryption.EncryptionConfig;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import io.kestra.core.runners.DisabledReusableInputsExpander;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.runners.PausedTaskNotifier;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.configuration.LoggingConfiguration;
import io.kestra.core.runners.configuration.VariableConfiguration;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.services.ConcurrencyLimitResolver;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.QuotaService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.services.WorkerQueueService;
import io.kestra.core.services.configuration.TaskOutputConfiguration;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.trace.TracerFactory;
import io.kestra.executor.ConcurrencySlotReleaseProcessor;
import io.kestra.executor.ExecutionDelayProcessor;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorService;
import io.kestra.executor.FlowTriggerService;
import io.kestra.executor.KillSwitchActionService;
import io.kestra.executor.SLAMonitorProcessor;
import io.kestra.executor.SLAService;
import io.kestra.executor.handler.ExecutionCommandMessageHandler;
import io.kestra.executor.handler.ExecutionEventMessageHandler;
import io.kestra.executor.handler.ExecutionKilledExecutionMessageHandler;
import io.kestra.executor.handler.LoopExecutionEventMessageHandler;
import io.kestra.executor.handler.MultipleConditionEventMessageHandler;
import io.kestra.executor.handler.SubflowExecutionEndMessageHandler;
import io.kestra.executor.handler.SubflowExecutionResultMessageHandler;
import io.kestra.executor.handler.WorkerTaskResultListener;
import io.kestra.executor.handler.WorkerTaskResultMessageHandler;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import jakarta.validation.Validator;

/**
 * Composition root for executor unit tests: wires the real {@link ExecutorService} and all
 * message handlers over in-memory fakes — no Micronaut context, no database, no queues, no threads.
 * <p>
 * The harness exposes the executor as a function: feed a (flow, execution) pair, a
 * {@link WorkerTaskResult}, or call a handler directly, and assert on the returned
 * {@link ExecutorContext} command object plus the recorded side-effect channels.
 * {@link #process} mirrors the production {@code ExecutionEventMessageHandler} cycle:
 * a fresh {@code ExecutorContext} per event, {@code process()} then {@code onNexts()}, repeated
 * until the executor asks for external work (worker tasks, delays, subflows) or goes quiet.
 * <p>
 * Cross-cutting collaborators without executor logic ({@link KillSwitchService} — stubbed to
 * PASS, {@link QuotaService}, {@link AsyncOperationService}, {@link FlowTriggerService},
 * {@link KillSwitchActionService}) are Mockito mocks exposed for per-test stubbing.
 */
public final class ExecutorTestHarness {
    private static final int MAX_CYCLES = 100;

    // real production objects
    private final ExecutorService executorService;
    private final ExecutionEventMessageHandler executionEventMessageHandler;
    private final ExecutionCommandMessageHandler executionCommandMessageHandler;
    private final WorkerTaskResultMessageHandler workerTaskResultMessageHandler;
    private final ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler;
    private final SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler;
    private final SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler;
    private final LoopExecutionEventMessageHandler loopExecutionEventMessageHandler;
    private final MultipleConditionEventMessageHandler multipleConditionEventMessageHandler;
    private final ExecutionDelayProcessor executionDelayProcessor;
    private final ConcurrencySlotReleaseProcessor concurrencySlotReleaseProcessor;
    private final SLAMonitorProcessor slaMonitorProcessor;

    // in-memory fakes
    private final InMemoryFlowMetaStore flowMetaStore;
    private final InMemoryExecutionStateStore executionStateStore;
    private final InMemoryExecutionQueuedStateStore executionQueuedStateStore;
    private final InMemoryExecutionDelayStateStore executionDelayStateStore;
    private final InMemorySLAMonitorStateStore slaMonitorStateStore;
    private final InMemoryConcurrencyLimitStateStore concurrencyLimitStateStore;
    private final InMemoryTaskOutputRepository taskOutputRepository;
    private final RecordingBroadcastQueue<ExecutionKilled> killQueue;
    private final RecordingDispatchQueue<LoopExecutionEvent> loopExecutionEventQueue;
    private final RecordingKeyedDispatchQueue<WorkerJobEvent> workerJobEventQueue;
    private final RecordingDispatchQueue<SubflowExecutionResult> subflowExecutionResultQueue;
    private final RecordingDispatchQueue<Execution> executionQueue;
    private final RecordingBroadcastQueue<FollowExecutionEvent> followExecutionEventQueue;
    private final RecordingDispatchQueue<ExecutionCommand> executionCommandQueue;
    private final RecordingLogEntryEmitter logEmitter;

    // mocks exposed for per-test stubbing
    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;
    private final WorkerTaskResultListener workerTaskResultListener;
    private final ConcurrencyLimitResolver concurrencyLimitResolver;
    private final QuotaService quotaService;
    private final AsyncOperationService asyncOperationService;
    private final FlowTriggerService flowTriggerService;
    private final MultipleConditionStateStore multipleConditionStateStore;
    private final ExecutionService executionService;
    private final KitRunContextFactory runContextFactory;

    public static ExecutorTestHarness create() {
        return new ExecutorTestHarness();
    }

    private ExecutorTestHarness() {
        this.flowMetaStore = new InMemoryFlowMetaStore();
        this.executionStateStore = new InMemoryExecutionStateStore();
        this.executionQueuedStateStore = new InMemoryExecutionQueuedStateStore();
        this.executionDelayStateStore = new InMemoryExecutionDelayStateStore();
        this.slaMonitorStateStore = new InMemorySLAMonitorStateStore();
        this.concurrencyLimitStateStore = new InMemoryConcurrencyLimitStateStore();
        this.taskOutputRepository = new InMemoryTaskOutputRepository();
        this.killQueue = new RecordingBroadcastQueue<>("kill");
        this.loopExecutionEventQueue = new RecordingDispatchQueue<>("loopExecutionEvent");
        this.workerJobEventQueue = new RecordingKeyedDispatchQueue<>("workerJobEvent");
        this.subflowExecutionResultQueue = new RecordingDispatchQueue<>("subflowExecutionResult");
        this.executionQueue = new RecordingDispatchQueue<>("execution");
        this.followExecutionEventQueue = new RecordingBroadcastQueue<>("followExecutionEvent");
        this.executionCommandQueue = new RecordingDispatchQueue<>("executionCommand");
        this.logEmitter = new RecordingLogEntryEmitter();

        MetricRegistry metricRegistry = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig(null, null, null, Map.of()));
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
        TracerFactory tracerFactory = Mockito.mock(
            TracerFactory.class,
            invocation -> "getTracer".equals(invocation.getMethod().getName()) ? new PassthroughTracer() : Mockito.RETURNS_DEFAULTS.answer(invocation)
        );
        // Task-bean validation after Property rendering is a no-op in the unit lane: a Mockito
        // Validator returns no violations (empty-set default), so RunContextProperty#as never NPEs.
        Validator validator = Mockito.mock(Validator.class);
        // DefaultRunContext.services(), validate() and inputAndOutput() resolve beans through the
        // run context's ApplicationContext; this mocked locator answers only those lookups
        // (Optional-returning methods like getProperty/findBean fall back to Mockito's empty
        // defaults, so Services.isWorker resolves to false and additionalService() stays allowed).
        KitRunContextFactory[] runContextFactoryRef = new KitRunContextFactory[1];
        FlowInputOutput flowInputOutput = new FlowInputOutput(
            Mockito.mock(StorageInterface.class),
            () -> runContextFactoryRef[0],
            new EncryptionConfig(null),
            new DisabledReusableInputsExpander()
        );
        ApplicationContext runContextBeanLocator = Mockito.mock(ApplicationContext.class, invocation ->
        {
            if ("getBean".equals(invocation.getMethod().getName()) && invocation.getArguments().length == 1) {
                Object beanType = invocation.getArgument(0);
                if (TracerFactory.class.equals(beanType)) {
                    return tracerFactory;
                }
                if (FlowInputOutput.class.equals(beanType)) {
                    return flowInputOutput;
                }
                if (Validator.class.equals(beanType)) {
                    return validator;
                }
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
        this.runContextFactory = new KitRunContextFactory(renderer, runContextLoggerFactory, metricRegistry, taskOutputService, runContextBeanLocator);
        runContextFactoryRef[0] = runContextFactory;
        WorkerQueueService workerQueueService = new WorkerQueueService.Default();

        // the executor-facing ExecutionService methods are pure and never touch its injected fields
        this.executionService = Mockito.mock(ExecutionService.class, Mockito.CALLS_REAL_METHODS);
        // every evaluate overload defaults to PASS; tests re-stub the overload they exercise
        this.killSwitchService = Mockito.mock(
            KillSwitchService.class,
            invocation -> "evaluate".equals(invocation.getMethod().getName()) ? EvaluationType.PASS : Mockito.RETURNS_DEFAULTS.answer(invocation)
        );
        this.killSwitchActionService = Mockito.mock(KillSwitchActionService.class);
        this.workerTaskResultListener = Mockito.mock(WorkerTaskResultListener.class);
        // a spy so tests can stub namespace/tenant limits while the OSS flow-scope default stays real
        this.concurrencyLimitResolver = Mockito.spy(new ConcurrencyLimitResolver());
        this.quotaService = Mockito.mock(QuotaService.class);
        this.asyncOperationService = Mockito.mock(AsyncOperationService.class);
        this.flowTriggerService = Mockito.mock(FlowTriggerService.class);
        this.multipleConditionStateStore = Mockito.mock(MultipleConditionStateStore.class);

        this.executorService = new ExecutorService(
            runContextFactory,
            metricRegistry,
            flowMetaStore,
            executionService,
            workerQueueService,
            new SLAService(),
            Optional.empty(),
            killQueue,
            loopExecutionEventQueue,
            runContextLoggerFactory,
            new AssetService.NoopAssetService(),
            Mockito.mock(RunContextInitializer.class),
            taskOutputService,
            new PausedTaskNotifier.NoopPausedTaskNotifier()
        );

        this.executionEventMessageHandler = new ExecutionEventMessageHandler(
            executionStateStore,
            executionQueuedStateStore,
            executionDelayStateStore,
            slaMonitorStateStore,
            concurrencyLimitStateStore,
            concurrencyLimitResolver,
            executorService,
            workerQueueService,
            quotaService,
            flowMetaStore,
            workerJobEventQueue,
            subflowExecutionResultQueue,
            executionQueue,
            runContextLoggerFactory,
            killSwitchService,
            killSwitchActionService,
            metricRegistry,
            tracerFactory
        );
        this.executionCommandMessageHandler = new ExecutionCommandMessageHandler(
            executionService,
            executionStateStore,
            flowMetaStore,
            taskOutputService,
            asyncOperationService,
            executionEventMessageHandler,
            killSwitchService,
            killSwitchActionService
        );
        this.workerTaskResultMessageHandler = new WorkerTaskResultMessageHandler(
            executionStateStore,
            executorService,
            flowMetaStore,
            killSwitchService,
            killSwitchActionService,
            List.of(workerTaskResultListener)
        );
        this.executionKilledExecutionMessageHandler = new ExecutionKilledExecutionMessageHandler(
            executorService,
            executionService,
            executionStateStore,
            executionQueuedStateStore,
            metricRegistry,
            flowMetaStore,
            killQueue,
            asyncOperationService,
            killSwitchService
        );
        this.subflowExecutionResultMessageHandler = new SubflowExecutionResultMessageHandler(
            executorService,
            metricRegistry,
            executionService,
            executionStateStore,
            taskOutputService,
            killSwitchService
        );
        this.subflowExecutionEndMessageHandler = new SubflowExecutionEndMessageHandler(
            executorService,
            executionStateStore,
            flowMetaStore,
            runContextFactory,
            subflowExecutionResultQueue,
            killSwitchService
        );
        this.loopExecutionEventMessageHandler = new LoopExecutionEventMessageHandler(
            executorService,
            executionService,
            taskOutputService,
            executionStateStore,
            runContextFactory,
            flowMetaStore,
            executionQueue,
            followExecutionEventQueue,
            killSwitchService,
            runContextLoggerFactory
        );
        this.multipleConditionEventMessageHandler = new MultipleConditionEventMessageHandler(
            flowTriggerService,
            multipleConditionStateStore,
            executionCommandQueue
        );
        this.executionDelayProcessor = new ExecutionDelayProcessor(
            executionDelayStateStore,
            executionStateStore,
            flowMetaStore,
            executionService,
            executorService,
            metricRegistry
        );
        this.concurrencySlotReleaseProcessor = new ConcurrencySlotReleaseProcessor(
            concurrencyLimitStateStore,
            concurrencyLimitResolver,
            executionQueuedStateStore,
            flowMetaStore,
            metricRegistry
        );
        this.slaMonitorProcessor = new SLAMonitorProcessor(
            slaMonitorStateStore,
            executionStateStore,
            flowMetaStore,
            executionService,
            executorService,
            new SLAService(),
            runContextFactory,
            metricRegistry
        );
    }

    /**
     * Register a flow so the executor can resolve it (event handling, subflow lookups, restarts).
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
     * One production message cycle: fresh context, {@code process()}, then {@code onNext()}.
     */
    private ExecutorContext cycle(FlowWithSource flow, Execution execution) {
        ExecutorContext context = new ExecutorContext(execution, flow);
        context = executorService.process(context);

        if (context.getNextCount() > 0) {
            context.withExecution(
                executorService.onNext(context.getExecution(), context.getNextCount()),
                "onNext"
            );
        }

        return context;
    }

    // --- real production objects

    public ExecutorService executorService() {
        return executorService;
    }

    public ExecutionEventMessageHandler executionEventMessageHandler() {
        return executionEventMessageHandler;
    }

    public ExecutionCommandMessageHandler executionCommandMessageHandler() {
        return executionCommandMessageHandler;
    }

    public WorkerTaskResultMessageHandler workerTaskResultMessageHandler() {
        return workerTaskResultMessageHandler;
    }

    public ExecutionKilledExecutionMessageHandler executionKilledExecutionMessageHandler() {
        return executionKilledExecutionMessageHandler;
    }

    public SubflowExecutionResultMessageHandler subflowExecutionResultMessageHandler() {
        return subflowExecutionResultMessageHandler;
    }

    public SubflowExecutionEndMessageHandler subflowExecutionEndMessageHandler() {
        return subflowExecutionEndMessageHandler;
    }

    public LoopExecutionEventMessageHandler loopExecutionEventMessageHandler() {
        return loopExecutionEventMessageHandler;
    }

    public MultipleConditionEventMessageHandler multipleConditionEventMessageHandler() {
        return multipleConditionEventMessageHandler;
    }

    public ConcurrencySlotReleaseProcessor concurrencySlotReleaseProcessor() {
        return concurrencySlotReleaseProcessor;
    }

    public SLAMonitorProcessor slaMonitorProcessor() {
        return slaMonitorProcessor;
    }

    public ExecutionDelayProcessor executionDelayProcessor() {
        return executionDelayProcessor;
    }

    // --- in-memory fakes (state seeding + assertion channels)

    public InMemoryExecutionStateStore executionStateStore() {
        return executionStateStore;
    }

    public InMemoryExecutionQueuedStateStore executionQueuedStateStore() {
        return executionQueuedStateStore;
    }

    public InMemoryExecutionDelayStateStore executionDelayStateStore() {
        return executionDelayStateStore;
    }

    public InMemorySLAMonitorStateStore slaMonitorStateStore() {
        return slaMonitorStateStore;
    }

    public InMemoryConcurrencyLimitStateStore concurrencyLimitStateStore() {
        return concurrencyLimitStateStore;
    }

    public InMemoryTaskOutputRepository taskOutputRepository() {
        return taskOutputRepository;
    }

    public List<ExecutionKilled> kills() {
        return killQueue.emitted();
    }

    public List<LoopExecutionEvent> loopEvents() {
        return loopExecutionEventQueue.emitted();
    }

    public RecordingKeyedDispatchQueue<WorkerJobEvent> workerJobEventQueue() {
        return workerJobEventQueue;
    }

    public RecordingDispatchQueue<SubflowExecutionResult> subflowExecutionResultQueue() {
        return subflowExecutionResultQueue;
    }

    public RecordingDispatchQueue<Execution> executionQueue() {
        return executionQueue;
    }

    public RecordingBroadcastQueue<FollowExecutionEvent> followExecutionEventQueue() {
        return followExecutionEventQueue;
    }

    public RecordingDispatchQueue<ExecutionCommand> executionCommandQueue() {
        return executionCommandQueue;
    }

    public List<LogEntry> logs() {
        return logEmitter.emitted();
    }

    // --- mocks exposed for per-test stubbing (Mockito)

    public KillSwitchService killSwitchService() {
        return killSwitchService;
    }

    public KillSwitchActionService killSwitchActionService() {
        return killSwitchActionService;
    }

    public WorkerTaskResultListener workerTaskResultListener() {
        return workerTaskResultListener;
    }

    public ConcurrencyLimitResolver concurrencyLimitResolver() {
        return concurrencyLimitResolver;
    }

    public QuotaService quotaService() {
        return quotaService;
    }

    public AsyncOperationService asyncOperationService() {
        return asyncOperationService;
    }

    public FlowTriggerService flowTriggerService() {
        return flowTriggerService;
    }

    public MultipleConditionStateStore multipleConditionStateStore() {
        return multipleConditionStateStore;
    }

    public ExecutionService executionService() {
        return executionService;
    }

    /**
     * The kit's {@link KitRunContextFactory} — for hand-wiring executor collaborators
     * (e.g. {@code FlowTriggerService}) outside the harness in service-level tests.
     */
    public KitRunContextFactory runContextFactory() {
        return runContextFactory;
    }
}
