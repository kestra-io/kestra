package org.kestra.core.runners;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import io.micronaut.context.ApplicationContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.kestra.core.queues.QueueException;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.TaskRunAttempt;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.Output;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.serializers.JacksonMapper;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Worker implements Runnable {
    private ApplicationContext applicationContext;
    private QueueInterface<WorkerTask> workerTaskQueue;
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private MetricRegistry metricRegistry;

    private Map<Long, AtomicInteger> metricRunningCount = new HashMap<>();

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
            "[execution: {}] [taskrun: {}] Task {} (type: {}) started",
            workerTask.getTaskRun().getExecutionId(),
            workerTask.getTaskRun().getId(),
            workerTask.getTaskRun().getTaskId(),
            workerTask.getTask().getClass().getSimpleName()
        );

        workerTask = workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING));
        this.workerTaskResultQueue.emit(new WorkerTaskResult(workerTask));

        if (workerTask.getTask() instanceof RunnableTask) {
            AtomicReference<WorkerTask> current = new AtomicReference<>(workerTask);

            // run
            WorkerTask finalWorkerTask = Failsafe
                .with(this.retryPolicy(workerTask.getTask())
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
            List<TaskRunAttempt> attempts = finalWorkerTask.getTaskRun().getAttempts();
            TaskRunAttempt lastAttempt = attempts.get(attempts.size() - 1);
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
                finalWorkerTask.logger().info(
                    "[execution: {}] [taskrun: {}] Task {} (type: {}) with state {} completed in {}",
                    finalWorkerTask.getTaskRun().getExecutionId(),
                    finalWorkerTask.getTaskRun().getId(),
                    finalWorkerTask.getTaskRun().getTaskId(),
                    finalWorkerTask.getTask().getClass().getSimpleName(),
                    finalWorkerTask.getTaskRun().getState(),
                    finalWorkerTask.getTaskRun().getState().humanDuration()
                );

                metricRegistry
                    .counter(MetricRegistry.METRIC_WORKER_ENDED_COUNT, metricRegistry.tags(finalWorkerTask))
                    .increment();
            }
        }
    }

    private RetryPolicy<WorkerTask> retryPolicy(Task task) {
        if (task.getRetry() != null) {
            return task.getRetry().toPolicy();
        }

        return new RetryPolicy<WorkerTask>()
            .withMaxAttempts(1);
    }

    private WorkerTask runAttempt(WorkerTask workerTask) {
        RunnableTask<?> task = (RunnableTask<?>) workerTask.getTask();
        State.Type state;

        RunContext runContext = workerTask
            .getRunContext()
            .forWorker(this.applicationContext, workerTask.getTaskRun());

        Logger logger = runContext.logger(workerTask.getTask().getClass());

        TaskRunAttempt.TaskRunAttemptBuilder builder = TaskRunAttempt.builder()
            .state(new State());

        Output output = null;
        AtomicInteger metricRunningCount = getMetricRunningCount(workerTask);

        try {
            metricRunningCount.incrementAndGet();
            output = task.run(runContext);
            state = State.Type.SUCCESS;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            state = State.Type.FAILED;
        } finally {
            metricRunningCount.decrementAndGet();
        }

        // attempt
        TaskRunAttempt taskRunAttempt = builder
            .logs(runContext.logs())
            .metrics(runContext.metrics())
            .build()
            .withState(state);

        if (output != null) {
            logger.debug("Outputs\n{}", JacksonMapper.log(output));
        }

        if (runContext.metrics().size() > 0) {
            logger.trace("Metrics\n{}", JacksonMapper.log(runContext.metrics()));
        }

        ImmutableList<TaskRunAttempt> attempts = ImmutableList.<TaskRunAttempt>builder()
            .addAll(workerTask.getTaskRun().getAttempts() == null ? new ArrayList<>() : workerTask.getTaskRun().getAttempts())
            .add(taskRunAttempt)
            .build();

        // save outputs
        return workerTask
            .withTaskRun(
                workerTask.getTaskRun()
                    .withAttempts(attempts)
                    .withOutputs(output != null ? output.toMap() : ImmutableMap.of())
            );
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
}
