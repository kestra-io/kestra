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
     * The uids of the concurrency scopes the current attempt of this execution claimed a slot in when it was admitted.
     * The release decrements exactly these scopes, so removing or changing a namespace/tenant
     * limit while the execution runs cannot leak the counter of a scope it was admitted under.
     * <ul>
     * <li>null: the concurrency gate hasn't evaluated this attempt yet (or the execution predates the stamp), the
     * release then decides from the state history of the current attempt;</li>
     * <li>empty: the attempt holds no slot, as it was queued, rejected, or bypassed the gate.</li>
     * </ul>
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

    /**
     * Returns the metadata of a new attempt, as created by a restart, a replay, or a task run state change.
     * The claim of the previous attempt is not carried over: the concurrency gate evaluates the new attempt again and
     * stamps its own claim.
     */
    public ExecutionMetadata nextAttempt() {
        return this.toBuilder()
            .attemptNumber(this.attemptNumber + 1)
            .concurrencyScopes(null)
            .build();
    }

    /**
     * Returns {@link #executionDepth}, defaulting to 0 for a root execution or one predating this field.
     */
    public int executionDepthOrZero() {
        return this.executionDepth == null ? 0 : this.executionDepth;
    }
}
