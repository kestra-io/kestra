package org.kestra.core.runners;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import io.micronaut.context.ApplicationContext;
import lombok.Getter;
import net.jodah.failsafe.Failsafe;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.TaskRunAttempt;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.Output;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.retrys.AbstractRetry;
import org.kestra.core.queues.QueueException;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.serializers.JacksonMapper;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Worker implements Runnable {
    private ApplicationContext applicationContext;
    private QueueInterface<WorkerTask> workerTaskQueue;
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private MetricRegistry metricRegistry;

    private Map<Long, AtomicInteger> metricRunningCount = new ConcurrentHashMap<>();

    public Worker(
        ApplicationContext applicationContext,
        QueueInterface<WorkerTask> workerTaskQueue,
        QueueInterface<WorkerTaskResult> workerTaskResultQueue,
        MetricRegistry metricRegistry
    ) {
        this.applicationContext = applicationContext;
        this.workerTaskQueue = workerTaskQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void run() {
        this.workerTaskQueue.receive(Worker.class, this::run);
    }

    public void run(WorkerTask workerTask) throws QueueException {
        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_STARTED_COUNT, metricRegistry.tags(workerTask))
            .increment();

        workerTask.logger().info(
            "[namespace: {}] [flow: {}] [task: {}] [execution: {}] [taskrun: {}] Type {} started",
            workerTask.getTaskRun().getNamespace(),
            workerTask.getTaskRun().getFlowId(),
            workerTask.getTaskRun().getTaskId(),
            workerTask.getTaskRun().getExecutionId(),
            workerTask.getTaskRun().getId(),
            workerTask.getTask().getClass().getSimpleName()
        );

        workerTask = workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING));
        this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask));

        if (workerTask.getTask() instanceof RunnableTask) {
            AtomicReference<WorkerTask> current = new AtomicReference<>(workerTask);

            // run
            WorkerTask finalWorkerTask = Failsafe
                .with(AbstractRetry.<WorkerTask>retryPolicy(workerTask.getTask().getRetry())
                    .handleResultIf(result -> result.getTaskRun().lastAttempt() != null &&
                        Objects.requireNonNull(result.getTaskRun().lastAttempt()).getState().isFailed()
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
                // log
                workerTask.logger().info(
                    "[namespace: {}] [flow: {}] [task: {}] [execution: {}] [taskrun: {}] Type {} with state {} completed in {}",
                    workerTask.getTaskRun().getNamespace(),
                    workerTask.getTaskRun().getFlowId(),
                    workerTask.getTaskRun().getTaskId(),
                    workerTask.getTaskRun().getExecutionId(),
                    workerTask.getTaskRun().getId(),
                    finalWorkerTask.getTask().getClass().getSimpleName(),
                    finalWorkerTask.getTaskRun().getState().getCurrent(),
                    finalWorkerTask.getTaskRun().getState().humanDuration()
                );

                metricRegistry
                    .counter(MetricRegistry.METRIC_WORKER_ENDED_COUNT, metricRegistry.tags(finalWorkerTask))
                    .increment();

                metricRegistry
                    .timer(MetricRegistry.METRIC_WORKER_ENDED_DURATION, metricRegistry.tags(finalWorkerTask))
                    .record(finalWorkerTask.getTaskRun().getState().getDuration());
            }
        }
    }

    private WorkerTask runAttempt(WorkerTask workerTask) {
        RunnableTask<?> task = (RunnableTask<?>) workerTask.getTask();

        RunContext runContext = workerTask
            .getRunContext()
            .forWorker(this.applicationContext, workerTask.getTaskRun());

        Logger logger = runContext.logger();

        TaskRunAttempt.TaskRunAttemptBuilder builder = TaskRunAttempt.builder()
            .state(new State());

        AtomicInteger metricRunningCount = getMetricRunningCount(workerTask);

        metricRunningCount.incrementAndGet();

        WorkerThread workerThread = new WorkerThread(logger, task, runContext);
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
            workerThread.join();
            state = workerThread.getTaskState();
        } catch (InterruptedException e) {
            logger.error("Failed to join WorkerThread {}", e.getMessage(), e);
            state = State.Type.FAILED;
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

    public List<TaskRunAttempt> addAttempt(WorkerTask workerTask, TaskRunAttempt taskRunAttempt) {
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

    @Getter
    public static class WorkerThread extends Thread {
        Logger logger;
        RunnableTask<?> task;
        RunContext runContext;

        Output taskOutput;
        org.kestra.core.models.flows.State.Type taskState;

        public WorkerThread(Logger logger, RunnableTask<?> task, RunContext runContext) {
            super("WorkerThread");
            this.setUncaughtExceptionHandler(this::exceptionHandler);

            this.logger = logger;
            this.task = task;
            this.runContext = runContext;
        }

        @Override
        public void run() {
            try {
                taskOutput = task.run(runContext);
                taskState = org.kestra.core.models.flows.State.Type.SUCCESS;
            } catch (Exception e) {
                this.exceptionHandler(this, e);
            }
        }

        private void exceptionHandler(Thread t, Throwable e) {
            logger.error(e.getMessage(), e);
            taskState = org.kestra.core.models.flows.State.Type.FAILED;
        }
    }
}
