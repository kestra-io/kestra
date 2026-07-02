package io.kestra.worker.processors.internals;

import java.time.Duration;

import io.kestra.core.exceptions.TimeoutExceededException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;

import dev.failsafe.Failsafe;
import dev.failsafe.Timeout;
import lombok.Getter;

import static io.kestra.core.models.flows.State.Type.*;

public class WorkerTaskCallable extends AbstractWorkerCallable {
    RunnableTask<?> task;
    MetricRegistry metricRegistry;
    String workerGroup;

    @Getter
    WorkerTask workerTask;

    @Getter
    Output taskOutput;

    public WorkerTaskCallable(WorkerTask workerTask, RunnableTask<?> task, RunContext runContext, MetricRegistry metricRegistry, String workerGroup) {
        super(runContext, task.getClass().getName(), workerTask.uid(), task.getClass().getClassLoader());
        this.workerTask = workerTask;
        this.task = task;
        this.metricRegistry = metricRegistry;
        this.workerGroup = workerGroup;
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
            logger.warn("Error while killing task: '{}'", getType(), e);
        } finally {
            super.kill(markAsKilled); //interrupt
        }
    }

    @Override
    public State.Type doCall() throws Exception {
        final Duration workerTaskTimeout = runContext.render(workerTask.getTask().getTimeout()).as(Duration.class).orElse(null);

        try {
            if (workerTaskTimeout != null) {
                Timeout<Object> taskTimeout = Timeout
                    .builder(workerTaskTimeout)
                    .withInterrupt() // use to awake blocking tasks.
                    .build();
                Failsafe
                    .with(taskTimeout)
                    .onFailure(
                        event -> metricRegistry
                            .counter(
                                MetricRegistry.METRIC_WORKER_TIMEOUT_COUNT,
                                MetricRegistry.METRIC_WORKER_TIMEOUT_COUNT_DESCRIPTION,
                                metricRegistry.tags(
                                    this.workerTask,
                                    this.workerGroup,
                                    MetricRegistry.TAG_ATTEMPT_COUNT, String.valueOf(event.getAttemptCount())
                                )
                            )
                            .increment()
                    )
                    .run(() -> taskOutput = task.run(runContext));
            } else {
                taskOutput = task.run(runContext);
            }

            if (taskOutput != null && taskOutput.finalState().isPresent()) {
                return taskOutput.finalState().get();
            }
            return SUCCESS;
        } catch (dev.failsafe.TimeoutExceededException e) {
            kill(false);
            return this.exceptionHandler(new TimeoutExceededException(workerTaskTimeout));
        } catch (RunnableTaskException e) {
            taskOutput = e.getOutput();
            return this.exceptionHandler(e);
        }
    }
}
