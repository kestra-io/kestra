package org.kestra.core.runners;

import com.google.common.collect.ImmutableList;
import io.micronaut.context.ApplicationContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.kestra.core.models.executions.TaskRunAttempt;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.storages.StorageInterface;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class Worker implements Runnable {
    private StorageInterface storageInterface;
    private ApplicationContext applicationContext;
    private QueueInterface<WorkerTask> workerTaskQueue;
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    public Worker(
        StorageInterface storageInterface,
        ApplicationContext applicationContext,
        QueueInterface<WorkerTask> workerTaskQueue,
        QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        this.storageInterface = storageInterface;
        this.applicationContext = applicationContext;
        this.workerTaskQueue = workerTaskQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
    }

    @Override
    public void run() {
        this.workerTaskQueue.receive(Worker.class, this::run);
    }

    public void run(WorkerTask workerTask) {
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
            this.workerTaskResultQueue.emit(new WorkerTaskResult(finalWorkerTask));

            // log
            finalWorkerTask.logger().info(
                "[execution: {}] [taskrun: {}] Task {} (type: {}) with state {} completed in {}",
                finalWorkerTask.getTaskRun().getExecutionId(),
                finalWorkerTask.getTaskRun().getId(),
                finalWorkerTask.getTaskRun().getTaskId(),
                finalWorkerTask.getTask().getClass().getSimpleName(),
                state,
                finalWorkerTask.getTaskRun().getState().humanDuration()
            );
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
        Logger logger = workerTask.logger();
        RunnableTask task = (RunnableTask) workerTask.getTask();
        State.Type state;

        RunContext runContext = workerTask
            .getRunContext()
            .withStorageInterface(this.storageInterface)
            .withApplicationContext(this.applicationContext)
            .updateVariablesForWorker(workerTask.getTaskRun());

        TaskRunAttempt.TaskRunAttemptBuilder builder = TaskRunAttempt.builder()
            .state(new State());

        RunOutput output = null;

        try {
            output = task.run(runContext);
            state = State.Type.SUCCESS;
        } catch (Exception e) {
            logger.error("Failed tasks:" + e.getMessage(), e);
            state = State.Type.FAILED;
        }

        // attempt
        TaskRunAttempt taskRunAttempt = builder
            .logs(runContext.logs())
            .metrics(runContext.metrics())
            .build()
            .withState(state);

        if (output != null && output.getOutputs() != null) {
            logger.debug("Outputs\n{}", JacksonMapper.log(output.getOutputs()));
        }

        if (runContext.getMetrics() != null) {
            logger.trace("Metrics\n{}", JacksonMapper.log(runContext.getMetrics()));
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
                    .withOutputs(output != null ? output.getOutputs() : null)
            );
    }
}
