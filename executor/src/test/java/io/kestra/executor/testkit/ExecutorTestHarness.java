package io.kestra.executor.testkit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.Map;
import java.util.Optional;

import org.mockito.Mockito;

import io.kestra.core.assets.AssetService;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.encryption.EncryptionConfig;
import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.LoopExecutionEvent;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.queues.event.Event;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.namespace.NamespaceFileMetadataStateStore;
import io.kestra.core.runners.DisabledReusableInputsExpander;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.runners.LocalPathFactory;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.runners.PausedTaskNotifier;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.runners.configuration.ExecutionDepthConfiguration;
import io.kestra.core.runners.configuration.LocalFilesConfiguration;
import io.kestra.core.runners.configuration.LoggingConfiguration;
import io.kestra.core.runners.configuration.VariableConfiguration;
import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.ConcurrencyLimitResolver;
import io.kestra.core.services.ExecutionOutputService;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.services.QuotaService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.services.WorkerQueueService;
import io.kestra.core.services.configuration.ExecutionOutputConfiguration;
import io.kestra.core.services.configuration.TaskOutputConfiguration;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.executor.ConcurrencySlotReleaseProcessor;
import io.kestra.executor.DefaultExecutor;
import io.kestra.executor.ExecutionDelayProcessor;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorCore;
import io.kestra.executor.ExecutorService;
import io.kestra.executor.FlowTriggerService;
import io.kestra.executor.KillSwitchActionService;
import io.kestra.executor.SLAMonitorProcessor;
import io.kestra.executor.SLAService;
import io.kestra.executor.configuration.ExecutorConfiguration;
import io.kestra.executor.handler.ExecutionCommandMessageHandler;
import io.kestra.executor.handler.ExecutionEventMessageHandler;
import io.kestra.executor.handler.ExecutionKilledExecutionMessageHandler;
import io.kestra.executor.handler.LoopExecutionEventMessageHandler;
import io.kestra.executor.handler.MultipleConditionEventMessageHandler;
import io.kestra.executor.handler.SubflowExecutionEndMessageHandler;
import io.kestra.executor.handler.SubflowExecutionResultMessageHandler;
import io.kestra.executor.handler.WorkerTaskResultListener;
import io.kestra.executor.handler.WorkerTaskResultMessageHandler;

import com.google.common.util.concurrent.MoreExecutors;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.validation.Validator;

/**
 * Composition root for executor unit tests: the real {@link ExecutorService}, message handlers,
 * processors and {@link DefaultExecutor} wired over in-memory fakes — no Micronaut context, no
 * database, no threads. Collaborators without executor logic ({@link KillSwitchService},
 * {@link QuotaService}, {@link AsyncOperationService}, {@link FlowTriggerService},
 * {@link KillSwitchActionService}) are Mockito mocks exposed for per-test stubbing.
 */
public final class ExecutorTestHarness {
    private static final int MAX_STEPS = 500;
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
    private final List<Runnable> loops = new ArrayList<>();
    private final List<Trace.Emission> journal = new ArrayList<>();
    private int workerJobsAnswered = 0;

    // in-memory fakes
    private final InMemoryFlowMetaStore flowMetaStore;
    private final InMemoryExecutionStateStore executionStateStore;
    private final InMemoryExecutionQueuedStateStore executionQueuedStateStore;
    private final InMemoryExecutionDelayStateStore executionDelayStateStore;
    private final InMemorySLAMonitorStateStore slaMonitorStateStore;
    private final InMemoryConcurrencyLimitStateStore concurrencyLimitStateStore;
    private final InMemoryTaskOutputRepository taskOutputRepository;
    private final InMemoryExecutionOutputRepository executionOutputRepository;
    private final ExecutionOutputService executionOutputService;
    private final RecordingBroadcastQueue<ExecutionKilled> killQueue;
    private final RecordingDispatchQueue<LoopExecutionEvent> loopExecutionEventQueue;
    private final RecordingKeyedDispatchQueue<WorkerJobEvent> workerJobEventQueue;
    private final RecordingDispatchQueue<SubflowExecutionResult> subflowExecutionResultQueue;
    private final RecordingDispatchQueue<Execution> executionQueue;
    private final RecordingBroadcastQueue<FollowExecutionEvent> followExecutionEventQueue;
    private final RecordingDispatchQueue<ExecutionCommand> executionCommandQueue;
    private final RecordingDispatchQueue<ExecutionEvent> executionEventQueue;
    private final RecordingDispatchQueue<WorkerTaskResult> workerTaskResultQueue;
    private final RecordingDispatchQueue<SubflowExecutionEnd> subflowExecutionEndQueue;
    private final RecordingDispatchQueue<MultipleConditionEvent> multipleConditionEventQueue;
    private final RecordingDispatchQueue<ExecutionStatistic> executionStatisticQueue;
    private final List<RecordingQueue<?>> queues;
    private final RecordingLogEntryEmitter logEmitter;

    // mocks exposed for per-test stubbing
    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;
    private final WorkerTaskResultListener workerTaskResultListener;
    private final WorkerJobRunningStateStore workerJobRunningStateStore;
    private final ConcurrencyLimitResolver concurrencyLimitResolver;
    private final QuotaService quotaService;
    private final AsyncOperationService asyncOperationService;
    private final FlowTriggerService flowTriggerService;
    private final MultipleConditionStateStore multipleConditionStateStore;
    private final ExecutionService executionService;
    private final KitRunContextFactory runContextFactory;
    private final MutableClock clock;

    public static ExecutorTestHarness create() {
        return new ExecutorTestHarness();
    }

    private ExecutorTestHarness() {
        this.clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        this.flowMetaStore = new InMemoryFlowMetaStore();
        this.executionStateStore = new InMemoryExecutionStateStore();
        this.executionQueuedStateStore = new InMemoryExecutionQueuedStateStore();
        this.executionDelayStateStore = new InMemoryExecutionDelayStateStore();
        this.slaMonitorStateStore = new InMemorySLAMonitorStateStore();
        this.concurrencyLimitStateStore = new InMemoryConcurrencyLimitStateStore();
        this.taskOutputRepository = new InMemoryTaskOutputRepository();
        this.executionOutputRepository = new InMemoryExecutionOutputRepository();
        this.killQueue = new RecordingBroadcastQueue<>("kill", journal);
        this.loopExecutionEventQueue = new RecordingDispatchQueue<>("loopExecutionEvent", journal);
        this.workerJobEventQueue = new RecordingKeyedDispatchQueue<>("workerJobEvent", journal);
        this.subflowExecutionResultQueue = new RecordingDispatchQueue<>("subflowExecutionResult", journal);
        this.executionQueue = new RecordingDispatchQueue<>("execution", journal);
        this.followExecutionEventQueue = new RecordingBroadcastQueue<>("followExecutionEvent", journal);
        this.executionCommandQueue = new RecordingDispatchQueue<>("executionCommand", journal);
        this.executionEventQueue = new RecordingDispatchQueue<>("executionEvent", journal);
        this.workerTaskResultQueue = new RecordingDispatchQueue<>("workerTaskResult", journal);
        this.subflowExecutionEndQueue = new RecordingDispatchQueue<>("subflowExecutionEnd", journal);
        this.multipleConditionEventQueue = new RecordingDispatchQueue<>("multipleConditionEvent", journal);
        this.executionStatisticQueue = new RecordingDispatchQueue<>("executionStatistic", journal);
        TriggerEventQueue triggerEventQueue = Mockito.mock(TriggerEventQueue.class);
        Mockito.doAnswer(invocation -> journal.add(new Trace.Emission(journal.size(), "triggerEvent", invocation.getArgument(0)))).when(triggerEventQueue).send(Mockito.any());
        this.queues = List.of(
            executionQueue, executionEventQueue, workerTaskResultQueue, executionCommandQueue, subflowExecutionResultQueue,
            subflowExecutionEndQueue, multipleConditionEventQueue, loopExecutionEventQueue, killQueue
        );
        this.logEmitter = new RecordingLogEntryEmitter();

        MetricRegistry metricRegistry = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig(null, null, null, Map.of()));
        RunContextLoggerFactory runContextLoggerFactory = new RunContextLoggerFactory(logEmitter, new LoggingConfiguration(null));
        TaskOutputService taskOutputService = new TaskOutputService(
            taskOutputRepository,
            Mockito.mock(StorageInterface.class),
            new NamespaceFactory(Mockito.mock(NamespaceFileMetadataStateStore.class)),
            new TaskOutputConfiguration(-1)
        );
        this.executionOutputService = new ExecutionOutputService(
            executionOutputRepository,
            Mockito.mock(StorageInterface.class),
            new NamespaceFactory(Mockito.mock(NamespaceFileMetadataStateStore.class)),
            new ExecutionOutputConfiguration(-1)
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
            new DisabledReusableInputsExpander(),
            new LocalPathFactory(new LocalFilesConfiguration(List.of(), false, false))
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
                if (ExecutionDepthConfiguration.class.equals(beanType)) {
                    return new ExecutionDepthConfiguration(100);
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
        this.workerJobRunningStateStore = Mockito.mock(WorkerJobRunningStateStore.class);
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
            executionOutputService,
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
            tracerFactory,
            clock
        );
        this.executionCommandMessageHandler = new ExecutionCommandMessageHandler(
            executionService,
            executionStateStore,
            flowMetaStore,
            taskOutputService,
            executionOutputService,
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

        ExecutorCore executorCore = new ExecutorCore(
            executorService,
            executionService,
            flowTriggerService,
            flowMetaStore,
            executionStateStore,
            slaMonitorStateStore,
            concurrencySlotReleaseProcessor,
            executionDelayProcessor,
            slaMonitorProcessor,
            runContextFactory,
            killSwitchService,
            killSwitchActionService,
            metricRegistry,
            clock,
            executionQueue,
            executionEventQueue,
            followExecutionEventQueue,
            subflowExecutionEndQueue,
            multipleConditionEventQueue,
            loopExecutionEventQueue,
            executionStatisticQueue,
            triggerEventQueue,
            execution -> journal.add(new Trace.Emission(journal.size(), "executionTerminated", execution)),
            workerJobRunningStateStore,
            executionCommandMessageHandler,
            executionEventMessageHandler,
            workerTaskResultMessageHandler,
            executionKilledExecutionMessageHandler,
            subflowExecutionResultMessageHandler,
            subflowExecutionEndMessageHandler,
            multipleConditionEventMessageHandler,
            loopExecutionEventMessageHandler
        );

        // the production DefaultExecutor over same-thread pools and hand-ticked loops
        ScheduledExecutorService scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(scheduledExecutorService.scheduleAtFixedRate(Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.any())).thenAnswer(invocation -> {
            loops.add(invocation.getArgument(0));
            return Mockito.mock(ScheduledFuture.class);
        });
        ExecutorsUtils executorsUtils = Mockito.mock(ExecutorsUtils.class);
        Mockito.when(executorsUtils.maxCachedThreadPool(Mockito.anyInt(), Mockito.anyString())).thenAnswer(invocation -> MoreExecutors.newDirectExecutorService());
        Mockito.when(executorsUtils.singleThreadScheduledExecutor(Mockito.anyString())).thenReturn(scheduledExecutorService);
        KestraContext kestraContext = Mockito.mock(KestraContext.class);
        Mockito.when(kestraContext.getAllocatedCpuCores()).thenReturn(1);
        @SuppressWarnings("unchecked")
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        DefaultExecutor executor = new DefaultExecutor(
            eventPublisher,
            executorsUtils,
            clock,
            new ExecutorConfiguration(1, 1000, 1000, 60000),
            kestraContext,
            executionQueue,
            executionCommandQueue,
            executionEventQueue,
            workerTaskResultQueue,
            killQueue,
            subflowExecutionResultQueue,
            subflowExecutionEndQueue,
            multipleConditionEventQueue,
            loopExecutionEventQueue,
            executorCore,
            new MaintenanceService.NoopMaintenanceService(),
            multipleConditionStateStore,
            metricRegistry
        );
        executor.initMetrics();
        executor.run();
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

    // --- the whole machine: the real DefaultExecutor, one delivery at a time

    /** Puts {@code message} on its queue and delivers it to the executor; returns what the executor emitted in return. */
    public List<Trace.Emission> step(Object message) {
        RecordingQueue<?> queue = enqueue(message);
        int from = journal.size();
        queue.deliverNext();
        return emittedSince(from);
    }

    /** Fires the execution-delay loop at {@code now}. */
    public List<Trace.Emission> tickExecutionDelays(Instant now) {
        clock.set(now);
        int from = journal.size();
        loops.getFirst().run();
        return emittedSince(from);
    }

    public Trace run(Execution execution, ScriptedWorker worker) {
        return run(List.of(execution), worker);
    }

    /**
     * Drains every queue the executor subscribed to, one delivery at a time in subscription order,
     * while {@code worker} answers each dispatched task. Stops when nothing is pending.
     */
    public Trace run(List<?> messages, ScriptedWorker worker) {
        messages.forEach(this::enqueue);
        List<Trace.Step> steps = new ArrayList<>();
        for (int i = 0; i < MAX_STEPS; i++) {
            answerWorkerJobs(worker);
            RecordingQueue<?> queue = queues.stream().filter(RecordingQueue::isSubscribed).filter(RecordingQueue::hasPending).findFirst().orElse(null);
            if (queue == null) {
                return new Trace(steps);
            }
            Object message = queue.peekPending();
            int from = journal.size();
            queue.deliverNext();
            steps.add(new Trace.Step(queue.queueName(), message, emittedSince(from)));
        }
        throw new IllegalStateException("Executor did not quiesce after " + MAX_STEPS + " deliveries — possible execution loop");
    }

    private List<Trace.Emission> emittedSince(int from) {
        return List.copyOf(journal.subList(from, journal.size()));
    }

    private void answerWorkerJobs(ScriptedWorker worker) {
        List<WorkerJobEvent> jobs = workerJobEventQueue.emittedMessages();
        for (; workerJobsAnswered < jobs.size(); workerJobsAnswered++) {
            workerTaskResultQueue.emit(worker.run((WorkerTask) jobs.get(workerJobsAnswered).job()));
        }
    }

    private RecordingQueue<?> enqueue(Object message) {
        return switch (message) {
            case Execution e -> put(executionQueue, e);
            case ExecutionEvent e -> put(executionEventQueue, e);
            case WorkerTaskResult r -> put(workerTaskResultQueue, r);
            case ExecutionKilled k -> put(killQueue, k);
            case SubflowExecutionResult r -> put(subflowExecutionResultQueue, r);
            case SubflowExecutionEnd e -> put(subflowExecutionEndQueue, e);
            case LoopExecutionEvent e -> put(loopExecutionEventQueue, e);
            case MultipleConditionEvent e -> put(multipleConditionEventQueue, e);
            case ExecutionCommand c -> put(executionCommandQueue, c);
            default -> throw new IllegalArgumentException("Not an executor message: " + message.getClass().getName());
        };
    }

    private static <T extends Event> RecordingQueue<T> put(RecordingQueue<T> queue, T message) {
        queue.emit(message);
        return queue;
    }

    // --- saga verbs

    /**
     * Creates and persists a fresh execution of {@code flow} and runs its CREATED event through the
     * real {@code ExecutionEventMessageHandler} — the exact path a webserver or scheduler submission takes.
     */
    public ExecutorContext start(FlowWithSource flow) {
        return handle(Executions.created(flow));
    }

    /**
     * Persists {@code execution} if the store does not know it yet and runs its CREATED event through
     * the real handler. Returns the cycle's decision.
     */
    public ExecutorContext handle(Execution execution) {
        if (executionStateStore.findByIdWithoutAcl(execution.getId()) == null) {
            executionStateStore.save(execution);
        }
        return executionEventMessageHandler.handle(new ExecutionEvent(execution, ExecutionEventType.CREATED)).orElseThrow();
    }

    /** The persisted state of {@code execution}, as the store has it now. */
    public State.Type stateOf(Execution execution) {
        return executionStateStore.findByIdWithoutAcl(execution.getId()).getState().getCurrent();
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

    public InMemoryFlowMetaStore flowMetaStore() {
        return flowMetaStore;
    }

    public InMemoryExecutionOutputRepository executionOutputRepository() {
        return executionOutputRepository;
    }

    public ExecutionOutputService executionOutputService() {
        return executionOutputService;
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

    public WorkerJobRunningStateStore workerJobRunningStateStore() {
        return workerJobRunningStateStore;
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
    public MutableClock clock() {
        return clock;
    }

    public KitRunContextFactory runContextFactory() {
        return runContextFactory;
    }
}
