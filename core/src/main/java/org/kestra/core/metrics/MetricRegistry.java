package org.kestra.core.metrics;


import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.Nullable;
import org.apache.commons.lang3.ArrayUtils;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MetricRegistry {
    public final static String METRIC_WORKER_RUNNING_COUNT = "worker.running.count";
    public final static String METRIC_WORKER_STARTED_COUNT = "worker.started.count";
    public final static String METRIC_WORKER_RETRYED_COUNT = "worker.retryed.count";
    public final static String METRIC_WORKER_ENDED_COUNT = "worker.ended.count";

    public final static String KESTRA_EXECUTOR_TASKRUN_NEXT_COUNT = "executor.taskrun.next.count";
    public final static String KESTRA_EXECUTOR_WORKERTASKRESULT_COUNT = "executor.workertaskresult.count";
    public final static String KESTRA_EXECUTOR_EXECUTION_STARTED_COUNT = "executor.execution.started.count";
    public final static String KESTRA_EXECUTOR_EXECUTION_END_COUNT = "executor.execution.end.count";
    public final static String METRIC_EXECUTOR_EXECUTION_DURATION = "executor.execution.duration";

    public final static String METRIC_INDEXER_COUNT = "indexer.count";
    public final static String METRIC_INDEXER_DURATION = "indexer.duration";

    public final static String TAG_TASK_ID = "task_id";
    public final static String TAG_TASK_TYPE = "task_type";
    public final static String TAG_FLOW_ID = "flow_id";
    public final static String TAG_NAMESPACE_ID = "namespace_id";
    public final static String TAG_STATE = "state";
    public final static String TAG_ATTEMPT_COUNT = "attempt_count";
    public final static String TAG_VALUE = "value";

    @Inject
    private MeterRegistry meterRegistry;

    @Inject
    private MetricConfig metricConfig;

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing counter.
     */
    public Counter counter(String name, String... tags) {
        return this.meterRegistry.counter(metricName(name), tags);
    }

    /**
     * Register a gauge that reports the value of the {@link Number}.
     *
     * @param name Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param <T> The type of the number from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public <T extends Number> T gauge(String name, T number, String... tags) {
        return this.meterRegistry.gauge(metricName(name), Tags.of(tags), number);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing timer.
     */
    public Timer timer(String name, String... tags) {
        return this.meterRegistry.timer(metricName(name), tags);
    }

    /**
     * Measures the distribution of samples.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing distribution summary.
     */
    public DistributionSummary summary(String name, String... tags) {
        return this.meterRegistry.summary(metricName(name), tags);
    }

    /**
     * Return the tag with prefix from configuration
     *
     * @param name the metric to prefix
     * @return The complete metric with prefix
     */
    private String metricName(String name) {
        return metricConfig.getPrefix() + "." + name;
    }

    /**
     * Return tags for current {@link WorkerTask}.
     * We don't include current state since it will breakup the values per state and it's make no sense.
     *
     * @param workerTask the current WorkerTask
     * @return tags to applied to metrics
     */
    public String[] tags(WorkerTask workerTask, String... tags) {
        return ArrayUtils.addAll(
            ArrayUtils.addAll(
                this.tags(workerTask.getTask()),
                tags
            ),
            TAG_NAMESPACE_ID, workerTask.getTaskRun().getNamespace(),
            TAG_FLOW_ID, workerTask.getTaskRun().getFlowId()
        );
    }

    /**
     * Return tags for current {@link WorkerTaskResult}
     *
     * @param workerTaskResult the current WorkerTaskResult
     * @return tags to applied to metrics
     */
    public String[] tags(WorkerTaskResult workerTaskResult, String... tags) {
        return ArrayUtils.addAll(
            ArrayUtils.addAll(
                this.tags(workerTaskResult.getTask()),
                tags
            ),
            TAG_NAMESPACE_ID, workerTaskResult.getTaskRun().getNamespace(),
            TAG_FLOW_ID, workerTaskResult.getTaskRun().getFlowId(),
            TAG_STATE, workerTaskResult.getTaskRun().getState().getCurrent().name()
        );
    }

    /**
     * Return tags for current {@link Task}
     *
     * @param task the current Task
     * @return tags to applied to metrics
     */
    public String[] tags(Task task) {
        return new String[]{
            TAG_TASK_ID, task.getId(),
            TAG_TASK_TYPE, task.getType(),
        };
    }

    /**
     * Return tags for current {@link Execution}
     *
     * @param execution the current Execution
     * @return tags to applied to metrics
     */
    public String[] tags(Execution execution) {
        return new String[]{
            TAG_FLOW_ID, execution.getFlowId(),
            TAG_NAMESPACE_ID, execution.getNamespace(),
            TAG_STATE, execution.getState().getCurrent().name(),
        };
    }

    /**
     * Attach a {@link MeterBinder} to current registry
     *
     * @param meterBinder the {@link MeterBinder} to bind to current registry
     */
    public void bind(MeterBinder meterBinder) {
        meterBinder.bindTo(this.meterRegistry);
    }
}

