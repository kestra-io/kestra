package io.kestra.core.models.tasks;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.utils.IdUtils;
import jakarta.annotation.Nullable;

/**
 * A lightweight task-run-statistic row, emitted asynchronously on task completion
 * and consumed by the indexer.
 * <p>
 * Two flavors share this same shape:
 * <ul>
 *   <li><b>Raw row</b>: emitted directly for a terminated task run, {@code count = 1},
 *   {@code taskRunId} set. Its {@link #uid()} is the task run id for idempotent ingestion.</li>
 *   <li><b>Aggregate row</b>: written by the periodic compaction job by merging raw rows of a
 *   closed bucket, {@code count = N}, {@code taskRunId} is {@code null}. Its {@link #uid()}
 *   is a deterministic hash of the bucket key (including {@code taskId}).</li>
 * </ul>
 */
public record TaskRunStatistic(
    String tenantId,
    String namespace,
    String flowId,
    String taskId,
    Instant date,
    State.Type state,
    long count,
    long durationSumMs,
    long durationMinMs,
    long durationMaxMs,
    @Nullable String executionId,
    @Nullable String taskRunId
) implements HasUID, DispatchEvent {

    /**
     * Builds a raw statistic row ({@code count = 1}) for a terminated task run.
     *
     * @param taskRun the terminated task run.
     * @param bucket the minute bucket this task run is recorded under (the executor or worker
     *        buckets by the task run's end date, falling back to the current time if not yet set).
     */
    public TaskRunStatistic(TaskRun taskRun, Instant bucket) {
        this(
            taskRun.getTenantId(),
            taskRun.getNamespace(),
            taskRun.getFlowId(),
            taskRun.getTaskId(),
            bucket,
            taskRun.getState().getCurrent(),
            1,
            taskRun.getState().getDurationOrComputeIt().toMillis(),
            taskRun.getState().getDurationOrComputeIt().toMillis(),
            taskRun.getState().getDurationOrComputeIt().toMillis(),
            taskRun.getExecutionId(),
            taskRun.getId()
        );
    }

    /** {@inheritDoc} **/
    @Override
    public String uid() {
        return taskRunId != null
            ? taskRunId
            : IdUtils.from(IdUtils.fromParts(tenantId, namespace, flowId, taskId, date.toString(), state.name()));
    }

    /** {@inheritDoc} **/
    @Override
    public String key() {
        return uid();
    }
}
