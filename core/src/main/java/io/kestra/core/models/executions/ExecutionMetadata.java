package io.kestra.core.models.executions;

import java.time.Instant;
import java.util.List;

import io.kestra.core.models.executions.statistics.TaskRunStatistic;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.With;

@Builder(toBuilder = true)
@Setter
@Getter
public class ExecutionMetadata {
    @Builder.Default
    @With
    Integer attemptNumber = 1;

    @NotNull
    Instant originalCreatedDate;

    /**
     * The uids of the concurrency scopes this execution claimed a slot in when it was admitted.
     * The release decrements exactly these scopes, so removing or changing a namespace/tenant
     * limit while the execution runs cannot leak the counter of a scope it was admitted under.
     * Null when the execution never claimed a slot (or predates the scoped limits).
     */
    @With
    List<String> concurrencyScopes;

    /**
     * The number of Subflow/Flow-trigger hops between this execution and the root execution that
     * started the chain, incremented by one at each hop.
     * Null for a root execution which is treated as depth 0.
     */
    @With
    Integer executionDepth;

    /**
     * Task runs that ran as part of this execution but are no longer reachable from its
     * {@code taskRunList} by the time it terminates: a LoopUntil task's discarded iterations,
     * and Loop sub-executions.
     */
    @With
    TaskRunStatistic taskRunStatistic;

    /**
     * Returns a copy with {@code other} folded into {@link #taskRunStatistic}. A no-op (returns
     * {@code this}) when {@code other} is null or empty, so an execution that never accumulates
     * anything keeps {@link #taskRunStatistic} null rather than an empty instance.
     */
    public ExecutionMetadata withTaskRunStatisticPlus(TaskRunStatistic other) {
        if (other == null || other.count() == 0) {
            return this;
        }

        return this.withTaskRunStatistic(this.taskRunStatistic == null ? other : this.taskRunStatistic.plus(other));
    }

    public ExecutionMetadata nextAttempt() {
        return this.toBuilder()
            .attemptNumber(this.attemptNumber + 1)
            .build();
    }

    /**
     * Returns {@link #executionDepth}, defaulting to 0 for a root execution or one predating this field.
     */
    public int executionDepthOrZero() {
        return this.executionDepth == null ? 0 : this.executionDepth;
    }
}
