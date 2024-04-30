package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerJobQueueInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.LogService;
import io.kestra.core.services.WorkerGroupService;
import io.kestra.core.tasks.flows.WorkingDirectory;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.Hashing;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.models.flows.State.Type.*;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_FORCED;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_GRACEFULLY;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
@Introspected
public class Worker implements Service, Runnable, AutoCloseable {
    private final static ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final String SERVICE_PROPS_WORKER_GROUP = "worker.group";

    @Inject
    private ApplicationContext applicationContext;

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
    private MetricRegistry metricRegistry;

    @Inject
    private ServerConfig serverConfig;

    @Inject
    private LogService logService;

    private final Set<String> killedExecution = ConcurrentHashMap.newKeySet();

    @Getter
    private final Map<Long, AtomicInteger> metricRunningCount = new ConcurrentHashMap<>();

    @VisibleForTesting
    @Getter
    private final Map<String, AtomicInteger> evaluateTriggerRunningCount = new ConcurrentHashMap<>();

    private final List<AbstractWorkerThread> workerThreadReferences = new ArrayList<>();

    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AtomicBoolean skipGracefulTermination = new AtomicBoolean(false);

    @Getter
    private final String workerGroup;

    private final String id;

    // package private to allow its usage within tests
    final ExecutorService executorService;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final AtomicReference<ServiceState> state = new AtomicReference<>();

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
        this.workerGroup = workerGroupService.resolveGroupFromKey(workerGroupKey);
        this.eventPublisher = eventPublisher;
        this.executorService = executorsUtils.maxCachedThreadPool(numThreads, "worker");
        setState(ServiceState.CREATED);
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    @Deprecated(forRemoval = true)
    public Worker(
        ApplicationContext context,
        Integer numThreads,
        String workerGroupKey
    ) {
        this(
            UUID.randomUUID().toString(),
            numThreads,
            workerGroupKey,
            context.getBean(ApplicationEventPublisher.class),
            context.getBean(WorkerGroupService.class),
            context.getBean(ExecutorsUtils.class)
        );
        context.inject(this);
    }

    @Override
    public void run() {
        setState(ServiceState.RUNNING);
        this.executionKilledQueue.receive(executionKilled -> {
            if (executionKilled == null || !executionKilled.isLeft()) {
                return;
            }

            ExecutionKilled.State state = executionKilled.getLeft().getState();

            if (state != null && state != ExecutionKilled.State.EXECUTED) {
                return;
            }

            synchronized (this) {
                if (executionKilled.getLeft() instanceof ExecutionKilledExecution executionKilledExecution) {
                    // @FIXME: the hashset will never expire killed execution
                    killedExecution.add(executionKilledExecution.getExecutionId());

                    workerThreadReferences
                        .stream()
                        .filter(workerThread -> workerThread instanceof WorkerTaskThread)
                        .map(workerThread -> (WorkerTaskThread) workerThread)
                        .filter(workerThread -> executionKilledExecution.isEqual(workerThread.getWorkerTask()))
                        .forEach(AbstractWorkerThread::kill);
                } else if (executionKilled.getLeft() instanceof ExecutionKilledTrigger executionKilledTrigger) {
                    workerThreadReferences
                        .stream()
                        .filter(workerThread -> workerThread instanceof AbstractWorkerTriggerThread)
                        .map(workerThread -> (AbstractWorkerTriggerThread) workerThread)
                        .filter(workerThread -> executionKilledTrigger.isEqual(workerThread.getWorkerTrigger().getTriggerContext()))
                        .forEach(AbstractWorkerThread::kill);
                }
            }
        });

        this.workerJobQueue.receive(
            this.workerGroup,
            Worker.class,
            either -> {
                executorService.execute(() -> {
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
                });
            }
        );
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
            } catch (IOException e) {
                // ignore the message if we cannot do anything about it
                log.error("Unexpected exception when trying to handle a deserialization error", e);
            }
        }
    }

    private void handleTask(WorkerTask workerTask) {
        if (workerTask.getTask() instanceof RunnableTask) {
            this.run(workerTask, true);
        } else if (workerTask.getTask() instanceof WorkingDirectory workingDirectory) {
            RunContext runContext = workerTask.getRunContext().forWorkingDirectory(applicationContext, workerTask);

            try {
                // preExecuteTasks
                try {
                    workingDirectory.preExecuteTasks(runContext, workerTask.getTaskRun());
                } catch (Exception e) {
                    runContext.logger().error("Failed preExecuteTasks on WorkingDirectory: {}", e.getMessage(), e);
                    workerTask = workerTask.fail();
                    this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask));
                    this.logTerminated(workerTask);

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
                        runContext
                    );

                    // all tasks will be handled immediately by the worker
                    WorkerTaskResult workerTaskResult = this.run(currentWorkerTask, false);

                    if (workerTaskResult.getTaskRun().getState().isFailed() && !currentWorkerTask.getTask().isAllowFailure()) {
                        break;
                    }

                    runContext = runContext.updateVariables(workerTaskResult, workerTask.getTaskRun());
                }

                workingDirectory.postExecuteTasks(runContext, workerTask.getTaskRun());
            } finally {
                runContext.cleanup();
            }
        } else {
            throw new RuntimeException("Unable to process the task '" + workerTask.getTask().getId() + "' as it's not a runnable task");
        }
    }


    private RunContext initRunContextForTrigger(WorkerTrigger workerTrigger) {
        return workerTrigger.getConditionContext()
            .getRunContext()
            .forWorker(this.applicationContext, workerTrigger);
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

        var flowLabels = workerTrigger.getConditionContext().getFlow().getLabels();
        if (flowLabels != null) {
            evaluate = evaluate.map(execution -> {
                    List<Label> executionLabels = execution.getLabels() != null ? execution.getLabels() : new ArrayList<>();
                    executionLabels.addAll(flowLabels);
                    return execution.withLabels(executionLabels);
                }
            );
        }

        this.workerTriggerResultQueue.emit(
            WorkerTriggerResult.builder()
                .execution(evaluate)
                .triggerContext(workerTrigger.getTriggerContext())
                .trigger(workerTrigger.getTrigger())
                .build()
        );
    }

    private void handleTriggerError(WorkerTrigger workerTrigger, Throwable e) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        logError(workerTrigger, e);
        this.workerTriggerResultQueue.emit(
            WorkerTriggerResult.builder()
                .success(false)
                .triggerContext(workerTrigger.getTriggerContext())
                .trigger(workerTrigger.getTrigger())
                .build()
        );
    }

    private void handleTrigger(WorkerTrigger workerTrigger) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_STARTED_COUNT, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();

        this.metricRegistry
            .timer(MetricRegistry.METRIC_WORKER_TRIGGER_DURATION, metricRegistry.tags(workerTrigger.getTriggerContext(), workerGroup))
            .record(() -> {
                    StopWatch stopWatch = new StopWatch();
                    stopWatch.start();

                    this.evaluateTriggerRunningCount.computeIfAbsent(workerTrigger.getTriggerContext().uid(), s -> metricRegistry
                        .gauge(MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT, new AtomicInteger(0), metricRegistry.tags(workerTrigger.getTriggerContext(), workerGroup)));
                    this.evaluateTriggerRunningCount.get(workerTrigger.getTriggerContext().uid()).addAndGet(1);

                    try {
                        RunContext runContext = this.initRunContextForTrigger(workerTrigger);

                        logService.logTrigger(
                            workerTrigger.getTriggerContext(),
                            workerTrigger.getConditionContext().getRunContext().logger(),
                            Level.INFO,
                            "Type {} started",
                            workerTrigger.getTrigger().getType()
                        );

                        if (workerTrigger.getTrigger() instanceof PollingTriggerInterface pollingTrigger) {
                            WorkerTriggerThread workerThread = new WorkerTriggerThread(workerTrigger, pollingTrigger);
                            io.kestra.core.models.flows.State.Type state = runThread(workerThread, runContext.logger());

                            if (workerThread.getException() != null || !state.equals(SUCCESS)) {
                                this.handleTriggerError(workerTrigger, workerThread.getException());
                            }

                            if (!state.equals(FAILED)) {
                                this.publishTriggerExecution(workerTrigger, workerThread.getEvaluate());
                            }
                        } else if (workerTrigger.getTrigger() instanceof RealtimeTriggerInterface streamingTrigger) {
                            WorkerTriggerRealtimeThread workerThread = new WorkerTriggerRealtimeThread(
                                workerTrigger,
                                streamingTrigger,
                                throwable -> this.handleTriggerError(workerTrigger, throwable),
                                execution -> this.publishTriggerExecution(workerTrigger, Optional.of(execution))
                            );
                            io.kestra.core.models.flows.State.Type state = runThread(workerThread, runContext.logger());

                            if (workerThread.getException() != null || !state.equals(SUCCESS)) {
                                this.handleTriggerError(workerTrigger, workerThread.getException());
                            }
                        }
                    } catch (Exception e) {
                        this.handleTriggerError(workerTrigger, e);
                    } finally {
                        workerTrigger.getConditionContext().getRunContext().cleanup();

                        logService.logTrigger(
                            workerTrigger.getTriggerContext(),
                            workerTrigger.getConditionContext().getRunContext().logger(),
                            Level.INFO,
                            "Type {} completed in {}",
                            workerTrigger.getTrigger().getType(),
                            DurationFormatUtils.formatDurationHMS(stopWatch.getTime(TimeUnit.MILLISECONDS))
                        );
                    }

                    this.evaluateTriggerRunningCount.get(workerTrigger.getTriggerContext().uid()).addAndGet(-1);
                }
            );

        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ENDED_COUNT, metricRegistry.tags(workerTrigger, workerGroup))
            .increment();
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private WorkerTask cleanUpTransient(WorkerTask workerTask) {
        try {
            return MAPPER.readValue(MAPPER.writeValueAsString(workerTask), WorkerTask.class);
        } catch (JsonProcessingException e) {
            log.warn("Unable to cleanup transient", e);

            return workerTask;
        }
    }

    private WorkerTaskResult run(WorkerTask workerTask, Boolean cleanUp) throws QueueException {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_STARTED_COUNT, metricRegistry.tags(workerTask, workerGroup))
            .increment();

        if (workerTask.getTaskRun().getState().getCurrent() == CREATED) {
            metricRegistry
                .timer(MetricRegistry.METRIC_WORKER_QUEUED_DURATION, metricRegistry.tags(workerTask, workerGroup))
                .record(Duration.between(
                    workerTask.getTaskRun().getState().getStartDate(), now()
                ));
        }

        if (killedExecution.contains(workerTask.getTaskRun().getExecutionId())) {
            workerTask = workerTask.withTaskRun(workerTask.getTaskRun().withState(KILLED));

            WorkerTaskResult workerTaskResult = new WorkerTaskResult(workerTask);
            this.workerTaskResultQueue.emit(workerTaskResult);

            this.logTerminated(workerTask);

            //FIXME should we remove it from the killedExecution set ?

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
        this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask));

        AtomicReference<WorkerTask> current = new AtomicReference<>(workerTask);

        // run
        WorkerTask finalWorkerTask = this.runAttempt(current.get());

        // save dynamic WorkerResults since cleanUpTransient will remove them
        List<WorkerTaskResult> dynamicWorkerResults = finalWorkerTask.getRunContext().dynamicWorkerResults();

        // remove tmp directory
        if (cleanUp) {
            finalWorkerTask.getRunContext().cleanup();
        }

        finalWorkerTask = this.cleanUpTransient(finalWorkerTask);

        // get last state
        TaskRunAttempt lastAttempt = finalWorkerTask.getTaskRun().lastAttempt();
        if (lastAttempt == null) {
            throw new IllegalStateException("Can find lastAttempt on taskRun '" +
                finalWorkerTask.getTaskRun().toString(true) + "'"
            );
        }
        io.kestra.core.models.flows.State.Type state = lastAttempt.getState().getCurrent();

        if (workerTask.getTask().getRetry() != null &&
            workerTask.getTask().getRetry().getWarningOnRetry() &&
            finalWorkerTask.getTaskRun().attemptNumber() > 1 &&
            state == SUCCESS
        ) {
            state = WARNING;
        }

        if (workerTask.getTask().isAllowFailure() && !finalWorkerTask.getTaskRun().shouldBeRetried(workerTask.getTask().getRetry()) && state.isFailed()) {
            state = WARNING;
        }

        // emit
        finalWorkerTask = finalWorkerTask.withTaskRun(finalWorkerTask.getTaskRun().withState(state));

        // if resulting object can't be emitted (mostly size of message), we just can't emit it like that.
        // So we just tried to fail the status of the worker task, in this case, no log can't be added, just
        // changing status must work in order to finish current task (except if we are near the upper bound size).
        try {
            WorkerTaskResult workerTaskResult = new WorkerTaskResult(finalWorkerTask, dynamicWorkerResults);
            this.workerTaskResultQueue.emit(workerTaskResult);
            return workerTaskResult;
        } catch (QueueException e) {
            finalWorkerTask = workerTask.fail();
            WorkerTaskResult workerTaskResult = new WorkerTaskResult(finalWorkerTask, dynamicWorkerResults);
            RunContext runContext = workerTask
                .getRunContext()
                .forWorker(this.applicationContext, workerTask);

            runContext.logger().error("Exception while trying to emit the worker task result to the queue", e);
            this.workerTaskResultQueue.emit(workerTaskResult);
            return workerTaskResult;
        } finally {
            this.logTerminated(finalWorkerTask);
        }
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

        logService.logTrigger(
            workerTrigger.getTriggerContext(),
            logger,
            Level.WARN,
            "[date: {}] Worker Evaluate Failed with error '{}'",
            workerTrigger.getTriggerContext().getDate(),
            e != null ? e.getMessage() : "null",
            e
        );

        if (logger.isTraceEnabled() && e != null) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }
    }

    private WorkerTask runAttempt(WorkerTask workerTask) {
        RunContext runContext = workerTask
            .getRunContext()
            .forWorker(this.applicationContext, workerTask);

        Logger logger = runContext.logger();

        if (!(workerTask.getTask() instanceof RunnableTask<?> task)) {
            // This should never happen but better to deal with it than crashing the Worker
            var state = workerTask.getTask().isAllowFailure() ? WARNING : FAILED;
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

        WorkerTaskThread workerThread = new WorkerTaskThread(workerTask, task, runContext, metricRegistry);

        // emit attempts
        this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask
            .withTaskRun(
                workerTask.getTaskRun()
                    .withAttempts(this.addAttempt(workerTask, builder.build()))
            )
        ));

        // run it
        io.kestra.core.models.flows.State.Type state = runThread(workerThread, logger);

        metricRunningCount.decrementAndGet();

        // attempt
        TaskRunAttempt taskRunAttempt = builder
            .build()
            .withState(state);

        // metrics
        runContext.metrics().forEach(metric -> this.metricEntryQueue.emit(MetricEntry.of(workerTask.getTaskRun(), metric)));

        // save outputs
        List<TaskRunAttempt> attempts = this.addAttempt(workerTask, taskRunAttempt);

        TaskRun taskRun = workerTask.getTaskRun()
            .withAttempts(attempts);

        try {
            taskRun = taskRun.withOutputs(workerThread.getTaskOutput() != null ? workerThread.getTaskOutput().toMap() : ImmutableMap.of());
        } catch (Exception e) {
            logger.warn("Unable to save output on taskRun '{}'", taskRun, e);
        }

        return workerTask
            .withTaskRun(taskRun);
    }

    private io.kestra.core.models.flows.State.Type runThread(AbstractWorkerThread workerThread, Logger logger) {
        // run it
        io.kestra.core.models.flows.State.Type state;
        try {
            synchronized (this) {
                workerThreadReferences.add(workerThread);
            }
            workerThread.start();
            workerThread.join();
            state = workerThread.getTaskState();
        } catch (InterruptedException e) {
            logger.error("Failed to join WorkerThread {}", e.getMessage(), e);
            if (workerThread instanceof WorkerTaskThread workerTaskThread) {
                state = workerTaskThread.getWorkerTask().getTask().isAllowFailure() ? WARNING : FAILED;
            } else {
                state = FAILED;
            }
        } finally {
            synchronized (this) {
                workerThreadReferences.remove(workerThread);
            }
        }

        return state;
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
        workerJobQueue.pause();

        final boolean terminatedGracefully;
        if (!skipGracefulTermination.get()) {
            terminatedGracefully = waitForTasksCompletion(timeout);
        } else {
            log.info("Terminating now and skip waiting for tasks completions.");
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
        // start shutdown
        new Thread(
            () -> {
                try {
                    this.executorService.shutdown();
                    this.executorService.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    log.error("Fail to shutdown the worker", e);
                }
            },
            "worker-shutdown"
        ).start();

        // interrupt RealtimeThread since never ending
        this.workerThreadReferences
            .stream()
            .filter(t -> t instanceof WorkerTriggerRealtimeThread)
            .map(t -> (WorkerTriggerRealtimeThread) t)
            .forEach(t -> {
                t.interrupt();

                logService.logTrigger(
                    t.getWorkerTrigger().getTriggerContext(),
                    t.getWorkerTrigger().getConditionContext().getRunContext().logger(),
                    Level.INFO,
                    "Type {} interrupted",
                    t.getWorkerTrigger().getTrigger().getType()
                );
            });

        // wait for task completion
        final AtomicBoolean cleanShutdown = new AtomicBoolean(false);
        Await.until(
            () -> {
                if (this.executorService.isTerminated() || this.workerThreadReferences.isEmpty()) {
                    log.info("All working threads are terminated.");

                    // we ensure that last produce message are send
                    closeQueue();
                    cleanShutdown.set(true);
                    return true;
                }

                log.warn(
                    "Waiting for all worker threads to terminate (remaining: {}).",
                    this.workerThreadReferences.size()
                );
                return false;
            },
            Duration.ofSeconds(1)
        );

        // cleanup
        return cleanShutdown.get();
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
        this.executorService.shutdownNow();
    }

    public List<WorkerJob> getWorkerThreadTasks() throws Exception {
        return this.workerThreadReferences
            .stream()
            .map(throwFunction(workerThread -> {
                if (workerThread instanceof WorkerTaskThread workerTaskThread) {
                    return workerTaskThread.workerTask;
                } else if (workerThread instanceof AbstractWorkerTriggerThread workerTriggerThread) {
                    return workerTriggerThread.workerTrigger;
                } else {
                    throw new Exception("Invalid thread type: '" + workerThread.getClass().getName() + "'");
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
