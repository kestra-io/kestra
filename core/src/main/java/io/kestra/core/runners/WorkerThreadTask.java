package io.kestra.core.runners;

import dev.failsafe.Failsafe;
import dev.failsafe.Timeout;
import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import lombok.Getter;
import org.slf4j.Logger;

import java.time.Duration;

import static io.kestra.core.models.flows.State.Type.*;

public class WorkerThreadTask extends AbstractWorkerThread {
    RunnableTask<?> task;
    RunContext runContext;
    MetricRegistry metricRegistry;

    @Getter
    WorkerTask workerTask;

    @Getter
    Output taskOutput;

    public WorkerThreadTask(Logger logger, WorkerTask workerTask, RunnableTask<?> task, RunContext runContext, MetricRegistry metricRegistry) {
        super(logger);
        this.workerTask = workerTask;
        this.task = task;
        this.runContext = runContext;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void run() {
        Thread.currentThread().setContextClassLoader(this.task.getClass().getClassLoader());

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
            this.exceptionHandler(this, new TimeoutExceededException(workerTaskTimeout, e));
        } catch (Exception e) {
            this.exceptionHandler(this, e);
        }
    }
}
