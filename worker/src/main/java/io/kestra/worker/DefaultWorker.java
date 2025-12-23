package io.kestra.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Label;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.*;
import io.kestra.core.queues.*;
import io.kestra.core.runners.*;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.*;
import io.kestra.core.services.LabelService;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.services.VariablesService;
import io.kestra.core.services.WorkerGroupService;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.trace.TraceUtils;
import io.kestra.core.trace.Tracer;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.utils.*;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.kestra.core.models.flows.State.Type.*;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_FORCED;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_GRACEFULLY;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuppressWarnings("this-escape")
@Slf4j
@Introspected
public class DefaultWorker implements Worker {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final String SERVICE_PROPS_WORKER_GROUP = "worker.group";

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    private WorkerJobQueueInterface workerJobQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
    private QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    private QueueInterface<ExecutionKilled> executionKilledQueue;

    @Inject
    @Named(QueueFactoryInterface.METRIC_QUEUE)
    private QueueInterface<MetricEntry> metricEntryQueue;

    @Inject
    @Named(QueueFactoryInterface.TRIGGER_NAMED)
    private QueueInterface<Trigger> triggerQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    @Named(QueueFactoryInterface.CLUSTER_EVENT_NAMED)
    private Optional<QueueInterface<ClusterEvent>> clusterEventQueue;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private ServerConfig serverConfig;

    @Inject
    private RunContextInitializer runContextInitializer;

    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    private WorkerSecurityService workerSecurityService;

    @Inject
    private VariablesService variablesService;

    private final Set<String> killedExecution = ConcurrentHashMap.newKeySet();

    @Getter
    private final Map<Long, AtomicInteger> metricRunningCount = new ConcurrentHashMap<>();

    @VisibleForTesting
    @Getter
    private final Map<String, AtomicInteger> evaluateTriggerRunningCount = new ConcurrentHashMap<>();

    private final List<AbstractWorkerCallable> workerCallableReferences = new ArrayList<>();

    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AtomicBoolean skipGracefulTermination = new AtomicBoolean(false);

    @Getter
    private final String workerGroup;
    private final String workerGroupKey;

    private final String id;

    private final ExecutorService executorService;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean init = new AtomicBoolean(false);

    private final AtomicReference<ServiceState> state = new AtomicReference<>();

    private final List<Runnable> receiveCancellations = new ArrayList<>();

    @Getter
    private final Integer numThreads;
    private final AtomicInteger pendingJobCount = new AtomicInteger(0);
    private final AtomicInteger runningJobCount = new AtomicInteger(0);

    @Inject
    private TracerFactory tracerFactory;
    private Tracer tracer;

    @Inject
    private MaintenanceService maintenanceService;

    /**
     * Creates a new {@link DefaultWorker} instance.
     *
     * @param workerId       The worker service ID.
     * @param numThreads     The worker num threads.
     * @param workerGroupKey The worker group (EE).
     */
    @Inject
    public DefaultWorker(
        @Parameter String workerId,
        @Parameter Integer numThreads,
        @Nullable @Parameter String workerGroupKey,
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        WorkerGroupService workerGroupService,
        ExecutorsUtils executorsUtils
    ) {
        this.id = workerId;
        this.numThreads = numThreads;
        this.workerGroupKey = workerGroupKey;
        this.workerGroup = workerGroupService.resolveGroupFromKey(workerGroupKey);
        this.eventPublisher = eventPublisher;
        this.executorService = executorsUtils.maxCachedThreadPool(numThreads, EXECUTOR_NAME);
        this.setState(ServiceState.CREATED);
    }

    @PostConstruct
    void initMetricsAndTracer() {
        // the method is called twice due to how we create the bean, see https://github.com/micronaut-projects/micronaut-core/issues/11656
        if (this.init.compareAndSet(false, true)) {
            String[] tags = this.workerGroup == null ? new String[0] : new String[]{MetricRegistry.TAG_WORKER_GROUP, this.workerGroup};
            // create metrics to store thread count, pending jobs and running jobs, so we can have autoscaling easily
            this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT, MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT_DESCRIPTION, numThreads, tags);
            this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_JOB_PENDING_COUNT, MetricRegistry.METRIC_WORKER_JOB_PENDING_COUNT_DESCRIPTION, pendingJobCount, tags);
            this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_JOB_RUNNING_COUNT, MetricRegistry.METRIC_WORKER_JOB_RUNNING_COUNT_DESCRIPTION, runningJobCount, tags);

            this.tracer = tracerFactory.getTracer(DefaultWorker.class, "WORKER");
        }
    }

    @Override
    public Set<Metric> getMetrics() {
        if (this.metricRegistry == null) {
            // can arrive if called before the instance is fully created
            return Collections.emptySet();
        }

        Stream<String> metrics = Stream.of(
            MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT,
            MetricRegistry.METRIC_WORKER_JOB_PENDING_COUNT,
            MetricRegistry.METRIC_WORKER_JOB_RUNNING_COUNT
        );

        return metrics
            .flatMap(metric -> Optional.ofNullable(metricRegistry.findGauge(metric)).stream())
            .map(Metric::of)
            .collect(Collectors.toSet());
    }

    @Override
    public void run() {
        this.receiveCancellations.addFirst(this.executionKilledQueue.receive(executionKilled -> {
            if (executionKilled == null || !executionKilled.isLeft()) {
                return;
            }

            ExecutionKilled.State state = executionKilled.getLeft().getState();

            if (state != null && state != ExecutionKilled.State.EXECUTED) {
                return;
            }

            metricRegistry
                .counter(MetricRegistry.METRIC_WORKER_KILLED_COUNT, MetricRegistry.METRIC_WORKER_KILLED_COUNT_DESCRIPTION, metricRegistry.tags(executionKilled.getLeft()))
                .increment();

            synchronized (this) {
                if (executionKilled.getLeft() instanceof ExecutionKilledExecution executionKilledExecution) {
                    killedExecution.add(executionKilledExecution.getExecutionId());

                    workerCallableReferences
                        .stream()
                        .filter(workerCallable -> workerCallable instanceof WorkerTaskCallable)
                        .map(workerCallable -> (WorkerTaskCallable) workerCallable)
                        .filter(workerCallable -> executionKilledExecution.isEqual(workerCallable.getWorkerTask()))
                        .forEach(AbstractWorkerCallable::kill);
                } else if (executionKilled.getLeft() instanceof ExecutionKilledTrigger executionKilledTrigger) {
                    workerCallableReferences
                        .stream()
                        .filter(workerCallable -> workerCallable instanceof AbstractWorkerTriggerCallable)
                        .map(workerCallable -> (AbstractWorkerTriggerCallable) workerCallable)
                        .filter(workerCallable -> executionKilledTrigger.isEqual(workerCallable.getWorkerTrigger().getTriggerContext()))
                        .forEach(AbstractWorkerCallable::kill);
                }
            }
        }));

        this.receiveCancellations.addFirst(this.workerJobQueue.subscribe(
            this.id,
            this.workerGroup,
            either -> {
                pendingJobCount.incrementAndGet();
                executorService.execute(() -> {
                    pendingJobCount.decrementAndGet();
                    runningJobCount.incrementAndGet();

                    try {
                        if (either.isRight()) {
                            log.error("Unable to deserialize a worker job: {}", either.getRight().getMessage());
                            handleDeserializationError(either.getRight());
                            return;
                        }

                        WorkerJob workerTask = either.getLeft();
                        if (workerTask instanceof WorkerTask task) {
                            handleTask(task);
                        } else if (workerTask instanceof WorkerTrigger trigger) {
                            handleTrigger(trigger);
                        }
                    } finally {
                        runningJobCount.decrementAndGet();
                    }
                });
            }
        ));

        this.clusterEventQueue.ifPresent(clusterEventQueueInterface -> this.receiveCancellations.addFirst(clusterEventQueueInterface.receive(this::clusterEventQueue)));
        if (this.maintenanceService.isInMaintenanceMode()) {
            enterMaintenance();
        } else {
            setState(ServiceState.RUNNING);
        }

        if (workerGroupKey != null) {
            log.info("Worker started with {} thread(s) in group '{}'", numThreads, workerGroupKey);
        }
        else {
            log.info("Worker started with {} thread(s)", numThreads);
        }
    }

    private void clusterEventQueue(Either<ClusterEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a cluster event: {}", either.getRight().getMessage());
            return;
        }

        ClusterEvent clusterEvent = either.getLeft();
        log.info("Cluster event received: {}", clusterEvent);
        switch (clusterEvent.eventType()) {
            case MAINTENANCE_ENTER -> enterMaintenance();
            case MAINTENANCE_EXIT -> exitMaintenance();
        }
    }

    private void enterMaintenance() {
        this.executionKilledQueue.pause();
        this.workerJobQueue.pause();

        this.setState(ServiceState.MAINTENANCE);
    }

    private void exitMaintenance() {
        this.executionKilledQueue.resume();
        this.workerJobQueue.resume();

        this.setState(ServiceState.RUNNING);
    }

    private void setState(final ServiceState state) {
        this.state.set(state);
        Map<String, Object> properties = new HashMap<>();
        properties.put(SERVICE_PROPS_WORKER_GROUP, workerGroup);
        eventPublisher.publishEvent(new ServiceStateChangeEvent(this, properties));
    }

    private void handleDeserializationError(DeserializationException deserializationException) {
        if (deserializationException.getRecord() != null) {
            try {
                var json = MAPPER.readTree(deserializationException.getRecord());
                var type = json.get("type") != null ? json.get("type").asText() : null;
                if ("task".equals(type)) {
                    // try to deserialize the taskRun to fail it
                    var taskRun = MAPPER.treeToValue(json.get("taskRun"), TaskRun.class);
                    this.workerTaskResultQueue.emit(new WorkerTaskResult(taskRun.fail()));
                } else if ("trigger".equals(type)) {
                    // try to deserialize the triggerContext to fail it
                    var triggerContext = MAPPER.treeToValue(json.get("triggerContext"), TriggerContext.class);
                    var workerTriggerResult = WorkerTriggerResult.builder().triggerContext(triggerContext).execution(Optional.empty()).build();
                    this.workerTriggerResultQueue.emit(workerTriggerResult);
                }
            } catch (IOException | QueueException e) {
                // ignore the message if we cannot do anything about it
                log.error("Unexpected exception when trying to handle a deserialization error", e);
            }
        }
    }

    private void handleTask(final WorkerTask workerTask) {
        if (workerTask.getTask() instanceof RunnableTask) {
            this.run(workerTask, true);
        } else if (workerTask.getTask() instanceof WorkingDirectory workingDirectory) {

            DefaultRunContext runContext = runContextInitializer.forWorkingDirectory(((DefaultRunContext) workerTask.getRunContext()), workerTask);
            final RunContext workingDirectoryRunContext = runContext.clone();

            try {
                // preExecuteTasks
                try {
                    workingDirectory.preExecuteTasks(workingDirectoryRunContext, workerTask.getTaskRun());
                } catch (Exception e) {
                    workingDirectoryRunContext.logger().error("Failed preExecuteTasks on WorkingDirectory: {}", e.getMessage(), e);
                    WorkerTask failed = workerTask.withTaskRun(workerTask.fail());
                    try {
                        this.workerTaskResultQueue.emit(new WorkerTaskResult(failed.getTaskRun()));
                    } catch (QueueException ex) {
                        log.error("Unable to emit the worker task result for task {} taskrun {}", failed.getTask().getId(), failed.getTaskRun().getId(), e);
                    }
                    this.logTerminated(failed);
                    return;
                }

                // execute all tasks
                for (Task currentTask : workingDirectory.getTasks()) {
                    if (Boolean.TRUE.equals(currentTask.getDisabled())) {
                        continue;
                    }
                    WorkerTask currentWorkerTask = workingDirectory.workerTask(
                        workerTask.getTaskRun(),
                        currentTask,
                        runContext.cloneForPlugin(currentTask)
                    );

                    // all tasks will be handled immediately by the worker
                    WorkerTaskResult workerTaskResult = null;
                    try {
                        if (!TruthUtils.isTruthy(runContext.render(currentWorkerTask.getTask().getRunIf()))) {
                            workerTaskResult = new WorkerTaskResult(currentWorkerTask.getTaskRun().withState(SKIPPED).addAttempt(TaskRunAttempt.builder().workerId(this.id).state(new State().withState(SKIPPED)).build()));
                            this.workerTaskResultQueue.emit(workerTaskResult);
                        } else {
                            workerTaskResult = this.run(currentWorkerTask, false);
                        }
                    } catch (IllegalVariableEvaluationException e) {
                        RunContextLogger contextLogger = runContextLoggerFactory.create(currentWorkerTask);
                        contextLogger.logger().error("Failed evaluating runIf: {}", e.getMessage(), e);
                        try {
                            this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask.fail()));
                        } catch (QueueException ex) {
                            log.error("Unable to emit the worker task result for task {} taskrun {}", currentWorkerTask.getTask().getId(), currentWorkerTask.getTaskRun().getId(), e);
                        }
                    } catch (QueueException e) {
                        log.error("Unable to emit the worker task result for task {} taskrun {}", currentWorkerTask.getTask().getId(), currentWorkerTask.getTaskRun().getId(), e);
                    }

                    if (workerTaskResult == null || workerTaskResult.getTaskRun().getState().isFailed() && !currentWorkerTask.getTask().isAllowFailure()) {
                        break;
                    }

                    // create the next RunContext populated with the previous WorkerTaskResult
                    runContext = runContextInitializer.forWorker(runContext.clone(), workerTaskResult, workerTask.getTaskRun());
                }

                // postExecuteTasks
                try {
                    workingDirectory.postExecuteTasks(workingDirectoryRunContext, workerTask.getTaskRun());
                } catch (Exception e) {
                    workingDirectoryRunContext.logger().error("Failed postExecuteTasks on WorkingDirectory: {}", e.getMessage(), e);
                    try {
                        this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask.fail()));
                    } catch (QueueException ex) {
                        log.error("Unable to emit the worker task result for task {} taskrun {}", workerTask.getTask().getId(), workerTask.getTaskRun().getId(), e);
                    }
                }
            } finally {
                this.logTerminated(workerTask);
                runContext.cleanup();
            }
        } else {
            throw new RuntimeException("Unable to process the task '" + workerTask.getTask().getId() + "' as it's not a runnable task");
        }
    }

    private void publishTriggerExecution(WorkerTrigger workerTrigger, Optional<Execution> evaluate) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_EXECUTION_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_EXECUTION_COUNT_DESCRIPTION, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        if (log.isDebugEnabled()) {
            Logs.logTrigger(
                workerTrigger.getTriggerContext(),
                Level.DEBUG,
                "[type: {}] {}",
                workerTrigger.getTrigger().getType(),
                evaluate.map(execution -> "New execution '" + execution.getId() + "'").orElse("Empty evaluation")
            );
        }

        var flow = workerTrigger.getConditionContext().getFlow();
        if (flow.getLabels() != null) {
            evaluate = evaluate.map(execution -> {
                    List<Label> executionLabels = execution.getLabels() != null ? execution.getLabels() : new ArrayList<>();
                    executionLabels.addAll(LabelService.labelsExcludingSystem(flow));
                    return execution.withLabels(executionLabels);
                }
            );
        }

        try {
            this.workerTriggerResultQueue.emit(
                WorkerTriggerResult.builder()
                    .execution(evaluate)
                    .triggerContext(workerTrigger.getTriggerContext())
                    .trigger(workerTrigger.getTrigger())
                    .build()
            );
        } catch (QueueException e) {
            handleTriggerError(workerTrigger, e);
        }
    }

    private void handleTriggerError(WorkerTrigger workerTrigger, Throwable e) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT_DESCRIPTION, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        logError(workerTrigger, e);
        try {
            Execution execution = workerTrigger.getTrigger().isFailOnTriggerError() ? TriggerService.generateExecution(workerTrigger.getTrigger(), workerTrigger.getConditionContext(), workerTrigger.getTriggerContext(), (Output) null)
                .withState(FAILED) : null;
            if (execution != null) {
                try {
                    logQueue.emitAsync(RunContextLogger.logEntries(Execution.loggingEventFromException(e), LogEntry.of(execution)));
                } catch (QueueException ex) {
                    // fail silently
                }
            }
            this.workerTriggerResultQueue.emit(
                WorkerTriggerResult.builder()
                    .triggerContext(workerTrigger.getTriggerContext())
                    .trigger(workerTrigger.getTrigger())
                    .execution(Optional.ofNullable(execution))
                    .build()
            );
        } catch (QueueException ex) {
            log.error("Unable to send the worker trigger result {}.{}.{}",
                workerTrigger.getTriggerContext().getNamespace(), workerTrigger.getTriggerContext().getFlowId(), workerTrigger.getTriggerContext().getTriggerId(), ex);
        }
    }

    private void handleRealtimeTriggerError(WorkerTrigger workerTrigger, Throwable e) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT_DESCRIPTION, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        // We create a FAILED execution, so the user is aware that the realtime trigger failed to be created
        var execution = TriggerService
            .generateRealtimeExecution(workerTrigger.getTrigger(), workerTrigger.getConditionContext(), workerTrigger.getTriggerContext(), null)
            .withState(FAILED);

        // We create an ERROR log attached to the execution
        Logger logger = workerTrigger.getConditionContext().getRunContext().logger();
        Logs.logExecution(
            execution,
            logger,
            Level.ERROR,
            "[date: {}] Realtime trigger failed to be created in the worker with error: {}",
            workerTrigger.getTriggerContext().getDate(),
            e != null ? e.getMessage() : "unknown",
            e
        );
        if (logger.isTraceEnabled() && e != null) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }

        try {
            this.workerTriggerResultQueue.emit(
                WorkerTriggerResult.builder()
                    .execution(Optional.of(execution))
                    .triggerContext(workerTrigger.getTriggerContext())
                    .trigger(workerTrigger.getTrigger())
                    .build()
            );
        } catch (QueueException ex) {
            log.error("Unable to send the worker trigger result {}.{}.{}",
                workerTrigger.getTriggerContext().getNamespace(), workerTrigger.getTriggerContext().getFlowId(), workerTrigger.getTriggerContext().getTriggerId(), ex);
        }
    }

    private void handleTrigger(WorkerTrigger workerTrigger) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_STARTED_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_STARTED_COUNT_DESCRIPTION, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        // update the trigger so that it contains the workerId
        var trigger = workerTrigger.getTriggerContext();
        trigger.setWorkerId(this.id);
        try {
            triggerQueue.emit(trigger);
        } catch (QueueException e) {
            handleTriggerError(workerTrigger, e);
        }

        this.metricRegistry
            .timer(MetricRegistry.METRIC_WORKER_TRIGGER_DURATION, MetricRegistry.METRIC_WORKER_TRIGGER_DURATION_DESCRIPTION, metricRegistry.tags(workerTrigger, workerGroup))
            .record(() -> {
                    StopWatch stopWatch = new StopWatch();
                    stopWatch.start();

                    this.evaluateTriggerRunningCount.computeIfAbsent(workerTrigger.getTriggerContext().uid(), s -> metricRegistry
                        .gauge(MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT_DESCRIPTION, new AtomicInteger(0), metricRegistry.tags(workerTrigger, workerGroup)));
                    this.evaluateTriggerRunningCount.get(workerTrigger.getTriggerContext().uid()).addAndGet(1);

                    DefaultRunContext runContext = (DefaultRunContext) workerTrigger.getConditionContext().getRunContext();
                    runContextInitializer.forWorker(runContext, workerTrigger);
                    try {

                        Logs.logTrigger(
                            workerTrigger.getTriggerContext(),
                            runContext.logger(),
                            Level.INFO,
                            "Type {} started",
                            workerTrigger.getTrigger().getType()
                        );

                        if (workerTrigger.getTrigger() instanceof PollingTriggerInterface pollingTrigger) {
                            WorkerTriggerCallable workerCallable = new WorkerTriggerCallable(runContext, workerTrigger, pollingTrigger);
                            io.kestra.core.models.flows.State.Type state = callJob(workerCallable);

                            if (workerCallable.getException() != null || !state.equals(SUCCESS)) {
                                this.handleTriggerError(workerTrigger, workerCallable.getException());
                            }

                            if (!state.equals(FAILED)) {
                                this.publishTriggerExecution(workerTrigger, workerCallable.getEvaluate());
                            }
                        } else if (workerTrigger.getTrigger() instanceof RealtimeTriggerInterface streamingTrigger) {
                            WorkerTriggerRealtimeCallable workerCallable = new WorkerTriggerRealtimeCallable(
                                runContext,
                                workerTrigger,
                                streamingTrigger,
                                throwable -> this.handleTriggerError(workerTrigger, throwable),
                                execution -> this.publishTriggerExecution(workerTrigger, Optional.of(execution))
                            );
                            io.kestra.core.models.flows.State.Type state = callJob(workerCallable);

                            // here the realtime trigger fail before the publisher being call so we create a fail execution
                            if (workerCallable.getException() != null || !state.equals(SUCCESS)) {
                                this.handleRealtimeTriggerError(workerTrigger, workerCallable.getException());
                            }
                        }
                    } catch (Exception e) {
                        this.handleTriggerError(workerTrigger, e);
                    } finally {
                        Logs.logTrigger(
                            workerTrigger.getTriggerContext(),
                            runContext.logger(),
                            Level.INFO,
                            "Type {} completed in {}",
                            workerTrigger.getTrigger().getType(),
                            DurationFormatUtils.formatDurationHMS(stopWatch.getTime(TimeUnit.MILLISECONDS))
                        );

                        workerTrigger.getConditionContext().getRunContext().cleanup();
                    }

                    this.evaluateTriggerRunningCount.get(workerTrigger.getTriggerContext().uid()).addAndGet(-1);
                }
            );

        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ENDED_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ENDED_COUNT_DESCRIPTION, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();
    }

    private WorkerTaskResult run(WorkerTask workerTask, Boolean cleanUp) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_STARTED_COUNT, MetricRegistry.METRIC_WORKER_STARTED_COUNT_DESCRIPTION, metricRegistry.tags(workerTask, workerGroup))
            .increment();

        if (workerTask.getTaskRun().getState().getCurrent() == CREATED) {
            metricRegistry
                .timer(MetricRegistry.METRIC_WORKER_QUEUED_DURATION, MetricRegistry.METRIC_WORKER_QUEUED_DURATION_DESCRIPTION, metricRegistry.tags(workerTask, workerGroup))
                .record(Duration.between(
                    workerTask.getTaskRun().getState().getStartDate(), Instant.now()
                ));
        }

        if (! Boolean.TRUE.equals(workerTask.getTaskRun().getForceExecution()) && killedExecution.contains(workerTask.getTaskRun().getExecutionId())) {
            WorkerTaskResult workerTaskResult = new WorkerTaskResult(workerTask.getTaskRun().withState(KILLED));
            try {
                this.workerTaskResultQueue.emit(workerTaskResult);
            } catch (QueueException ex) {
                log.error("Unable to emit the worker task result for task {} taskrun {}", workerTask.getTask().getId(), workerTask.getTaskRun().getId(), ex);
            }

            this.logTerminated(workerTask);

            // We cannot remove the execution ID from the killedExecution in case the worker is processing multiple tasks of the execution
            // which can happens due to parallel processing.

            return workerTaskResult;
        }

        Logs.logTaskRun(
            workerTask.getTaskRun(),
            Level.INFO,
            "Type {} started",
            workerTask.getTask().getClass().getSimpleName()
        );

        workerTask = workerTask.withTaskRun(workerTask.getTaskRun().withState(RUNNING));

        DefaultRunContext runContext = runContextInitializer.forWorker((DefaultRunContext) workerTask.getRunContext(), workerTask);
        Optional<String> hash = Optional.empty();
        if (workerTask.getTask().getTaskCache() != null && workerTask.getTask().getTaskCache().getEnabled()) {
            runContext.logger().debug("Task output caching is enabled for task '{}''", workerTask.getTask().getId());
            hash = hashTask(runContext, workerTask.getTask());
            if (hash.isPresent()) {
                try {
                    Optional<InputStream> cacheFile = runContext.storage().getCacheFile(hash.get(), workerTask.getTaskRun().getValue(), workerTask.getTask().getTaskCache().getTtl());
                    if (cacheFile.isPresent()) {
                        runContext.logger().info("Skipping task execution for task '{}' as there is an existing cache entry for it", workerTask.getTask().getId());
                        try (ZipInputStream archive = new ZipInputStream(cacheFile.get())) {
                            if (archive.getNextEntry() != null) {
                                byte[] cache = archive.readAllBytes();
                                Map<String, Object> outputMap = JacksonMapper.ofIon().readValue(cache, JacksonMapper.MAP_TYPE_REFERENCE);
                                Variables variables = variablesService.of(StorageContext.forTask(workerTask.getTaskRun()), outputMap);

                                TaskRunAttempt attempt = TaskRunAttempt.builder()
                                    .state(new io.kestra.core.models.flows.State().withState(SUCCESS))
                                    .workerId(this.id)
                                    .build();
                                List<TaskRunAttempt> attempts = this.addAttempt(workerTask, attempt);
                                TaskRun taskRun = workerTask.getTaskRun().withAttempts(attempts).withOutputs(variables).withState(SUCCESS);
                                WorkerTaskResult workerTaskResult = new WorkerTaskResult(taskRun);
                                this.workerTaskResultQueue.emit(workerTaskResult);
                                return workerTaskResult;
                            }
                        }
                    }
                } catch (IOException | RuntimeException | QueueException e) {
                    // in case of any exception, log an error and continue
                    runContext.logger().error("Unexpected exception while loading the cache for task '{}', the task will be executed instead.", workerTask.getTask().getId(), e);
                }
            }
        }

        try {
            // run
            workerTask = this.runAttempt(runContext, workerTask);

            // get last state
            TaskRunAttempt lastAttempt = workerTask.getTaskRun().lastAttempt();
            if (lastAttempt == null) {
                throw new IllegalStateException("Can find lastAttempt on taskRun '" +
                    workerTask.getTaskRun().toString(true) + "'"
                );
            }
            io.kestra.core.models.flows.State.Type state = lastAttempt.getState().getCurrent();

            if (shutdown.get() && serverConfig.workerTaskRestartStrategy() != WorkerTaskRestartStrategy.NEVER && state.isFailed()) {
                // if the Worker is terminating and the task is not in success, it may have been terminated by the worker
                // in this case; we return immediately without emitting any result as it would be resubmitted (except if WorkerTaskRestartStrategy is NEVER)
                List<WorkerTaskResult> dynamicWorkerResults = workerTask.getRunContext().dynamicWorkerResults();
                List<TaskRun> dynamicTaskRuns = dynamicWorkerResults(dynamicWorkerResults);
                return new WorkerTaskResult(workerTask.getTaskRun(), dynamicTaskRuns);
            }

            if (workerTask.getTask().getRetry() != null &&
                workerTask.getTask().getRetry().getWarningOnRetry() &&
                workerTask.getTaskRun().attemptNumber() > 1 &&
                state == SUCCESS
            ) {
                state = WARNING;
            }

            if (workerTask.getTask().isAllowFailure() && !workerTask.getTaskRun().shouldBeRetried(workerTask.getTask().getRetry()) && state.isFailed()) {
                state = WARNING;
            }

            if (workerTask.getTask().isAllowWarning() && WARNING.equals(state)) {
                state = SUCCESS;
            }

            // emit
            List<WorkerTaskResult> dynamicWorkerResults = workerTask.getRunContext().dynamicWorkerResults();
            List<TaskRun> dynamicTaskRuns = dynamicWorkerResults(dynamicWorkerResults);

            workerTask = workerTask.withTaskRun(workerTask.getTaskRun().withState(state));

            WorkerTaskResult workerTaskResult = new WorkerTaskResult(workerTask.getTaskRun(), dynamicTaskRuns);

            this.workerTaskResultQueue.emit(workerTaskResult);

            // upload the cache file, hash may not be present if we didn't succeed in computing it
            if (workerTask.getTask().getTaskCache() != null && workerTask.getTask().getTaskCache().getEnabled() && hash.isPresent() &&
                (state == State.Type.SUCCESS || state == State.Type.WARNING)) {
                runContext.logger().info("Uploading a cache entry for task '{}'", workerTask.getTask().getId());

                try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                     ZipOutputStream archive = new ZipOutputStream(bos)) {
                    var zipEntry = new ZipEntry("outputs.ion");
                    archive.putNextEntry(zipEntry);
                    archive.write(JacksonMapper.ofIon().writeValueAsBytes(workerTask.getTaskRun().getOutputs()));
                    archive.closeEntry();
                    archive.finish();
                    Path archiveFile = runContext.workingDir().createTempFile( ".zip");
                    Files.write(archiveFile, bos.toByteArray());
                    URI uri = runContext.storage().putCacheFile(archiveFile.toFile(), hash.get(), workerTask.getTaskRun().getValue());
                    runContext.logger().debug("Caching entry uploaded in URI {}", uri);
                } catch (IOException | RuntimeException e) {
                    // in case of any exception, log an error and continue
                    runContext.logger().error("Unexpected exception while uploading the cache entry for task '{}', the task not be cached.", workerTask.getTask().getId(), e);
                }
            }
            return workerTaskResult;
        } catch (QueueException e) {
            // If there is a QueueException it can either be caused by the message limit or another queue issue.
            // We fail the task and try to resend it.
            TaskRun failed = workerTask.fail();
            if (e instanceof MessageTooBigException) {
                // If it's a message too big, we remove the outputs
                failed = failed.withOutputs(Variables.empty());
            }
            if (e instanceof UnsupportedMessageException) {
                // we expect the offending char is in the output so we remove it
                failed = failed.withOutputs(Variables.empty());
            }
            WorkerTaskResult workerTaskResult = new WorkerTaskResult(failed);
            RunContextLogger contextLogger = runContextLoggerFactory.create(workerTask);
            contextLogger.logger().error("Unable to emit the worker task result to the queue: {}", e.getMessage(), e);
            try {
                this.workerTaskResultQueue.emit(workerTaskResult);
            } catch (QueueException ex) {
                log.error("Unable to emit the worker task result for task {} taskrun {}", workerTask.getTask().getId(), workerTask.getTaskRun().getId(), e);
            }
            return workerTaskResult;
        } finally {
            this.logTerminated(workerTask);

            // remove tmp directory
            if (cleanUp) {
                workerTask.getRunContext().cleanup();
            }
        }
    }

    private Optional<String> hashTask(RunContext runContext, Task task) {
        try {
            var map = JacksonMapper.toMap(task);
            // If there are task provided variables, rendering the task may fail.
            // The best we can do is to add a fake 'workingDir' as it's an often added variables,
            // and it should not be part of the task hash.
            Map<String, Object> variables = Map.of("workingDir", "workingDir");
            var rMap = runContext.render(map, variables);
            var json = JacksonMapper.ofJson().writeValueAsBytes(rMap);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(json);
            byte[] bytes = digest.digest();
            return Optional.of(HexFormat.of().formatHex(bytes));
        } catch (RuntimeException | IllegalVariableEvaluationException | JsonProcessingException |
                 NoSuchAlgorithmException e) {
            runContext.logger().error("Unable to create the cache key for the task '{}'", task.getId(), e);
            return Optional.empty();
        }
    }

    private List<TaskRun> dynamicWorkerResults(List<WorkerTaskResult> dynamicWorkerResults) {
        return dynamicWorkerResults
            .stream()
            .map(WorkerTaskResult::getTaskRun)
            .map(taskRun -> taskRun.withDynamic(true))
            .toList();
    }

    private void logTerminated(WorkerTask workerTask) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_ENDED_COUNT, MetricRegistry.METRIC_WORKER_ENDED_COUNT_DESCRIPTION, metricRegistry.tags(workerTask, workerGroup))
            .increment();

        metricRegistry
            .timer(MetricRegistry.METRIC_WORKER_ENDED_DURATION, MetricRegistry.METRIC_WORKER_ENDED_DURATION_DESCRIPTION, metricRegistry.tags(workerTask, workerGroup))
            .record(workerTask.getTaskRun().getState().getDurationOrComputeIt());

        Logs.logTaskRun(
            workerTask.getTaskRun(),
            Level.INFO,
            "Type {} with state {} completed in {}",
            workerTask.getTask().getClass().getSimpleName(),
            workerTask.getTaskRun().getState().getCurrent(),
            workerTask.getTaskRun().getState().humanDuration()
        );
    }

    private void logError(WorkerTrigger workerTrigger, Throwable e) {
        Logger logger = workerTrigger.getConditionContext().getRunContext().logger();

        if (e instanceof InterruptedException || (e != null && e.getCause() instanceof InterruptedException)) {
            Logs.logTrigger(
                workerTrigger.getTriggerContext(),
                logger,
                Level.WARN,
                "[date: {}] Trigger evaluation interrupted in the worker",
                workerTrigger.getTriggerContext().getDate()
            );
        } else {
            Logs.logTrigger(
                workerTrigger.getTriggerContext(),
                logger,
                Level.WARN,
                "[date: {}] Trigger evaluation failed in the worker with error: {}",
                workerTrigger.getTriggerContext().getDate(),
                e != null ? e.getMessage() : "unknown",
                e
            );
        }

        if (logger.isTraceEnabled() && e != null) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }
    }

    private WorkerTask runAttempt(RunContext runContext, final WorkerTask workerTask) throws QueueException {
        Logger logger = runContext.logger();

        if (!(workerTask.getTask() instanceof RunnableTask<?> task)) {
            // This should never happen but better to deal with it than crashing the Worker
            var state = State.Type.fail(workerTask.getTask());
            TaskRunAttempt attempt = TaskRunAttempt.builder()
                .state(new io.kestra.core.models.flows.State().withState(state))
                .workerId(this.id)
                .build();
            List<TaskRunAttempt> attempts = this.addAttempt(workerTask, attempt);
            TaskRun taskRun = workerTask.getTaskRun().withAttempts(attempts);
            logger.error("Unable to execute the task '" + workerTask.getTask().getId() +
                "': only runnable tasks can be executed by the worker but the task is of type " + workerTask.getTask().getClass());
            return workerTask.withTaskRun(taskRun);
        }

        TaskRunAttempt.TaskRunAttemptBuilder builder = TaskRunAttempt.builder()
            .state(new io.kestra.core.models.flows.State().withState(RUNNING))
            .workerId(this.id);

        // emit the attempt so the execution knows that the task is in RUNNING
        this.workerTaskResultQueue.emit(new WorkerTaskResult(
                workerTask.getTaskRun()
                    .withAttempts(this.addAttempt(workerTask, builder.build()))
            )
        );

        AtomicInteger metricRunningCount = getMetricRunningCount(workerTask);
        metricRunningCount.incrementAndGet();

        // run it
        WorkerTaskCallable workerTaskCallable = new WorkerTaskCallable(workerTask, task, runContext, metricRegistry);
        io.kestra.core.models.flows.State.Type state = callJob(workerTaskCallable);

        metricRunningCount.decrementAndGet();

        // attempt
        TaskRunAttempt taskRunAttempt = builder
            .build()
            .withState(state)
            .withLogFile(runContext.logFileURI());

        // metrics
        runContext.metrics().forEach(metric -> {
            try {
                this.metricEntryQueue.emit(MetricEntry.of(workerTask.getTaskRun(), metric, workerTask.getExecutionKind()));
            } catch (QueueException e) {
                // fail silently
            }
        });

        // save outputs
        List<TaskRunAttempt> attempts = this.addAttempt(workerTask, taskRunAttempt);

        TaskRun taskRun = workerTask.getTaskRun()
            .withAttempts(attempts);

        try {
            Variables variables = variablesService.of(StorageContext.forTask(taskRun), workerTaskCallable.getTaskOutput());
            taskRun = taskRun.withOutputs(variables);
            if (workerTask.getTask().getAssets() != null) {
                List<Asset> outputAssets = runContext.assets().outputs();
                Optional<AssetsDeclaration> renderedAssetsDeclaration = runContext.render(workerTask.getTask().getAssets()).as(AssetsDeclaration.class);
                renderedAssetsDeclaration.map(AssetsDeclaration::getOutputs).ifPresent(outputAssets::addAll);
                taskRun = taskRun.withAssets(new AssetsInOut(
                    renderedAssetsDeclaration.map(AssetsDeclaration::getInputs).orElse(null),
                    outputAssets
                ));
            }
        } catch (Exception e) {
            logger.warn("Unable to save output on taskRun '{}'", taskRun, e);
        }

        return workerTask
            .withTaskRun(taskRun);
    }

    private io.kestra.core.models.flows.State.Type callJob(AbstractWorkerCallable workerJobCallable) {
        synchronized (this) {
            workerCallableReferences.add(workerJobCallable);
        }

        try {
            return tracer.inCurrentContext(
                workerJobCallable.runContext,
                workerJobCallable.getType(),
                Attributes.of(TraceUtils.ATTR_UID, workerJobCallable.getUid()),
                () -> workerSecurityService.callInSecurityContext(workerJobCallable)
            );
        } catch (Exception e) {
            // should only occur if it fails in the tracing code which should be unexpected
            // we add the exception to have some log in that case
            workerJobCallable.exception = e;
            return State.Type.FAILED;
        } finally {
            synchronized (this) {
                workerCallableReferences.remove(workerJobCallable);
            }
        }
    }

    private List<TaskRunAttempt> addAttempt(WorkerTask workerTask, TaskRunAttempt taskRunAttempt) {
        return ImmutableList.<TaskRunAttempt>builder()
            .addAll(workerTask.getTaskRun().getAttempts() == null ? new ArrayList<>() : workerTask.getTaskRun().getAttempts())
            .add(taskRunAttempt)
            .build();
    }

    public AtomicInteger getMetricRunningCount(WorkerTask workerTask) {
        String[] tags = this.metricRegistry.tags(workerTask, workerGroup);
        Arrays.sort(tags);

        long index = Hashing.hashToLong(String.join("-", tags));

        return this.metricRunningCount
            .computeIfAbsent(index, l -> metricRegistry.gauge(
                MetricRegistry.METRIC_WORKER_RUNNING_COUNT,
                MetricRegistry.METRIC_WORKER_RUNNING_COUNT_DESCRIPTION,
                new AtomicInteger(0),
                metricRegistry.tags(workerTask, workerGroup)
            ));
    }

    /**
     * {@inheritDoc}
     **/
    @PreDestroy
    @Override
    public void close() {
        if (shutdown.compareAndSet(false, true)) {
            closeWorker(serverConfig.terminationGracePeriod());
        }
    }

    @VisibleForTesting
    public void closeWorker(final Duration timeout) {
        if (log.isDebugEnabled()) {
            log.debug("Terminating");
        }

        setState(ServiceState.TERMINATING);

        try {
            // close the WorkerJob queue to stop receiving new JobTask execution.
            workerJobQueue.close();
        } catch (IOException e) {
            log.error("Failed to close the WorkerJobQueue");
        }

        final boolean terminatedGracefully;
        if (!skipGracefulTermination.get()) {
            terminatedGracefully = waitForTasksCompletion(timeout);
        } else {
            log.info("Terminating now and skip waiting for tasks completions.");
            this.receiveCancellations.forEach(Runnable::run);
            this.executorService.shutdownNow();
            closeQueue();
            terminatedGracefully = false;
        }

        ServiceState state = terminatedGracefully ? TERMINATED_GRACEFULLY : TERMINATED_FORCED;
        setState(state);

        if (log.isDebugEnabled()) {
            log.debug("Closed ({}).", state.name());
        }
    }

    private boolean waitForTasksCompletion(final Duration timeout) {
        final Instant deadline = Instant.now().plus(timeout);

        final List<AbstractWorkerCallable> callables;
        synchronized (this) {
            // copy to avoid concurrent modification exception on iteration.
            callables = new ArrayList<>(this.workerCallableReferences);
        }

        // signals all worker tasks and triggers of the shutdown.
        callables.forEach(AbstractWorkerCallable::signalStop);

        AtomicReference<ServiceState> shutdownState = new AtomicReference<>();
        // start shutdown
        Thread.ofVirtual().name("worker-shutdown").start(
            () -> {
                try {
                    this.receiveCancellations.forEach(Runnable::run);
                    this.executorService.shutdown();

                    long remaining = Math.max(0, Instant.now().until(deadline, ChronoUnit.MILLIS));

                    // wait for all realtime triggers to cleanly stop.
                    awaitForRealtimeTriggers(callables, Duration.ofMillis(remaining));

                    boolean gracefullyShutdown = this.executorService.awaitTermination(remaining, TimeUnit.MILLISECONDS);
                    if (!gracefullyShutdown) {
                        log.warn("Worker still has some pending threads after `terminationGracePeriod`. Forcing shutdown now.");
                        this.executorService.shutdownNow();
                    }

                    shutdownState.set(gracefullyShutdown ? TERMINATED_GRACEFULLY : TERMINATED_FORCED);
                } catch (InterruptedException e) {
                    log.error("Failed to shutdown the worker. Thread was interrupted");
                    shutdownState.set(TERMINATED_FORCED);
                }
            }
        );

        // wait for task completion
        Await.until(
            () -> {
                ServiceState serviceState = shutdownState.get();
                if (serviceState == TERMINATED_FORCED || serviceState == TERMINATED_GRACEFULLY) {
                    log.info("All working threads are terminated.");

                    // we ensure that last produce message are send
                    closeQueue();
                    return true;
                }

                if (this.workerCallableReferences.isEmpty()) {
                    log.debug("All worker threads is terminated.");
                } else {
                    log.warn(
                        "Waiting for all worker threads to terminate (remaining: {}).",
                        this.workerCallableReferences.size()
                    );
                }

                return false;
            },
            Duration.ofSeconds(1)
        );

        return shutdownState.get() == TERMINATED_GRACEFULLY;
    }

    private void awaitForRealtimeTriggers(final List<AbstractWorkerCallable> callables,
                                          final Duration timeout) {
        final Instant deadline = Instant.now().plus(timeout);
        for (AbstractWorkerCallable callable : callables) {
            if (callable instanceof WorkerTriggerRealtimeCallable t) {
                long remaining = Math.max(0, Instant.now().until(deadline, ChronoUnit.MILLIS));

                if (!t.awaitStop(Duration.ofMillis(remaining))) {
                    final String type = t.getWorkerTrigger().getTrigger().getType();
                    log.debug("Failed to stop trigger '{}' before timeout elapsed.", type);
                    // As a last resort, we try to stop the trigger via Thread.interrupt.
                    // If the trigger doesn't respond to interrupts, it may never terminate.
                    t.interrupt();
                    Logs.logTrigger(
                        t.getWorkerTrigger().getTriggerContext(),
                        t.getWorkerTrigger().getConditionContext().getRunContext().logger(),
                        Level.INFO,
                        "Type {} interrupted",
                        type
                    );
                }
            }
        }
    }

    private void closeQueue() {
        try {
            this.workerTaskResultQueue.close();
            this.workerTriggerResultQueue.close();
        } catch (IOException e) {
            log.error("Failed to close the queue", e);
        }
    }

    /**
     * This method should only be used on tests.
     * It shut down the worker without waiting for tasks to end,
     * and without closing the queue, so tests can launch and shutdown a worker manually without closing the queue.
     */
    @VisibleForTesting
    public void shutdown() {
        // initiate shutdown
        shutdown.compareAndSet(false, true);

        // close all queues and shutdown now
        this.receiveCancellations.forEach(Runnable::run);
        this.executorService.shutdownNow();
    }

    public List<WorkerJob> getWorkerThreadTasks() {
        return this.workerCallableReferences
            .stream()
            .map(throwFunction(workerCallable -> {
                if (workerCallable instanceof WorkerTaskCallable workerTaskCallable) {
                    return workerTaskCallable.workerTask;
                } else if (workerCallable instanceof AbstractWorkerTriggerCallable workerTriggerCallable) {
                    return workerTriggerCallable.workerTrigger;
                } else {
                    throw new IllegalArgumentException("Invalid Callable type: '" + workerCallable.getClass().getName() + "'");
                }
            }))
            .toList();
    }

    /**
     * Specify whether to skip graceful termination on shutdown.
     *
     * @param skipGracefulTermination {@code true} to skip graceful termination on shutdown.
     */
    @Override
    public void skipGracefulTermination(final boolean skipGracefulTermination) {
        this.skipGracefulTermination.set(skipGracefulTermination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceType getType() {
        return ServiceType.WORKER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceState getState() {
        return state.get();
    }
}
