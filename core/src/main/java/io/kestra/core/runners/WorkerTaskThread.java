package io.kestra.core.runners;

import dev.failsafe.Failsafe;
import dev.failsafe.Timeout;
import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.triggers.WorkerTriggerInterface;
import lombok.Getter;

import java.time.Duration;

import static io.kestra.core.models.flows.State.Type.*;

public class WorkerTaskThread extends AbstractWorkerThread {
    RunnableTask<?> task;
    MetricRegistry metricRegistry;

    @Getter
    WorkerTask workerTask;

    @Getter
    Output taskOutput;

    public WorkerTaskThread(WorkerTask workerTask, RunnableTask<?> task, RunContext runContext, MetricRegistry metricRegistry) {
        super(runContext, task.getClass().getName(), task.getClass().getClassLoader());
        this.workerTask = workerTask;
        this.task = task;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void signalStop() {
        try {
            task.stop();
        } catch (Exception e) {
            logger.warn("Error while stopping task: '{}'", getType(), e);
        }
    }

    @Override
    protected void kill(final boolean markAsKilled) {
        try {
            task.kill();
        } catch (Exception e) {
            logger.warn("Error while killing task: '{}'", e.getMessage(), e);
        } finally {
            super.kill(markAsKilled); //interrupt
        }
    }

    @Override
    public void doRun() throws Exception {
        final Duration workerTaskTimeout = workerTask.getTask().getTimeout();
        try {
            if (workerTaskTimeout != null) {
                Timeout<Object> taskTimeout = Timeout
                    .builder(workerTaskTimeout)
                    .withInterrupt() // use to awake blocking tasks.
                    .build();
                Failsafe
                    .with(taskTimeout)
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
                    .run(() -> taskOutput = task.run(runContext));

            } else {
                taskOutput = task.run(runContext);
            }
            taskState = SUCCESS;
            if (taskOutput != null && taskOutput.finalState().isPresent()) {
                taskState = taskOutput.finalState().get();
            }
        } catch (dev.failsafe.TimeoutExceededException e) {
            kill(false);
            this.exceptionHandler(this, new TimeoutExceededException(workerTaskTimeout));
        }
    }
}
