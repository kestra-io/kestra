package io.kestra.worker.processors.internals;

import java.time.Duration;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;

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
            State.Type timeoutState = callWithTimeout(
                workerTaskTimeout,
                () -> taskOutput = task.run(runContext),
                () -> metricRegistry
                    .counter(
                        MetricRegistry.METRIC_WORKER_TIMEOUT_COUNT,
                        MetricRegistry.METRIC_WORKER_TIMEOUT_COUNT_DESCRIPTION,
                        // The timeout policy has no retries, so the attempt count is always 1.
                        metricRegistry.tags(this.workerTask, this.workerGroup, MetricRegistry.TAG_ATTEMPT_COUNT, String.valueOf(1))
                    )
                    .increment()
            );
            if (timeoutState != null) {
                return timeoutState;
            }

            if (taskOutput != null && taskOutput.finalState().isPresent()) {
                return taskOutput.finalState().get();
            }
            return SUCCESS;
        } catch (RunnableTaskException e) {
            taskOutput = e.getOutput();
            return this.exceptionHandler(e);
        }
    }
}
