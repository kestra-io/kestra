package io.kestra.core.runners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.PollingTriggerInterface;
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
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Timeout;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.models.flows.State.Type.*;
import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.KILLED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;
import static io.kestra.core.models.flows.State.Type.WARNING;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_FORCED;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_GRACEFULLY;

@Slf4j
@Introspected
public class Worker implements Service, Runnable, AutoCloseable {
    private final static ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final String SERVICE_PROPS_WORKER_GROUP = "worker.group";

    private final ApplicationContext applicationContext;
    private final WorkerJobQueueInterface workerJobQueue;
    private final QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private final QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;
    private final QueueInterface<ExecutionKilled> executionKilledQueue;
    private final QueueInterface<MetricEntry> metricEntryQueue;
    private final MetricRegistry metricRegistry;
    private final ServerConfig serverConfig;

    private final Set<String> killedExecution = ConcurrentHashMap.newKeySet();

    // package private to allow its usage within tests
    final ExecutorService executors;

    @Getter
    private final Map<Long, AtomicInteger> metricRunningCount = new ConcurrentHashMap<>();

    @VisibleForTesting
    @Getter
    private final Map<String, AtomicInteger> evaluateTriggerRunningCount = new ConcurrentHashMap<>();

    private final List<WorkerThread> workerThreadReferences = new ArrayList<>();

    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AtomicBoolean skipGracefulTermination = new AtomicBoolean(false);

    @Getter
    private final String workerGroup;

    private final LogService logService;

    private final String id;
    private final AtomicReference<ServiceState> state = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    @Inject
    public Worker(ApplicationContext applicationContext, int thread, String workerGroupKey) {
        // FIXME: For backward-compatibility with Kestra 0.15.x and earliest we still used UUID for Worker ID instead of IdUtils
        this(applicationContext, thread, workerGroupKey, UUID.randomUUID().toString());
    }

    @VisibleForTesting
    public Worker(ApplicationContext applicationContext, int thread, String workerGroupKey, String id) {
        this.id = id;
        this.applicationContext = applicationContext;
        this.workerJobQueue = applicationContext.getBean(WorkerJobQueueInterface.class);
        this.eventPublisher = applicationContext.getBean(ApplicationEventPublisher.class);

        this.workerTaskResultQueue = (QueueInterface<WorkerTaskResult>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
        );
        this.workerTriggerResultQueue = (QueueInterface<WorkerTriggerResult>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTRIGGERRESULT_NAMED)
        );
        this.executionKilledQueue = (QueueInterface<ExecutionKilled>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.KILL_NAMED)
        );
        this.metricEntryQueue = (QueueInterface<MetricEntry>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.METRIC_QUEUE)
        );
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);

        ExecutorsUtils executorsUtils = applicationContext.getBean(ExecutorsUtils.class);
        this.executors = executorsUtils.maxCachedThreadPool(thread, "worker");

        WorkerGroupService workerGroupService = applicationContext.getBean(WorkerGroupService.class);
        this.workerGroup = workerGroupService.resolveGroupFromKey(workerGroupKey);

        this.logService = applicationContext.getBean(LogService.class);

        this.serverConfig = applicationContext.getBean(ServerConfig.class);
        setState(ServiceState.CREATED);
    }

    @Override
    public void run() {
        setState(ServiceState.RUNNING);
        this.executionKilledQueue.receive(executionKilled -> {
            if(executionKilled == null || !executionKilled.isLeft()) {
                return;
            }
            ExecutionKilled.State state = executionKilled.getLeft().getState();
            if (state != null && state != ExecutionKilled.State.EXECUTED) {
                return;
            }
            // @FIXME: the hashset will never expire killed execution
            killedExecution.add(executionKilled.getLeft().getExecutionId());
            synchronized (this) {
                workerThreadReferences
                    .stream()
                    .filter(workerThread -> executionKilled.getLeft().getExecutionId().equals(workerThread.getWorkerTask().getTaskRun().getExecutionId()))
                    .forEach(WorkerThread::kill);
            }
        });

        this.workerJobQueue.receive(
            this.workerGroup,
            Worker.class,
            either -> {
                executors.execute(() -> {
                    if (either.isRight()) {
                        log.error("Unable to deserialize a worker job: {}", either.getRight().getMessage());
                        handleDeserializationError(either.getRight());
                        return;
                    }

                    WorkerJob workerTask = either.getLeft();
                    if (workerTask instanceof WorkerTask task) {
                        handleTask(task);
                    }
                    else if (workerTask instanceof WorkerTrigger trigger) {
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
            }
            catch (IOException e) {
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
        }
        else {
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
            evaluate = evaluate.map( execution -> {
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
                    this.evaluateTriggerRunningCount.computeIfAbsent(workerTrigger.getTriggerContext().uid(), s -> metricRegistry
                        .gauge(MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT, new AtomicInteger(0), metricRegistry.tags(workerTrigger.getTriggerContext(), workerGroup)));
                    this.evaluateTriggerRunningCount.get(workerTrigger.getTriggerContext().uid()).addAndGet(1);

                    try {
                        if (workerTrigger.getTrigger() instanceof PollingTriggerInterface pollingTrigger) {
                            RunContext runContext = this.initRunContextForTrigger(workerTrigger);

                            Optional<Execution> evaluate = pollingTrigger.evaluate(
                                workerTrigger.getConditionContext().withRunContext(runContext),
                                workerTrigger.getTriggerContext()
                            );

                            this.publishTriggerExecution(workerTrigger, evaluate);
                        }
                    } catch (Exception e) {
                        this.handleTriggerError(workerTrigger, e);
                    } finally {
                        workerTrigger.getConditionContext().getRunContext().cleanup();
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

        if (workerTask.getTask().isAllowFailure() && !finalWorkerTask.getTaskRun().shouldBeRetried(workerTask.getTask()) && state.isFailed()) {
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
            "[date: {}] Evaluate Failed with error '{}'",
            workerTrigger.getTriggerContext().getDate(),
            e.getMessage(),
            e
        );

        if (logger.isTraceEnabled()) {
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

        WorkerThread workerThread = new WorkerThread(logger, workerTask, task, runContext, metricRegistry, workerGroup);

        // emit attempts
        this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask
            .withTaskRun(
                workerTask.getTaskRun()
                    .withAttempts(this.addAttempt(workerTask, builder.build()))
            )
        ));

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
            state  = workerTask.getTask().isAllowFailure() ? WARNING : FAILED;
        } finally {
            synchronized (this) {
                workerThreadReferences.remove(workerThread);
            }
        }

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

    @Override
    public void close() throws Exception {
        closeWorker(serverConfig.terminationGracePeriod());
    }

    @VisibleForTesting
    public void closeWorker(Duration timeout) throws Exception {
        log.info("Terminating.");
        setState(ServiceState.TERMINATING);
        workerJobQueue.pause();

        final boolean terminatedGracefully;
        if (!skipGracefulTermination.get()) {
            terminatedGracefully = waitForTasksCompletion(timeout);
        } else {
            log.info("Terminating now and skip waiting for tasks completions.");
            this.executors.shutdownNow();
            closeWorkerTaskResultQueue();
            terminatedGracefully = false;
        }

        ServiceState state = terminatedGracefully ? TERMINATED_GRACEFULLY : TERMINATED_FORCED;
        setState(state);
        log.info("Worker closed ({}).", state.name().toLowerCase());
    }

    private boolean waitForTasksCompletion(final Duration timeout) {
        new Thread(
            () -> {
                try {
                    this.executors.shutdown();
                    this.executors.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    log.error("Fail to shutdown the worker", e);
                }
            },
            "worker-shutdown"
        ).start();

        final AtomicBoolean cleanShutdown = new AtomicBoolean(false);
        Await.until(
            () -> {
                if (this.executors.isTerminated() || this.workerThreadReferences.isEmpty()) {
                    log.info("No more worker threads busy, shutting down!");

                    // we ensure that last produce message are send
                    closeWorkerTaskResultQueue();
                    cleanShutdown.set(true);
                    return true;
                }

                log.warn(
                    "Worker still has {} thread(s) running, waiting all threads to terminate before shutdown!",
                    this.workerThreadReferences.size()
                );

                return false;
            },
            Duration.ofSeconds(1)
        );
        return cleanShutdown.get();
    }

    private void closeWorkerTaskResultQueue() {
        try {
            this.workerTaskResultQueue.close();
        } catch (IOException e) {
            log.error("Failed to close the workerTaskResultQueue", e);
        }
    }

    @VisibleForTesting
    public void shutdown() throws IOException {
        this.executors.shutdownNow();
    }

    public List<WorkerTask> getWorkerThreadTasks() {
        return this.workerThreadReferences.stream().map(thread -> thread.workerTask).toList();
    }

    @Getter
    public static class WorkerThread extends Thread {
        Logger logger;
        WorkerTask workerTask;
        RunnableTask<?> task;
        RunContext runContext;
        MetricRegistry metricRegistry;
        String workerGroup;

        Output taskOutput;
        io.kestra.core.models.flows.State.Type taskState;
        volatile boolean killed = false;

        public WorkerThread(Logger logger, WorkerTask workerTask, RunnableTask<?> task, RunContext runContext, MetricRegistry metricRegistry, String workerGroup) {
            super("WorkerThread");
            this.setUncaughtExceptionHandler(this::exceptionHandler);

            this.logger = logger;
            this.workerTask = workerTask;
            this.task = task;
            this.runContext = runContext;
            this.metricRegistry = metricRegistry;
            this.workerGroup = workerGroup;
        }

        @Override
        public void run() {
            Thread.currentThread().setContextClassLoader(this.task.getClass().getClassLoader());

            try {
                // timeout
                if (workerTask.getTask().getTimeout() != null) {
                    Failsafe
                        .with(Timeout
                            .of(workerTask.getTask().getTimeout())
                            .withInterrupt(true)
                            .onFailure(event -> metricRegistry
                                .counter(
                                    MetricRegistry.METRIC_WORKER_TIMEOUT_COUNT,
                                    metricRegistry.tags(
                                        this.workerTask,
                                        MetricRegistry.TAG_ATTEMPT_COUNT, String.valueOf(event.getAttemptCount())
                                    )
                                )
                                .increment()
                            )
                        )
                        .run(() -> taskOutput = task.run(runContext));

                } else {
                    taskOutput = task.run(runContext);
                }

                taskState = SUCCESS;
                if (taskOutput != null && taskOutput.finalState().isPresent()) {
                    taskState = taskOutput.finalState().get();
                }
            } catch (net.jodah.failsafe.TimeoutExceededException e) {
                this.exceptionHandler(this, new TimeoutExceededException(workerTask.getTask().getTimeout(), e));
            } catch (Exception e) {
                this.exceptionHandler(this, e);
            }
        }

        @Synchronized
        public void kill() {
            this.killed = true;
            taskState = KILLED;
            this.interrupt();
        }

        private void exceptionHandler(Thread t, Throwable e) {
            if (!this.killed) {
                logger.error(e.getMessage(), e);
                taskState = FAILED;
            }
        }
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
