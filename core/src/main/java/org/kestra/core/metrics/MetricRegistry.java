package org.kestra.core.metrics;


import io.micrometer.core.instrument.*;
import io.micrometer.core.lang.Nullable;
import org.apache.commons.lang3.ArrayUtils;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.WorkerTask;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MetricRegistry {
    public final static String TASK_ID = "task_id";
    public final static String TASK_TYPE = "task_type";
    public final static String FLOW_ID = "flow_id";
    public final static String NAMESPACE_ID = "namespace_id";
    public final static String TASKRUN_VALUE = "taskrun_id";
    public final static String STATE = "state";

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
        return this.meterRegistry.<T>gauge(metricName(name), Tags.of(tags), number);
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
     * Return tags for current {@link WorkerTask}
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
            FLOW_ID, workerTask.getTaskRun().getFlowId(),
            STATE, workerTask.getTaskRun().getState().getCurrent().name()
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
            TASK_ID, task.getId(),
            TASK_TYPE, task.getType(),
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
            FLOW_ID, execution.getFlowId(),
            NAMESPACE_ID, execution.getNamespace(),
            STATE, execution.getState().getCurrent().name(),
        };
    }
}

