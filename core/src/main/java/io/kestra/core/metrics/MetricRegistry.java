package io.kestra.core.metrics;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.search.Search;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;

@Singleton
@Slf4j
public class MetricRegistry {
    public static final String METRIC_WORKER_JOB_PENDING_COUNT = "worker.job.pending";
    public static final String METRIC_WORKER_JOB_PENDING_COUNT_DESCRIPTION = "The number of jobs (tasks or triggers) pending to be run by the Worker";
    public static final String METRIC_WORKER_JOB_RUNNING_COUNT = "worker.job.running";
    public static final String METRIC_WORKER_JOB_RUNNING_COUNT_DESCRIPTION = "The number of jobs (tasks or triggers) currently running inside the Worker";
    public static final String METRIC_WORKER_JOB_THREAD_COUNT = "worker.job.thread";
    public static final String METRIC_WORKER_JOB_THREAD_COUNT_DESCRIPTION = "The number of worker threads";
    public static final String METRIC_WORKER_RUNNING_COUNT = "worker.running.count";
    public static final String METRIC_WORKER_RUNNING_COUNT_DESCRIPTION = "The number of tasks currently running inside the Worker";
    public static final String METRIC_WORKER_QUEUED_DURATION = "worker.queued.duration";
    public static final String METRIC_WORKER_QUEUED_DURATION_DESCRIPTION = "Task queued duration inside the Worker";
    public static final String METRIC_WORKER_STARTED_COUNT = "worker.started.count";
    public static final String METRIC_WORKER_STARTED_COUNT_DESCRIPTION = "The total number of tasks started by the Worker";
    public static final String METRIC_WORKER_TIMEOUT_COUNT = "worker.timeout.count";
    public static final String METRIC_WORKER_TIMEOUT_COUNT_DESCRIPTION = "The total number of tasks that timeout inside the Worker";
    public static final String METRIC_WORKER_ENDED_COUNT = "worker.ended.count";
    public static final String METRIC_WORKER_ENDED_COUNT_DESCRIPTION = "The total number of tasks ended by the Worker";
    public static final String METRIC_WORKER_ENDED_DURATION = "worker.ended.duration";
    public static final String METRIC_WORKER_ENDED_DURATION_DESCRIPTION = "Task run duration inside the Worker";
    public static final String METRIC_WORKER_TRIGGER_DURATION = "worker.trigger.duration";
    public static final String METRIC_WORKER_TRIGGER_DURATION_DESCRIPTION = "Trigger evaluation duration inside the Worker";
    public static final String METRIC_WORKER_TRIGGER_RUNNING_COUNT = "worker.trigger.running.count";
    public static final String METRIC_WORKER_TRIGGER_RUNNING_COUNT_DESCRIPTION = "The number of triggers currently evaluating inside the Worker";
    public static final String METRIC_WORKER_TRIGGER_STARTED_COUNT = "worker.trigger.started.count";
    public static final String METRIC_WORKER_TRIGGER_STARTED_COUNT_DESCRIPTION = "The total number of trigger evaluations started by the Worker";
    public static final String METRIC_WORKER_TRIGGER_ENDED_COUNT = "worker.trigger.ended.count";
    public static final String METRIC_WORKER_TRIGGER_ENDED_COUNT_DESCRIPTION = "The total number of trigger evaluations ended by the Worker";
    public static final String METRIC_WORKER_TRIGGER_ERROR_COUNT = "worker.trigger.error.count";
    public static final String METRIC_WORKER_TRIGGER_ERROR_COUNT_DESCRIPTION = "The total number of trigger evaluations that failed inside the Worker";
    public static final String METRIC_WORKER_TRIGGER_EXECUTION_COUNT = "worker.trigger.execution.count";
    public static final String METRIC_WORKER_TRIGGER_EXECUTION_COUNT_DESCRIPTION = "The total number of triggers evaluated by the Worker";
    public static final String METRIC_WORKER_KILLED_COUNT = "worker.killed.count";
    public static final String METRIC_WORKER_KILLED_COUNT_DESCRIPTION = "The total number of executions killed events received the Executor";

    public static final String METRIC_EXECUTOR_THREAD_COUNT = "executor.thread.count";
    public static final String METRIC_EXECUTOR_THREAD_COUNT_DESCRIPTION = "The number of executor threads";
    public static final String METRIC_EXECUTOR_TASKRUN_CREATED_COUNT = "executor.taskrun.created.count";
    public static final String METRIC_EXECUTOR_TASKRUN_CREATED_COUNT_DESCRIPTION = "The total number of tasks created by the Executor";
    public static final String METRIC_EXECUTOR_TASKRUN_ENDED_COUNT = "executor.taskrun.ended.count";
    public static final String METRIC_EXECUTOR_TASKRUN_ENDED_COUNT_DESCRIPTION = "The total number of tasks ended by the Executor";
    public static final String METRIC_EXECUTOR_TASKRUN_ENDED_DURATION = "executor.taskrun.ended.duration";
    public static final String METRIC_EXECUTOR_TASKRUN_ENDED_DURATION_DESCRIPTION = "Task duration inside the Executor";
    public static final String METRIC_EXECUTOR_FLOWABLE_EXECUTION_COUNT = "executor.flowable.execution.count";
    public static final String METRIC_EXECUTOR_FLOWABLE_EXECUTION_COUNT_DESCRIPTION = "The total number of flowable tasks executed by the Executor";
    public static final String METRIC_EXECUTOR_EXECUTION_STARTED_COUNT = "executor.execution.started.count";
    public static final String METRIC_EXECUTOR_EXECUTION_STARTED_COUNT_DESCRIPTION = "The total number of executions started by the Executor";
    public static final String METRIC_EXECUTOR_EXECUTION_END_COUNT = "executor.execution.end.count";
    public static final String METRIC_EXECUTOR_EXECUTION_END_COUNT_DESCRIPTION = "The total number of executions ended by the Executor";
    public static final String METRIC_EXECUTOR_EXECUTION_DURATION = "executor.execution.duration";
    public static final String METRIC_EXECUTOR_EXECUTION_DURATION_DESCRIPTION = "Execution duration inside the Executor";
    public static final String METRIC_EXECUTOR_EXECUTION_MESSAGE_PROCESS_DURATION = "executor.execution.message.process";
    public static final String METRIC_EXECUTOR_EXECUTION_MESSAGE_PROCESS_DURATION_DESCRIPTION = "Duration of a single execution message processed by the Executor";
    public static final String METRIC_EXECUTOR_KILLED_COUNT = "executor.killed.count";
    public static final String METRIC_EXECUTOR_KILLED_COUNT_DESCRIPTION = "The total number of executions killed events received the Executor";
    public static final String METRIC_EXECUTOR_SLA_EXPIRED_COUNT = "executor.sla.expired.count";
    public static final String METRIC_EXECUTOR_SLA_EXPIRED_COUNT_DESCRIPTION = "The total number of expired SLA (i.e. executions with SLA of type MAX_DURATION that took longer than the SLA) evaluated by the Executor";
    public static final String METRIC_EXECUTOR_SLA_VIOLATION_COUNT = "executor.sla.violation.count";
    public static final String METRIC_EXECUTOR_SLA_VIOLATION_COUNT_DESCRIPTION = "The total number of expired SLA (i.e. executions with SLA of type MAX_DURATION that took longer than the SLA) evaluated by the Executor";
    public static final String METRIC_EXECUTOR_EXECUTION_DELAY_CREATED_COUNT = "executor.execution.delay.created.count";
    public static final String METRIC_EXECUTOR_EXECUTION_DELAY_CREATED_COUNT_DESCRIPTION = "The total number of execution delays created by the Executor";
    public static final String METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT = "executor.execution.delay.ended.count";
    public static final String METRIC_EXECUTOR_EXECUTION_DELAY_ENDED_COUNT_DESCRIPTION = "The total number of execution delays ended (resumed) by the Executor";
    public static final String METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT = "executor.worker.job.resubmit.count";
    public static final String METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT_DESCRIPTION = "The total number of worker jobs resubmitted to the Worker by the Executor";
    public static final String METRIC_EXECUTOR_EXECUTION_QUEUED_COUNT = "executor.execution.queued.count";
    public static final String METRIC_EXECUTOR_EXECUTION_QUEUED_COUNT_DESCRIPTION = "The total number of executions queued by the Executor";
    public static final String METRIC_EXECUTOR_EXECUTION_POPPED_COUNT = "executor.execution.popped.count";
    public static final String METRIC_EXECUTOR_EXECUTION_POPPED_COUNT_DESCRIPTION = "The total number of executions popped by the Executor";

    public static final String METRIC_INDEXER_REQUEST_COUNT = "indexer.request.count";
    public static final String METRIC_INDEXER_REQUEST_COUNT_DESCRIPTION = "Total number of batches of records received by the Indexer";
    public static final String METRIC_INDEXER_REQUEST_DURATION = "indexer.request.duration";
    public static final String METRIC_INDEXER_REQUEST_DURATION_DESCRIPTION = "Batch of records duration inside the Indexer";
    public static final String METRIC_INDEXER_REQUEST_RETRY_COUNT = "indexer.request.retry.count";
    public static final String METRIC_INDEXER_REQUEST_RETRY_COUNT_DESCRIPTION = "Total number of batches of records retried by the Indexer";
    public static final String METRIC_INDEXER_SERVER_DURATION = "indexer.server.duration";
    public static final String METRIC_INDEXER_SERVER_DURATION_DESCRIPTION = "Batch of records indexation duration";
    public static final String METRIC_INDEXER_MESSAGE_FAILED_COUNT = "indexer.message.failed.count";
    public static final String METRIC_INDEXER_MESSAGE_FAILED_COUNT_DESCRIPTION = "Total number of records which failed to be indexed by the Indexer";
    public static final String METRIC_INDEXER_MESSAGE_IN_COUNT = "indexer.message.in.count";
    public static final String METRIC_INDEXER_MESSAGE_IN_COUNT_DESCRIPTION = "Total number of records received by the Indexer";
    public static final String METRIC_INDEXER_MESSAGE_OUT_COUNT = "indexer.message.out.count";
    public static final String METRIC_INDEXER_MESSAGE_OUT_COUNT_DESCRIPTION = "Total number of records indexed by the Indexer";

    public static final String METRIC_SCHEDULER_LOOP_COUNT = "scheduler.loop.count";
    public static final String METRIC_SCHEDULER_LOOP_COUNT_DESCRIPTION = "Total number of evaluation loops executed by the Scheduler";
    public static final String METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION = "scheduler.trigger.evaluation.duration";
    public static final String METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION_DESCRIPTION = "Trigger evaluation duration for trigger executed inside the Scheduler (Schedulable triggers)";
    public static final String METRIC_SCHEDULER_TRIGGER_COUNT = "scheduler.trigger.count";
    public static final String METRIC_SCHEDULER_TRIGGER_COUNT_DESCRIPTION = "Total number of executions triggered by the Scheduler";
    public static final String METRIC_SCHEDULER_TRIGGER_DELAY_DURATION = "scheduler.trigger.delay.duration";
    public static final String METRIC_SCHEDULER_TRIGGER_DELAY_DURATION_DESCRIPTION = "Trigger delay duration inside the Scheduler";
    public static final String METRIC_SCHEDULER_EVALUATE_COUNT = "scheduler.evaluate.count";
    public static final String METRIC_SCHEDULER_EVALUATE_COUNT_DESCRIPTION = "Total number of triggers evaluated by the Scheduler";
    public static final String METRIC_SCHEDULER_EXECUTION_LOCK_DURATION = "scheduler.execution.lock.duration";
    public static final String METRIC_SCHEDULER_EXECUTION_LOCK_DURATION_DESCRIPTION = "Trigger lock duration waiting for an execution to be terminated";
    public static final String METRIC_SCHEDULER_EXECUTION_MISSING_DURATION = "scheduler.execution.missing.duration";
    public static final String METRIC_SCHEDULER_EXECUTION_MISSING_DURATION_DESCRIPTION = "Missing execution duration inside the Scheduler. A missing execution is an execution that was triggered by the Scheduler but not yet started by the Executor";
    public static final String METRIC_SCHEDULER_EVALUATION_LOOP_DURATION = "scheduler.evaluation.loop.duration";
    public static final String METRIC_SCHEDULER_EVALUATION_LOOP_DURATION_DESCRIPTION = "Trigger evaluation loop duration inside the Scheduler";

    public static final String METRIC_STREAMS_STATE_COUNT = "stream.state.count";
    public static final String METRIC_STREAMS_STATE_COUNT_DESCRIPTION = "Number of Kafka Stream applications by state";

    public static final String METRIC_JDBC_QUERY_DURATION = "jdbc.query.duration";
    public static final String METRIC_JDBC_QUERY_DURATION_DESCRIPTION = "Duration of database queries";

    public static final String METRIC_QUEUE_BIG_MESSAGE_COUNT = "queue.big_message.count";
    public static final String METRIC_QUEUE_BIG_MESSAGE_COUNT_DESCRIPTION = "Total number of big messages";
    public static final String METRIC_QUEUE_PRODUCE_COUNT = "queue.produce.count";
    public static final String METRIC_QUEUE_PRODUCE_COUNT_DESCRIPTION = "Total number of produced messages";
    public static final String METRIC_QUEUE_RECEIVE_DURATION = "queue.receive.duration";
    public static final String METRIC_QUEUE_RECEIVE_DURATION_DESCRIPTION = "Queue duration to receive and consume a batch of messages";
    public static final String METRIC_QUEUE_POLL_SIZE = "queue.poll.size";
    public static final String METRIC_QUEUE_POLL_SIZE_DESCRIPTION = "Size of a poll to the queue (message batch size)";

    public static final String TAG_TASK_TYPE = "task_type";
    public static final String TAG_TRIGGER_TYPE = "trigger_type";
    public static final String TAG_FLOW_ID = "flow_id";
    public static final String TAG_NAMESPACE_ID = "namespace_id";
    public static final String TAG_STATE = "state";
    public static final String TAG_ATTEMPT_COUNT = "attempt_count";
    public static final String TAG_WORKER_GROUP = "worker_group";
    public static final String TAG_TENANT_ID = "tenant_id";
    public static final String TAG_CLASS_NAME = "class_name";
    public static final String TAG_EXECUTION_KILLED_TYPE = "execution_killed_type";
    public static final String TAG_QUEUE_CONSUMER = "consumer";
    public static final String TAG_QUEUE_CONSUMER_GROUP = "consumer_group";
    public static final String TAG_QUEUE_TYPE = "queue_type";

    @Inject
    private MeterRegistry meterRegistry;

    @Inject
    private MetricConfig metricConfig;

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param description The metric description
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing counter.
     */
    public Counter counter(String name, String description, String... tags) {
        return Counter.builder(metricName(name))
            .description(description)
            .tags(tags)
            .register(this.meterRegistry);
    }

    /**
     * Register a gauge that reports the value of the {@link Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param description The metric description
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param tags   Sequence of dimensions for breaking down the name.
     * @param <T>    The type of the number from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    public <T extends Number> T gauge(String name, String description, T number, String... tags) {
        Gauge.builder(metricName(name), () -> number)
            .description(description)
            .tags(tags)
            .register(this.meterRegistry);
        return number;
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param description The metric description
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing timer.
     */
    public Timer timer(String name, String description, String... tags) {
        return Timer.builder(metricName(name))
            .description(description)
            .tags(tags)
            .register(this.meterRegistry);
    }

    /**
     * Measures the distribution of samples.
     *
     * @param name The base metric name
     * @param description The metric description
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing distribution summary.
     */
    public DistributionSummary summary(String name, String description, String... tags) {
        return DistributionSummary.builder(metricName(name))
            .description(description)
            .tags(tags)
            .register(this.meterRegistry);
    }

    /**
     * Search for an existing Meter in the meter registry
     * @param name The base metric name
     */
    public Search find(String name) {
        return this.meterRegistry.find(metricName(name));
    }

    /**
     * Search for an existing Counter in the meter registry
     * @param name The base metric name
     */
    public Counter findCounter(String name) {
        return this.meterRegistry.find(metricName(name)).counter();
    }

    /**
     * Search for an existing Gauge in the meter registry
     * @param name The base metric name
     */
    public Gauge findGauge(String name) {
        return this.meterRegistry.find(metricName(name)).gauge();
    }

    /**
     * Search for an existing Timer in the meter registry
     * @param name The base metric name
     */
    public Timer findTimer(String name) {
        return this.meterRegistry.find(metricName(name)).timer();
    }

    /**
     * Search for an existing DistributionSummary in the meter registry
     * @param name The base metric name
     */
    public DistributionSummary findDistributionSummary(String name) {
        return this.meterRegistry.find(metricName(name)).summary();
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
     * @return tags to apply to metrics
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
     * Return tags for current {@link WorkerTask}.
     * We don't include current state since it will break up the values per state which make no sense.
     *
     * @param workerTrigger the current WorkerTask
     * @param workerGroup the worker group, optional
     * @return tags to apply to metrics
     */
    public String[] tags(WorkerTrigger workerTrigger, String workerGroup, String... tags) {
        var baseTags = ArrayUtils.addAll(
            ArrayUtils.addAll(
                this.tags(workerTrigger.getTrigger()),
                tags
            ),
            TAG_NAMESPACE_ID, workerTrigger.getTriggerContext().getNamespace(),
            TAG_FLOW_ID, workerTrigger.getTriggerContext().getFlowId()
        );
        baseTags = workerGroup == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_WORKER_GROUP, workerGroup);
        return workerTrigger.getTriggerContext().getTenantId() == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_TENANT_ID, workerTrigger.getTriggerContext().getTenantId());
    }


    /**
     * Return tags for current {@link WorkerTaskResult}
     *
     * @param workerTaskResult the current WorkerTaskResult
     * @return tags to apply to metrics
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
     * @return tags to apply to metrics
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
     * @return tags to apply to metrics
     */
    public String[] tags(Task task) {
        return new String[]{
            TAG_TASK_TYPE, task.getType(),
        };
    }

    /**
     * Return tags for current {@link AbstractTrigger}
     *
     * @param trigger the current Trigger
     * @return tags to apply to metrics
     */
    public String[] tags(AbstractTrigger trigger) {
        return new String[]{
            TAG_TRIGGER_TYPE, trigger.getType(),
        };
    }

    /**
     * Return tags for current {@link Execution}
     *
     * @param execution the current Execution
     * @return tags to apply to metrics
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
     * @return tags to apply to metrics
     */
    public String[] tags(TriggerContext triggerContext) {
        var baseTags = new String[]{
            TAG_FLOW_ID, triggerContext.getFlowId(),
            TAG_NAMESPACE_ID, triggerContext.getNamespace()
        };
        return triggerContext.getTenantId() == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_TENANT_ID, triggerContext.getTenantId());
    }

    /**
     * Return tags for current {@link ExecutionKilled}
     *
     * @param executionKilled the current Trigger
     * @return tags to apply to metrics
     */
    public String[] tags(ExecutionKilled executionKilled) {
        var baseTags = new String[]{
            TAG_EXECUTION_KILLED_TYPE, executionKilled.getType(),
        };
        return executionKilled.getTenantId() == null ? baseTags : ArrayUtils.addAll(baseTags, TAG_TENANT_ID, executionKilled.getTenantId());
    }


    /**
     * Return globals tags
     *
     * @return tags to apply to metrics
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

