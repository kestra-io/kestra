package io.kestra.worker.processors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.WorkerTaskRestartStrategy;
import io.kestra.core.services.VariablesService;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.trace.Tracer;
import io.kestra.core.utils.Hashing;
import io.kestra.core.utils.Logs;
import io.kestra.core.utils.TruthUtils;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.processors.internals.WorkerTaskCallable;
import io.kestra.worker.queues.WorkerQueue;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.kestra.core.models.flows.State.Type.CREATED;
import static io.kestra.core.models.flows.State.Type.RUNNING;
import static io.kestra.core.models.flows.State.Type.SKIPPED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;
import static io.kestra.core.models.flows.State.Type.WARNING;

@Slf4j
public class WorkerTaskProcessor extends AbstractWorkerJobProcessor<WorkerTask> {

    private final String workerId;
    private final String workerGroup;

    // SERVICES
    private final VariablesService variablesService;
    private final ServerConfig serverConfig;
    private final RunContextInitializer runContextInitializer;
    private final RunContextLoggerFactory runContextLoggerFactory;

    // METRICS
    private final Map<Long, AtomicInteger> metricRunningCount = new ConcurrentHashMap<>();

    // QUEUEs
    private final WorkerQueue<WorkerTaskResult> workerTaskResultQueue;
    private final WorkerQueue<MetricEntry> workerMetricQueue;

    public WorkerTaskProcessor(final String workerId,
                               final String workerGroup,
                               final ServerConfig serverConfig,
                               final MetricRegistry metricRegistry,
                               final WorkerSecurityService workerSecurityService,
                               final Tracer tracer,
                               final VariablesService variablesService,
                               final RunContextInitializer runContextInitializer,
                               final RunContextLoggerFactory runContextLoggerFactory,
                               final WorkerQueue<WorkerTaskResult> workerTaskResultQueue,
                               final WorkerQueue<MetricEntry> workerMetricQueue) {
        super(workerGroup, metricRegistry, workerSecurityService, tracer);
        this.runContextInitializer = runContextInitializer;
        this.runContextLoggerFactory = runContextLoggerFactory;
        this.workerGroup = workerGroup;
        this.workerId = workerId;
        this.variablesService = variablesService;
        this.serverConfig = serverConfig;
        this.workerTaskResultQueue = workerTaskResultQueue;
        this.workerMetricQueue = workerMetricQueue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doProcess(final WorkerTask workerTask) {
        Task task = workerTask.getTask();
        if (task instanceof RunnableTask) {
            runTask(workerTask, true);
        } else if (task instanceof WorkingDirectory workingDirectory) {
            runWorkingDirectory(workerTask, workingDirectory);
        } else {
            throw new IllegalArgumentException("Unable to process the task '" + task.getId() + "' as it's not a runnable task");
        }
    }

    private void runWorkingDirectory(WorkerTask workerTask, WorkingDirectory workingDirectory) {
        DefaultRunContext runContext = runContextInitializer.forWorkingDirectory(((DefaultRunContext) workerTask.getRunContext()), workerTask);
        final RunContext workingDirectoryRunContext = runContext.clone();

        try {
            // preExecuteTasks
            try {
                workingDirectory.preExecuteTasks(workingDirectoryRunContext, workerTask.getTaskRun());
            } catch (Exception e) {
                runContext.logger().error("Failed preExecuteTasks on WorkingDirectory: {}", e.getMessage(), e);
                workerTask = workerTask.withTaskRun(workerTask.fail());
                workerTaskResultQueue.put(new WorkerTaskResult(workerTask.getTaskRun()));
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
                        workerTaskResult = new WorkerTaskResult(currentWorkerTask.getTaskRun()
                            .withState(SKIPPED)
                            .addAttempt(TaskRunAttempt.builder().workerId(workerId).state(new State().withState(SKIPPED)).build()));
                        workerTaskResultQueue.put(workerTaskResult);
                    } else {
                        workerTaskResult = this.runTask(currentWorkerTask, false);
                    }
                } catch (IllegalVariableEvaluationException e) {
                    RunContextLogger contextLogger = runContextLoggerFactory.create(currentWorkerTask);
                    contextLogger.logger().error("Failed evaluating runIf: {}", e.getMessage(), e);
                    workerTaskResultQueue.put(new WorkerTaskResult(workerTask.fail()));
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
                workerTaskResultQueue.put(new WorkerTaskResult(workerTask.fail()));
            }
        } finally {
            this.logTerminated(workerTask);
            runContext.cleanup();
        }
    }

    private WorkerTaskResult runTask(WorkerTask workerTask, boolean cleanUp) {
        String[] metricTags = metricRegistry.tags(workerTask, workerGroup);

        this.metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_STARTED_COUNT, MetricRegistry.METRIC_WORKER_STARTED_COUNT_DESCRIPTION, metricTags)
            .increment();

        if (workerTask.getTaskRun().getState().getCurrent() == CREATED) {
            this.metricRegistry
                .timer(MetricRegistry.METRIC_WORKER_QUEUED_DURATION, MetricRegistry.METRIC_WORKER_QUEUED_DURATION_DESCRIPTION, metricTags)
                .record(Duration.between(
                    workerTask.getTaskRun().getState().getStartDate(), Instant.now()
                ));
        }

        try {
            // TODO
            /**
             if (!Boolean.TRUE.equals(workerTask.getTaskRun().getForceExecution()) && killedExecution.contains(workerTask.getTaskRun().getExecutionId())) {
             WorkerTaskResult workerTaskResult = new WorkerTaskResult(workerTask.getTaskRun().withState(KILLED));
             workerTaskResultQueue.produce(workerTaskResult);

             // We cannot remove the execution ID from the killedExecution in case the worker is processing multiple tasks of the execution
             // which can happens due to parallel processing.
             return workerTaskResult;
             }
             **/

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
                                        .workerId(this.workerId)
                                        .build();
                                    List<TaskRunAttempt> attempts = this.addAttempt(workerTask, attempt);
                                    TaskRun taskRun = workerTask.getTaskRun().withAttempts(attempts).withOutputs(variables).withState(SUCCESS);
                                    WorkerTaskResult workerTaskResult = new WorkerTaskResult(taskRun);
                                    workerTaskResultQueue.put(workerTaskResult);
                                    return workerTaskResult;
                                }
                            }
                        }
                    } catch (IOException | RuntimeException e) {
                        // in case of any exception, log an error and continue
                        runContext.logger().error("Unexpected exception while loading the cache for task '{}', the task will be executed instead.", workerTask.getTask().getId(), e);
                    }
                }
            }

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

            if (isStopped() && serverConfig.workerTaskRestartStrategy() != WorkerTaskRestartStrategy.NEVER && state.isFailed()) {
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
            workerTaskResultQueue.put(workerTaskResult);

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
                    Path archiveFile = runContext.workingDir().createTempFile(".zip");
                    Files.write(archiveFile, bos.toByteArray());
                    URI uri = runContext.storage().putCacheFile(archiveFile.toFile(), hash.get(), workerTask.getTaskRun().getValue());
                    runContext.logger().debug("Caching entry uploaded in URI {}", uri);
                } catch (IOException | RuntimeException e) {
                    // in case of any exception, log an error and continue
                    runContext.logger().error("Unexpected exception while uploading the cache entry for task '{}', the task not be cached.", workerTask.getTask().getId(), e);
                }
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

    private void logTerminated(WorkerTask workerTask) {
        final String[] tags = metricRegistry.tags(workerTask, workerGroup);

        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_ENDED_COUNT, MetricRegistry.METRIC_WORKER_ENDED_COUNT_DESCRIPTION, tags)
            .increment();

        metricRegistry
            .timer(MetricRegistry.METRIC_WORKER_ENDED_DURATION, MetricRegistry.METRIC_WORKER_ENDED_DURATION_DESCRIPTION, tags)
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

    private WorkerTask runAttempt(final RunContext runContext, final WorkerTask workerTask) {
        Logger logger = runContext.logger();

        if (!(workerTask.getTask() instanceof RunnableTask<?> task)) {
            // This should never happen but better to deal with it than crashing the Worker
            var state = State.Type.fail(workerTask.getTask());
            TaskRunAttempt attempt = TaskRunAttempt.builder()
                .state(new io.kestra.core.models.flows.State().withState(state))
                .workerId(this.workerId)
                .build();
            List<TaskRunAttempt> attempts = this.addAttempt(workerTask, attempt);
            TaskRun taskRun = workerTask.getTaskRun().withAttempts(attempts);
            logger.error("Unable to execute the task '{}': only runnable tasks can be executed by the worker but the task is of type {}", workerTask.getTask().getId(), workerTask.getTask().getClass());
            return workerTask.withTaskRun(taskRun);
        }

        TaskRunAttempt.TaskRunAttemptBuilder builder = TaskRunAttempt.builder()
            .state(new io.kestra.core.models.flows.State().withState(RUNNING))
            .workerId(this.workerId);

        // emit the attempt so the execution knows that the task is in RUNNING
        workerTaskResultQueue.put(new WorkerTaskResult(
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
        runContext.metrics()
            .stream()
            .map(metric -> MetricEntry.of(workerTask.getTaskRun(), metric, workerTask.getExecutionKind()))
            .forEach(workerMetricQueue::put);

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

    private List<TaskRunAttempt> addAttempt(WorkerTask workerTask, TaskRunAttempt taskRunAttempt) {
        return ImmutableList.<TaskRunAttempt>builder()
            .addAll(workerTask.getTaskRun().getAttempts() == null ? new ArrayList<>() : workerTask.getTaskRun().getAttempts())
            .add(taskRunAttempt)
            .build();
    }

    private Optional<String> hashTask(RunContext runContext, Task task) {
        try {
            var map = JacksonMapper.toMap(task);
            var rMap = runContext.render(map);
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

    private AtomicInteger getMetricRunningCount(final WorkerTask workerTask) {
        String[] tags = this.metricRegistry.tags(workerTask, workerGroup);
        Arrays.sort(tags);

        long index = Hashing.hashToLong(String.join("-", tags));

        return this.metricRunningCount
            .computeIfAbsent(index, l -> metricRegistry.gauge(
                MetricRegistry.METRIC_WORKER_RUNNING_COUNT,
                MetricRegistry.METRIC_WORKER_RUNNING_COUNT_DESCRIPTION,
                new AtomicInteger(0),
                tags
            ));
    }
}
