package io.kestra.core.metrics;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.schedulers.SchedulerExecutionWithTrigger;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Singleton
@Slf4j
public class MetricRegistry {
    public final static String METRIC_WORKER_RUNNING_COUNT = "worker.running.count";
    public final static String METRIC_WORKER_QUEUED_DURATION = "worker.queued.duration";
    public final static String METRIC_WORKER_STARTED_COUNT = "worker.started.count";
    public final static String METRIC_WORKER_RETRYED_COUNT = "worker.retryed.count";
    public final static String METRIC_WORKER_TIMEOUT_COUNT = "worker.timeout.count";
    public final static String METRIC_WORKER_ENDED_COUNT = "worker.ended.count";
    public final static String METRIC_WORKER_ENDED_DURATION = "worker.ended.duration";
    public final static String METRIC_WORKER_EVALUATE_TRIGGER_DURATION = "worker.evaluate.trigger.duration";
    public final static String METRIC_WORKER_EVALUATE_TRIGGER_RUNNING_COUNT = "worker.evaluate.trigger.running.count";

    public final static String EXECUTOR_TASKRUN_NEXT_COUNT = "executor.taskrun.next.count";
    public final static String EXECUTOR_TASKRUN_ENDED_COUNT = "executor.taskrun.ended.count";
    public final static String EXECUTOR_TASKRUN_ENDED_DURATION = "executor.taskrun.ended.duration";
    public final static String EXECUTOR_WORKERTASKRESULT_COUNT = "executor.workertaskresult.count";
    public final static String EXECUTOR_EXECUTION_STARTED_COUNT = "executor.execution.started.count";
    public final static String EXECUTOR_EXECUTION_END_COUNT = "executor.execution.end.count";
    public final static String EXECUTOR_EXECUTION_DURATION = "executor.execution.duration";

    public final static String METRIC_INDEXER_REQUEST_COUNT = "indexer.request.count";
    public final static String METRIC_INDEXER_REQUEST_DURATION = "indexer.request.duration";
    public final static String METRIC_INDEXER_REQUEST_RETRY_COUNT = "indexer.request.retry.count";
    public final static String METRIC_INDEXER_SERVER_DURATION = "indexer.server.duration";
    public final static String METRIC_INDEXER_MESSAGE_FAILED_COUNT = "indexer.message.failed.count";
    public final static String METRIC_INDEXER_MESSAGE_IN_COUNT = "indexer.message.in.count";
    public final static String METRIC_INDEXER_MESSAGE_OUT_COUNT = "indexer.message.out.count";

    public final static String SCHEDULER_LOOP_COUNT = "scheduler.loop.count";
    public final static String SCHEDULER_TRIGGER_COUNT = "scheduler.trigger.count";
    public final static String SCHEDULER_TRIGGER_DELAY_DURATION = "scheduler.trigger.delay.duration";
    public final static String SCHEDULER_EVALUATE_COUNT = "scheduler.evaluate.count";
    public final static String SCHEDULER_EXECUTION_RUNNING_DURATION = "scheduler.execution.running.duration";
    public final static String SCHEDULER_EXECUTION_MISSING_DURATION = "scheduler.execution.missing.duration";

    public final static String STREAMS_STATE_COUNT = "stream.state.count";


    public final static String JDBC_QUERY_DURATION = "jdbc.query.duration";

    public final static String TAG_TASK_TYPE = "task_type";
    public final static String TAG_FLOW_ID = "flow_id";
    public final static String TAG_NAMESPACE_ID = "namespace_id";
    public final static String TAG_STATE = "state";
    public final static String TAG_ATTEMPT_COUNT = "attempt_count";
    public final static String TAG_WORKER_GROUP = "worker_group";
    public final static String TAG_TENANT_ID = "tenant_id";

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
     * @param name   Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param tags   Sequence of dimensions for breaking down the name.
     * @param <T>    The type of the number from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
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
        return (metricConfig.getPrefix() != null ? metricConfig.getPrefix() + "." : "") + name;
    }

    /**
     * Return tags for current {@link WorkerTask}.
     * We don't include current state since it will break up the values per state which make no sense.
     *
     * @param workerTask the current WorkerTask
     * @param workerGroup the worker group, optional
     * @return tags to applied to metrics
     */
    public String[] tags(WorkerTask workerTask, String workerGroup, String... tags) {
        var baseTags = ArrayUtils.addAll(
            ArrayUtils.addAll(
                this.tags(workerTask.getTask()),
                tags
            ),
            TAG_NAMESPACE_ID, workerTask.getTaskRun().getNamespace(),
            TAG_FLOW_ID, workerTask.getTaskRun().getFlowId()
        );
        baseTags = workerGroup == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_WORKER_GROUP, workerGroup);
        return workerTask.getTaskRun().getTenantId() == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_TENANT_ID, workerTask.getTaskRun().getTenantId());
    }

    /**
     * Return tags for current {@link WorkerTaskResult}
     *
     * @param workerTaskResult the current WorkerTaskResult
     * @return tags to applied to metrics
     */
    public String[] tags(WorkerTaskResult workerTaskResult, String... tags) {
        var baseTags = ArrayUtils.addAll(
            tags,
            TAG_NAMESPACE_ID, workerTaskResult.getTaskRun().getNamespace(),
            TAG_FLOW_ID, workerTaskResult.getTaskRun().getFlowId(),
            TAG_STATE, workerTaskResult.getTaskRun().getState().getCurrent().name()
        );
        return workerTaskResult.getTaskRun().getTenantId() == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_TENANT_ID, workerTaskResult.getTaskRun().getTenantId());
    }

    /**
     * Return tags for current {@link WorkerTaskResult}
     *
     * @param subflowExecutionResult the current WorkerTaskResult
     * @return tags to applied to metrics
     */
    public String[] tags(SubflowExecutionResult subflowExecutionResult, String... tags) {
        var baseTags = ArrayUtils.addAll(
            tags,
            TAG_NAMESPACE_ID, subflowExecutionResult.getParentTaskRun().getNamespace(),
            TAG_FLOW_ID, subflowExecutionResult.getParentTaskRun().getFlowId(),
            TAG_STATE, subflowExecutionResult.getParentTaskRun().getState().getCurrent().name()
        );
        return subflowExecutionResult.getParentTaskRun().getTenantId() == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_TENANT_ID, subflowExecutionResult.getParentTaskRun().getTenantId());
    }

    /**
     * Return tags for current {@link Task}
     *
     * @param task the current Task
     * @return tags to applied to metrics
     */
    public String[] tags(Task task) {
        return new String[]{
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
        var baseTags = new String[]{
            TAG_FLOW_ID, execution.getFlowId(),
            TAG_NAMESPACE_ID, execution.getNamespace(),
            TAG_STATE, execution.getState().getCurrent().name(),
        };
        return execution.getTenantId() == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_TENANT_ID, execution.getTenantId());
    }

    /**
     * Return tags for current {@link TriggerContext}
     *
     * @param triggerContext the current TriggerContext
     * @param workerGroup the worker group, optional
     * @return tags to applied to metrics
     */
    public String[] tags(TriggerContext triggerContext, String workerGroup) {
        var baseTags = new String[]{
            TAG_FLOW_ID, triggerContext.getFlowId(),
            TAG_NAMESPACE_ID, triggerContext.getNamespace()
        };
        baseTags =  workerGroup == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_WORKER_GROUP, workerGroup);
        return triggerContext.getTenantId() == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_TENANT_ID, triggerContext.getTenantId());
    }

    /**
     * Return tags for current {@link TriggerContext}
     *
     * @param triggerContext the current TriggerContext
     * @return tags to applied to metrics
     */
    public String[] tags(TriggerContext triggerContext) {
        return tags(triggerContext, null);
    }

    /**
     * Return tags for current {@link SchedulerExecutionWithTrigger}.
     *
     * @param schedulerExecutionWithTrigger the current SchedulerExecutionWithTrigger
     * @return tags to applied to metrics
     */
    public String[] tags(SchedulerExecutionWithTrigger schedulerExecutionWithTrigger, String... tags) {
        return ArrayUtils.addAll(
            this.tags(schedulerExecutionWithTrigger.getExecution()),
            tags
        );
    }


    /**
     * Return globals tags
     *
     * @return tags to applied to metrics
     */
    public Tags tags(String... tags) {
        return Tags.of(tags);
    }

    /**
     * Attach a {@link MeterBinder} to current registry
     *
     * @param meterBinder the {@link MeterBinder} to bind to current registry
     */
    public void bind(MeterBinder meterBinder) {
        try {
            meterBinder.bindTo(this.meterRegistry);
        } catch (Exception e) {
            log.warn("Error on metrics", e);
        }
    }
}

