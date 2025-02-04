package io.kestra.core.runners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.*;
import io.kestra.core.queues.*;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.*;
import io.kestra.core.services.LabelService;
import io.kestra.core.services.LogService;
import io.kestra.core.services.WorkerGroupService;
import io.kestra.core.trace.Tracer;
import io.kestra.core.trace.TracerFactory;
import io.kestra.core.trace.TraceUtils;
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

import java.io.IOException;
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

import static io.kestra.core.models.flows.State.Type.*;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_FORCED;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_GRACEFULLY;
import static io.kestra.core.utils.Rethrow.throwFunction;

@SuppressWarnings("this-escape")
@Slf4j
@Introspected
public class Worker implements Service, Runnable, AutoCloseable {
    public static final String EXECUTOR_NAME = "worker";

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final String SERVICE_PROPS_WORKER_GROUP = "worker.group";

    @Inject
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
    @Named(QueueFactoryInterface.CLUSTER_EVENT_NAMED)
    private Optional<QueueInterface<ClusterEvent>> clusterEventQueue;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private ServerConfig serverConfig;

    @Inject
    private LogService logService;

    @Inject
    private RunContextInitializer runContextInitializer;

    @Inject
    private RunContextLoggerFactory runContextLoggerFactory;

    @Inject
    private WorkerSecurityService workerSecurityService;

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

    private final String id;

    private final ExecutorService executorService;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final AtomicReference<ServiceState> state = new AtomicReference<>();

    private final List<Runnable> receiveCancellations = new ArrayList<>();

    @Getter
    private final Integer numThreads;
    private final AtomicInteger pendingJobCount = new AtomicInteger(0);
    private final AtomicInteger runningJobCount = new AtomicInteger(0);

    @Inject
    private TracerFactory tracerFactory;
    private Tracer tracer;

    /**
     * Creates a new {@link Worker} instance.
     *
     * @param workerId       The worker service ID.
     * @param numThreads     The worker num threads.
     * @param workerGroupKey The worker group (EE).
     */
    @Inject
    public Worker(
        @Parameter String workerId,
        @Parameter Integer numThreads,
        @Nullable @Parameter String workerGroupKey,
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        WorkerGroupService workerGroupService,
        ExecutorsUtils executorsUtils
    ) {
        this.id = workerId;
        this.numThreads = numThreads;
        this.workerGroup = workerGroupService.resolveGroupFromKey(workerGroupKey);
        this.eventPublisher = eventPublisher;
        this.executorService = executorsUtils.maxCachedThreadPool(numThreads, EXECUTOR_NAME);
        this.setState(ServiceState.CREATED);
    }

    @PostConstruct
    void initMetricsAndTracer() {
        String[] tags = this.workerGroup == null ? new String[0] : new String[]{MetricRegistry.TAG_WORKER_GROUP, this.workerGroup};
        // create metrics to store thread count, pending jobs and running jobs, so we can have autoscaling easily
        this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT, numThreads, tags);
        this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_JOB_PENDING_COUNT, pendingJobCount, tags);
        this.metricRegistry.gauge(MetricRegistry.METRIC_WORKER_JOB_RUNNING_COUNT, runningJobCount, tags);

        this.tracer = tracerFactory.getTracer(Worker.class, "WORKER");
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

        this.receiveCancellations.addFirst(this.workerJobQueue.receive(
            this.workerGroup,
            Worker.class,
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

        setState(ServiceState.RUNNING);
    }

    private void clusterEventQueue(Either<ClusterEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a cluster event: {}", either.getRight().getMessage());
            return;
        }

        ClusterEvent clusterEvent = either.getLeft();
        log.info("Cluster event received: {}", clusterEvent);
        switch (clusterEvent.eventType()) {
            case MAINTENANCE_ENTER -> {
                this.executionKilledQueue.pause();
                this.workerJobQueue.pause();

                this.setState(ServiceState.MAINTENANCE);
            }
            case MAINTENANCE_EXIT -> {
                this.executionKilledQueue.resume();
                this.workerJobQueue.resume();

                this.setState(ServiceState.RUNNING);
            }
        }
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
                    var workerTriggerResult = WorkerTriggerResult.builder().triggerContext(triggerContext).success(false).execution(Optional.empty()).build();
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
                        runContextInitializer.forPlugin(runContext, currentTask)
                    );

                    // all tasks will be handled immediately by the worker
                    WorkerTaskResult workerTaskResult = null;
                    try {
                        if (!TruthUtils.isTruthy(runContext.render(currentWorkerTask.getTask().getRunIf()))) {
                            workerTaskResult = new WorkerTaskResult(currentWorkerTask.getTaskRun().withState(SKIPPED));
                            this.workerTaskResultQueue.emit(workerTaskResult);
                        } else {
                            workerTaskResult = this.run(currentWorkerTask, false);
                        }
                    } catch (IllegalVariableEvaluationException e) {
                        RunContextLogger contextLogger = runContextLoggerFactory.create(currentWorkerTask.getTaskRun(), currentWorkerTask.getTask());
                        contextLogger.logger().error("Failed evaluating runIf: {}", e.getMessage(), e);
                    } catch (QueueException e) {
                        log.error("Unable to emit the worker task result for task {} taskrun {}", currentWorkerTask.getTask().getId(), currentWorkerTask.getTaskRun().getId(), e);
                    }

                    if (workerTaskResult.getTaskRun().getState().isFailed() && !currentWorkerTask.getTask().isAllowFailure()) {
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
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_EXECUTION_COUNT, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        if (log.isDebugEnabled()) {
            logService.logTrigger(
                workerTrigger.getTriggerContext(),
                log,
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
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        logError(workerTrigger, e);
        try {
            this.workerTriggerResultQueue.emit(
                WorkerTriggerResult.builder()
                    .success(false)
                    .triggerContext(workerTrigger.getTriggerContext())
                    .trigger(workerTrigger.getTrigger())
                    .build()
            );
        } catch (QueueException ex) {
            log.error("Unable to send the worker trigger result {}.{}.{}",
                workerTrigger.getTriggerContext().getNamespace(), workerTrigger.getTriggerContext().getFlowId(), workerTrigger.getTriggerContext().getTriggerId(), ex);
        }
    }

    private void handleRealtimeTriggerError(WorkerTrigger workerTrigger, Throwable e) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        // We create a FAILED execution, so the user is aware that the realtime trigger failed to be created
        var execution = TriggerService
            .generateRealtimeExecution(workerTrigger.getTrigger(), workerTrigger.getConditionContext(), workerTrigger.getTriggerContext(), null)
            .withState(FAILED);

        // We create an ERROR log attached to the execution
        Logger logger = workerTrigger.getConditionContext().getRunContext().logger();
        logService.logExecution(
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
                    .success(false)
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
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_STARTED_COUNT, metricRegistry.tags(workerTrigger, workerGroup))
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
            .timer(MetricRegistry.METRIC_WORKER_TRIGGER_DURATION, metricRegistry.tags(workerTrigger, workerGroup))
            .record(() -> {
                    StopWatch stopWatch = new StopWatch();
                    stopWatch.start();

                    this.evaluateTriggerRunningCount.computeIfAbsent(workerTrigger.getTriggerContext().uid(), s -> metricRegistry
                        .gauge(MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT, new AtomicInteger(0), metricRegistry.tags(workerTrigger, workerGroup)));
                    this.evaluateTriggerRunningCount.get(workerTrigger.getTriggerContext().uid()).addAndGet(1);

                    DefaultRunContext runContext = (DefaultRunContext) workerTrigger.getConditionContext().getRunContext();
                    runContextInitializer.forWorker(runContext, workerTrigger);
                    try {

                        logService.logTrigger(
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
                        logService.logTrigger(
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
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ENDED_COUNT, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();
    }

    private WorkerTaskResult run(WorkerTask workerTask, Boolean cleanUp) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_STARTED_COUNT, metricRegistry.tags(workerTask, workerGroup))
            .increment();

        if (workerTask.getTaskRun().getState().getCurrent() == CREATED) {
            metricRegistry
                .timer(MetricRegistry.METRIC_WORKER_QUEUED_DURATION, metricRegistry.tags(workerTask, workerGroup))
                .record(Duration.between(
                    workerTask.getTaskRun().getState().getStartDate(), Instant.now()
                ));
        }

        if (killedExecution.contains(workerTask.getTaskRun().getExecutionId())) {
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

        logService.logTaskRun(
            workerTask.getTaskRun(),
            workerTask.logger(),
            Level.INFO,
            "Type {} started",
            workerTask.getTask().getClass().getSimpleName()
        );

        workerTask = workerTask.withTaskRun(workerTask.getTaskRun().withState(RUNNING));
        try {
            this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask.getTaskRun()));
        } catch (QueueException e) {
            log.error("Unable to emit the worker task result for task {} taskrun {}", workerTask.getTask().getId(), workerTask.getTaskRun().getId(), e);
        }

        try {
            // run
            workerTask = this.runAttempt(workerTask);

            // get last state
            TaskRunAttempt lastAttempt = workerTask.getTaskRun().lastAttempt();
            if (lastAttempt == null) {
                throw new IllegalStateException("Can find lastAttempt on taskRun '" +
                    workerTask.getTaskRun().toString(true) + "'"
                );
            }
            io.kestra.core.models.flows.State.Type state = lastAttempt.getState().getCurrent();

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
            return workerTaskResult;
        } catch (QueueException e) {
            // If there is a QueueException it can either be caused by the message limit or another queue issue.
            // We fail the task and try to resend it.
            TaskRun failed = workerTask.fail();
            if (e instanceof MessageTooBigException) {
                // If it's a message too big, we remove the outputs
                failed = failed.withOutputs(Collections.emptyMap());
            }
            WorkerTaskResult workerTaskResult = new WorkerTaskResult(failed);
            RunContextLogger contextLogger = runContextLoggerFactory.create(workerTask.getTaskRun(), workerTask.getTask());
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

    private List<TaskRun> dynamicWorkerResults(List<WorkerTaskResult> dynamicWorkerResults) {
        return dynamicWorkerResults
            .stream()
            .map(WorkerTaskResult::getTaskRun)
            .map(taskRun -> taskRun.withDynamic(true))
            .toList();
    }

    private void logTerminated(WorkerTask workerTask) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_ENDED_COUNT, metricRegistry.tags(workerTask, workerGroup))
            .increment();

        metricRegistry
            .timer(MetricRegistry.METRIC_WORKER_ENDED_DURATION, metricRegistry.tags(workerTask, workerGroup))
            .record(workerTask.getTaskRun().getState().getDuration());

        logService.logTaskRun(
            workerTask.getTaskRun(),
            workerTask.logger(),
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
            logService.logTrigger(
                workerTrigger.getTriggerContext(),
                logger,
                Level.WARN,
                "[date: {}] Trigger evaluation interrupted in the worker",
                workerTrigger.getTriggerContext().getDate()
            );
        } else {
            logService.logTrigger(
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

    private WorkerTask runAttempt(final WorkerTask workerTask) throws QueueException {
        DefaultRunContext runContext = runContextInitializer.forWorker((DefaultRunContext) workerTask.getRunContext(), workerTask);

        Logger logger = runContext.logger();

        if (!(workerTask.getTask() instanceof RunnableTask<?> task)) {
            // This should never happen but better to deal with it than crashing the Worker
            var state = workerTask.getTask().isAllowFailure() ? workerTask.getTask().isAllowWarning() ? SUCCESS : WARNING : FAILED;
            TaskRunAttempt attempt = TaskRunAttempt.builder().state(new io.kestra.core.models.flows.State().withState(state)).build();
            List<TaskRunAttempt> attempts = this.addAttempt(workerTask, attempt);
            TaskRun taskRun = workerTask.getTaskRun().withAttempts(attempts);
            logger.error("Unable to execute the task '" + workerTask.getTask().getId() +
                "': only runnable tasks can be executed by the worker but the task is of type " + workerTask.getTask().getClass());
            return workerTask.withTaskRun(taskRun);
        }

        TaskRunAttempt.TaskRunAttemptBuilder builder = TaskRunAttempt.builder()
            .state(new io.kestra.core.models.flows.State().withState(RUNNING));

        AtomicInteger metricRunningCount = getMetricRunningCount(workerTask);

        metricRunningCount.incrementAndGet();

        WorkerTaskCallable workerTaskCallable = new WorkerTaskCallable(workerTask, task, runContext, metricRegistry);

        // emit attempts
        this.workerTaskResultQueue.emit(new WorkerTaskResult(
                workerTask.getTaskRun()
                    .withAttempts(this.addAttempt(workerTask, builder.build()))
            )
        );

        // run it
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
                this.metricEntryQueue.emit(MetricEntry.of(workerTask.getTaskRun(), metric));
            } catch (QueueException e) {
                // fail silently
            }
        });

        // save outputs
        List<TaskRunAttempt> attempts = this.addAttempt(workerTask, taskRunAttempt);

        TaskRun taskRun = workerTask.getTaskRun()
            .withAttempts(attempts);

        try {
            taskRun = taskRun.withOutputs(workerTaskCallable.getTaskOutput() != null ? workerTaskCallable.getTaskOutput().toMap() : ImmutableMap.of());
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
        } catch(Exception e) {
            // should only occur if it fails in the tracing code which should be unexpected
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
                    logService.logTrigger(
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

    @VisibleForTesting
    public void shutdown() {
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
