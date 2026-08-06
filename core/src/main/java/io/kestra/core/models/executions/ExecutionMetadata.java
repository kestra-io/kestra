package io.kestra.core.models.executions;

import java.time.Instant;
import java.util.List;

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
     * Cumulative count of taskruns removed during loop iterations (e.g. by {@code retryWaitFor}).
     * Used by {@link io.kestra.core.models.executions.statistics.ExecutionStatistic} to report
     * the total number of taskruns that actually ran, not just those present at termination.
     */
    @Builder.Default
    long accumulatedTaskRunCount = 0;

    /**
     * Cumulative duration (ms) of taskruns removed during loop iterations.
     */
    @Builder.Default
    long accumulatedTaskRunDurationSumMs = 0;

    public ExecutionMetadata nextAttempt() {
        return this.toBuilder()
            .attemptNumber(this.attemptNumber + 1)
            .build();
    }

    /**
     * Accumulates statistics from taskruns about to be removed during a loop iteration reset.
     *
     * @param removedTaskRuns the taskruns being removed.
     * @return a new metadata instance with updated accumulated stats.
     */
    public ExecutionMetadata accumulateRemovedTaskRuns(List<TaskRun> removedTaskRuns) {
        long count = removedTaskRuns.size();
        long durationSum = removedTaskRuns.stream()
            .mapToLong(tr -> tr.getState().getDurationOrComputeIt().toMillis())
            .sum();
        return this.toBuilder()
            .accumulatedTaskRunCount(this.accumulatedTaskRunCount + count)
            .accumulatedTaskRunDurationSumMs(this.accumulatedTaskRunDurationSumMs + durationSum)
            .build();
    }
}
