package io.kestra.core.models.executions.statistics;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;

import jakarta.annotation.Nullable;

/**
 * A lightweight execution-statistic row, emitted asynchronously by the executor on execution
 * termination and consumed by the indexer.
 * <p>
 * Two flavors share this same shape so that reading a range of buckets is a uniform aggregation
 * regardless of whether the periodic compaction job has already run on a given bucket:
 * <ul>
 * <li><b>Raw row</b>: emitted directly for a terminated execution, {@code count = 1},
 * {@code executionId} set. Its {@link #uid()} is the execution id, which makes ingestion
 * idempotent under at-least-once queue delivery: a redelivered record overwrites the same row
 * instead of being double-counted.</li>
 * <li><b>Aggregate row</b>: written by the periodic compaction job by merging the raw rows of a
 * closed minute bucket, {@code count = N}, {@code executionId} is {@code null}. Its
 * {@link #uid()} is a deterministic hash of the bucket key so re-compaction overwrites the same
 * row instead of creating duplicates.</li>
 * </ul>
 */
public record ExecutionStatistic(
    String tenantId,
    String namespace,
    String flowId,
    Instant date,
    State.Type state,
    long count,
    long durationSumMs,
    long durationMinMs,
    long durationMaxMs,
    long taskRunCount,
    long taskRunsDurationSumMs,
    // Nullable (unlike durationMinMs/MaxMs, which always have a value): an execution can have zero
    // task runs, in which case there is no task-run duration to report, not a zero one.
    @Nullable Long taskRunsDurationMinMs,
    @Nullable Long taskRunsDurationMaxMs,
    @Nullable String executionId) implements HasUID, DispatchEvent {
    /**
     * Builds a raw statistic row ({@code count = 1}) for a terminated execution.
     *
     * @param execution the terminated execution.
     * @param bucket the minute bucket this execution is recorded under (the executor buckets by
     *        the execution's end date, falling back to the current time if not yet set).
     */
    public ExecutionStatistic(Execution execution, Instant bucket) {
        this(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            bucket,
            execution.getState().getCurrent(),
            1,
            execution.getState().getDurationOrComputeIt().toMillis(),
            execution.getState().getDurationOrComputeIt().toMillis(),
            execution.getState().getDurationOrComputeIt().toMillis(),
            ListUtils.emptyOnNull(execution.getTaskRunList()).size(),
            ListUtils.emptyOnNull(execution.getTaskRunList()).stream()
                .mapToLong(taskRun -> taskRun.getState().getDurationOrComputeIt().toMillis())
                .sum(),
            ListUtils.emptyOnNull(execution.getTaskRunList()).stream()
                .mapToLong(taskRun -> taskRun.getState().getDurationOrComputeIt().toMillis())
                .min()
                .stream().boxed().findFirst().orElse(null),
            ListUtils.emptyOnNull(execution.getTaskRunList()).stream()
                .mapToLong(taskRun -> taskRun.getState().getDurationOrComputeIt().toMillis())
                .max()
                .stream().boxed().findFirst().orElse(null),
            execution.getId()
        );
    }

    /** {@inheritDoc} **/
    @Override
    public String uid() {
        return executionId != null
            ? executionId
            : IdUtils.from(IdUtils.fromParts(tenantId, namespace, flowId, date.toString(), state.name()));
    }

    /** {@inheritDoc} **/
    @Override
    public String key() {
        return uid();
    }
}
