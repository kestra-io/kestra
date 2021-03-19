package io.kestra.core.runners;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Timeout;
import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.WorkerTaskQueueInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ExecutorsUtils;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class Worker implements Runnable, Closeable {
    private final ApplicationContext applicationContext;
    private final WorkerTaskQueueInterface workerTaskQueue;
    private final QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private final QueueInterface<ExecutionKilled> executionKilledQueue;
    private final MetricRegistry metricRegistry;

    private final Set<String> killedExecution = ConcurrentHashMap.newKeySet();
    private final ExecutorService executors;

    @Getter
    private final Map<Long, AtomicInteger> metricRunningCount = new ConcurrentHashMap<>();

    @Getter
    private final List<WorkerThread> workerThreadReferences = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public Worker(ApplicationContext applicationContext, int thread) {
        this.applicationContext = applicationContext;
        this.workerTaskQueue = applicationContext.getBean(WorkerTaskQueueInterface.class);
        this.workerTaskResultQueue = (QueueInterface<WorkerTaskResult>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
        );
        this.executionKilledQueue = (QueueInterface<ExecutionKilled>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.KILL_NAMED)
        );
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);

        ExecutorsUtils executorsUtils = applicationContext.getBean(ExecutorsUtils.class);
        this.executors = executorsUtils.fixedThreadPool(thread, "worker");
    }

    @Override
    public void run() {
        this.executionKilledQueue.receive(executionKilled -> {
            if (executionKilled != null) {
                // @FIXME: the hashset will never expire killed execution
                killedExecution.add(executionKilled.getExecutionId());
            }

            if (executionKilled != null) {
                synchronized (this) {
                    workerThreadReferences
                        .stream()
                        .filter(workerThread -> executionKilled.getExecutionId().equals(workerThread.getWorkerTask().getTaskRun().getExecutionId()))
                        .forEach(WorkerThread::kill);
                }
            }
        });

        this.workerTaskQueue.receive(
            Worker.class,
            workerTask -> {
                executors.execute(() -> this.run(workerTask));
            }
        );
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private void run(WorkerTask workerTask) throws QueueException {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_STARTED_COUNT, metricRegistry.tags(workerTask))
            .increment();

        if (workerTask.getTaskRun().getState().getCurrent() == State.Type.CREATED) {
            metricRegistry
                .timer(MetricRegistry.METRIC_WORKER_QUEUED_DURATION, metricRegistry.tags(workerTask))
                .record(Duration.between(
                    workerTask.getTaskRun().getState().getStartDate(), now()
                ));
        }

        workerTask.logger().info(
            "[namespace: {}] [flow: {}] [task: {}] [execution: {}] [taskrun: {}] [value: {}] Type {} started",
            workerTask.getTaskRun().getNamespace(),
            workerTask.getTaskRun().getFlowId(),
            workerTask.getTaskRun().getTaskId(),
            workerTask.getTaskRun().getExecutionId(),
            workerTask.getTaskRun().getId(),
            workerTask.getTaskRun().getValue(),
            workerTask.getTask().getClass().getSimpleName()
        );

        if (workerTask.logger().isDebugEnabled()) {
            workerTask.logger().debug("Variables\n{}", JacksonMapper.log(workerTask.getRunContext().getVariables()));
        }

        workerTask = workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING));
        this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask));

        if (workerTask.getTask() instanceof RunnableTask) {
            // killed cased
            if (killedExecution.contains(workerTask.getTaskRun().getExecutionId())) {
                workerTask = workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.KILLED));
                this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask));

                this.logTerminated(workerTask);

                return;
            }

            AtomicReference<WorkerTask> current = new AtomicReference<>(workerTask);

            // run
            WorkerTask finalWorkerTask = Failsafe
                .with(AbstractRetry.<WorkerTask>retryPolicy(workerTask.getTask().getRetry())
                    .handleResultIf(result -> result.getTaskRun().lastAttempt() != null &&
                        Objects.requireNonNull(result.getTaskRun().lastAttempt()).getState().getCurrent() == State.Type.FAILED
                    )
                    .onRetry(e -> {
                        current.set(e.getLastResult());

                        metricRegistry
                            .counter(
                                MetricRegistry.METRIC_WORKER_RETRYED_COUNT,
                                metricRegistry.tags(
                                    current.get(),
                                    MetricRegistry.TAG_ATTEMPT_COUNT, String.valueOf(e.getAttemptCount())
                                )
                            )
                            .increment();

                        this.workerTaskResultQueue.emit(
                            new WorkerTaskResult(e.getLastResult())
                        );
                    })/*,
                    Fallback.of(current::get)*/
                )
                .get(() -> this.runAttempt(current.get()));

            // get last state
            TaskRunAttempt lastAttempt = finalWorkerTask.getTaskRun().lastAttempt();
            if (lastAttempt == null) {
                throw new IllegalStateException("Can find lastAttempt on taskRun '" +
                    finalWorkerTask.getTaskRun().toString(true) + "'"
                );
            }
            State.Type state = lastAttempt.getState().getCurrent();

            // emit
            finalWorkerTask = finalWorkerTask.withTaskRun(finalWorkerTask.getTaskRun().withState(state));

            // if resulting object can't be emitted (mostly size of message), we just can't emit it like that.
            // So we just tryed to failed the status of the worker task, in this case, no log can't be happend, just
            // changing status must work in order to finish current task (except if we are near the upper bound size).
            try {
                this.workerTaskResultQueue.emit(new WorkerTaskResult(finalWorkerTask));
            } catch (QueueException e) {
                finalWorkerTask = workerTask
                    .withTaskRun(workerTask.getTaskRun()
                        .withState(State.Type.FAILED)
                    );
                this.workerTaskResultQueue.emit(new WorkerTaskResult(finalWorkerTask));
            } finally {
                this.logTerminated(finalWorkerTask);
            }
        }
    }

    private void logTerminated(WorkerTask workerTask) {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_ENDED_COUNT, metricRegistry.tags(workerTask))
            .increment();

        metricRegistry
            .timer(MetricRegistry.METRIC_WORKER_ENDED_DURATION, metricRegistry.tags(workerTask))
            .record(workerTask.getTaskRun().getState().getDuration());

        workerTask.logger().info(
            "[namespace: {}] [flow: {}] [task: {}] [execution: {}] [taskrun: {}] [value: {}] Type {} with state {} completed in {}",
            workerTask.getTaskRun().getNamespace(),
            workerTask.getTaskRun().getFlowId(),
            workerTask.getTaskRun().getTaskId(),
            workerTask.getTaskRun().getExecutionId(),
            workerTask.getTaskRun().getId(),
            workerTask.getTaskRun().getValue(),
            workerTask.getTask().getClass().getSimpleName(),
            workerTask.getTaskRun().getState().getCurrent(),
            workerTask.getTaskRun().getState().humanDuration()
        );
    }

    private WorkerTask runAttempt(WorkerTask workerTask) {
        RunnableTask<?> task = (RunnableTask<?>) workerTask.getTask();

        RunContext runContext = workerTask
            .getRunContext()
            .forWorker(this.applicationContext, workerTask.getTaskRun());

        Logger logger = runContext.logger();

        TaskRunAttempt.TaskRunAttemptBuilder builder = TaskRunAttempt.builder()
            .state(new State().withState(State.Type.RUNNING));

        AtomicInteger metricRunningCount = getMetricRunningCount(workerTask);

        metricRunningCount.incrementAndGet();

        WorkerThread workerThread = new WorkerThread(logger, workerTask, task, runContext, metricRegistry);
        workerThread.start();

        // emit attempts
        this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask
            .withTaskRun(
                workerTask.getTaskRun()
                    .withAttempts(this.addAttempt(workerTask, builder.build()))
            )
        ));

        // run it
        State.Type state;
        try {
            synchronized (this) {
                workerThreadReferences.add(workerThread);
            }
            workerThread.join();
            state = workerThread.getTaskState();
        } catch (InterruptedException e) {
            logger.error("Failed to join WorkerThread {}", e.getMessage(), e);
            state = State.Type.FAILED;
        } finally {
            synchronized (this) {
                workerThreadReferences.remove(workerThread);
            }
        }

        metricRunningCount.decrementAndGet();

        // attempt
        TaskRunAttempt taskRunAttempt = builder
            .metrics(runContext.metrics())
            .build()
            .withState(state);

        // logs
        if (workerThread.getTaskOutput() != null) {
            logger.debug("Outputs\n{}", JacksonMapper.log(workerThread.getTaskOutput()));
        }

        if (runContext.metrics().size() > 0) {
            logger.trace("Metrics\n{}", JacksonMapper.log(runContext.metrics()));
        }

        // save outputs
        List<TaskRunAttempt> attempts = this.addAttempt(workerTask, taskRunAttempt);

        return workerTask
            .withTaskRun(
                workerTask.getTaskRun()
                    .withAttempts(attempts)
                    .withOutputs(workerThread.getTaskOutput() != null ? workerThread.getTaskOutput().toMap() : ImmutableMap.of())
            );
    }

    private List<TaskRunAttempt> addAttempt(WorkerTask workerTask, TaskRunAttempt taskRunAttempt) {
        return ImmutableList.<TaskRunAttempt>builder()
            .addAll(workerTask.getTaskRun().getAttempts() == null ? new ArrayList<>() : workerTask.getTaskRun().getAttempts())
            .add(taskRunAttempt)
            .build();
    }

    @SuppressWarnings("UnstableApiUsage")
    public AtomicInteger getMetricRunningCount(WorkerTask workerTask) {
        String[] tags = this.metricRegistry.tags(workerTask);
        Arrays.sort(tags);

        long index = Hashing
            .goodFastHash(64)
            .hashString(String.join("-", tags), Charsets.UTF_8)
            .asLong();

        return this.metricRunningCount
            .computeIfAbsent(index, l -> metricRegistry.gauge(
                MetricRegistry.METRIC_WORKER_RUNNING_COUNT,
                new AtomicInteger(0),
                metricRegistry.tags(workerTask)
            ));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void close() throws IOException {
        workerTaskQueue.close();
        executionKilledQueue.close();
        new Thread(
            () -> {
            try {
                this.executors.shutdown();
                this.executors.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error("Failed to shutdown workers executors", e);
            }
        },
            "worker-shutdown"
        ).start();

        Await.until(
            () -> {
                if (this.executors.isTerminated() && this.getWorkerThreadReferences().size() == 0) {
                    log.info("No more workers busy, shutting down!");

                    // we ensure that last produce message are send
                    try {
                        this.workerTaskResultQueue.close();
                    } catch (IOException e) {
                        log.error("Failed to close workerTaskResultQueue", e);
                    }

                    return true;
                }

                log.warn(
                    "Waiting worker with still {} thread(s) running, waiting!",
                    this.getWorkerThreadReferences().size()
                );

                return false;
            },
            Duration.ofSeconds(1)
        );
    }

    @Getter
    public static class WorkerThread extends Thread {
        Logger logger;
        WorkerTask workerTask;
        RunnableTask<?> task;
        RunContext runContext;
        MetricRegistry metricRegistry;

        Output taskOutput;
        io.kestra.core.models.flows.State.Type taskState;
        boolean killed = false;

        public WorkerThread(Logger logger, WorkerTask workerTask, RunnableTask<?> task, RunContext runContext, MetricRegistry metricRegistry) {
            super("WorkerThread");
            this.setUncaughtExceptionHandler(this::exceptionHandler);

            this.logger = logger;
            this.workerTask = workerTask;
            this.task = task;
            this.runContext = runContext;
            this.metricRegistry = metricRegistry;
        }

        @Override
        public void run() {
            try {
                // timeout
                if (workerTask.getTask().getTimeout() != null) {
                    Failsafe
                        .with(Timeout
                            .<WorkerTask>of(workerTask.getTask().getTimeout())
                            .withCancel(true)
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

                taskState = io.kestra.core.models.flows.State.Type.SUCCESS;
            } catch (net.jodah.failsafe.TimeoutExceededException e) {
                this.exceptionHandler(this, new TimeoutExceededException(workerTask.getTask().getTimeout(), e));
            } catch (Exception e) {
                this.exceptionHandler(this, e);
            }
        }

        @Synchronized
        public void kill() {
            this.killed = true;
            taskState = io.kestra.core.models.flows.State.Type.KILLED;
            this.interrupt();
        }

        @Synchronized
        private void exceptionHandler(Thread t, Throwable e) {
            if (!this.killed) {
                logger.error(e.getMessage(), e);
                taskState = io.kestra.core.models.flows.State.Type.FAILED;
            }
        }
    }
}
