package io.kestra.core.models.executions.statistics;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.utils.ListUtils;

import io.kestra.core.utils.MapUtils;
import jakarta.annotation.Nullable;

/**
 * A count/duration accumulator for task runs.
 * <p>
 * Accumulated on {@link io.kestra.core.models.executions.ExecutionMetadata} so that
 * {@link ExecutionStatistic} can fold it on top of the execution's own {@code taskRunList} without
 * losing task runs that are no longer reachable from the execution itself (for ex for Loop or LoopUntil tasks).
 */
public record TaskRunStatistic(long count, long durationSumMs, @Nullable Long durationMinMs, @Nullable Long durationMaxMs) {
    private static final TaskRunStatistic EMPTY = new TaskRunStatistic(0, 0, null, null);

    public static TaskRunStatistic of(List<TaskRun> taskRuns) {
        if (ListUtils.isEmpty(taskRuns)) {
            return EMPTY;
        }

        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;
        for (TaskRun taskRun : taskRuns) {
            long durationMs = taskRun.getState().getDurationOrComputeIt().toMillis();
            sum += durationMs;
            min = Math.min(min, durationMs);
            max = Math.max(max, durationMs);
        }

        return new TaskRunStatistic(taskRuns.size(), sum, min, max);
    }

    public TaskRunStatistic plus(TaskRunStatistic other) {
        if (other == null || other.count == 0) {
            return this;
        }
        if (this.count == 0) {
            return other;
        }

        return new TaskRunStatistic(
            this.count + other.count,
            this.durationSumMs + other.durationSumMs,
            minWith(other),
            maxWith(other)
        );
    }

    private Long maxWith(TaskRunStatistic other) {
        if (this.durationMaxMs == null && other.durationMaxMs == null) {
            return null;
        }

        return Math.max(this.durationMaxMs != null ? this.durationMaxMs : Long.MIN_VALUE, other.durationMaxMs != null ? other.durationMaxMs : Long.MIN_VALUE);
    }

    private Long minWith(TaskRunStatistic other) {
        if (this.durationMinMs == null && other.durationMinMs == null) {
            return null;
        }

        return Math.min(this.durationMinMs != null ? this.durationMinMs : Long.MAX_VALUE, other.durationMinMs != null ? other.durationMinMs : Long.MAX_VALUE);
    }

    /**
     * Returns this accumulator with {@code other} removed.
     * Count and duration sum subtract exactly; min/max keep this accumulator's extremes.
     * Keeping the extremes is exact when the removed contribution does not hold the extreme,
     * and a conservative over-approximation otherwise — exact rollback would require retaining
     * every contribution, which this accumulator deliberately does not do.
     * Removing everything (or more) returns {@link #EMPTY} with null min/max, which is exact.
     */
    public TaskRunStatistic minus(TaskRunStatistic other) {
        if (other == null || other.count == 0) {
            return this;
        }
        if (this.count == 0 || other.count >= this.count) {
            return EMPTY;
        }

        return new TaskRunStatistic(
            this.count - other.count,
            Math.max(0, this.durationSumMs - other.durationSumMs),
            this.durationMinMs,
            this.durationMaxMs
        );
    }

    /**
     * Serializes this accumulator as a plain map so it can be carried inside a task run's JSON
     * outputs (see {@link io.kestra.plugin.core.flow.Loop#TASK_RUN_STATISTIC_OUTPUT}).
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "count", count,
            "durationSumMs", durationSumMs,
            "durationMinMs", durationMinMs == null ? 0L : durationMinMs,
            "durationMaxMs", durationMaxMs == null ? 0L : durationMaxMs
        );
    }

    /**
     * Reads back a map produced by {@link #toMap()}. Values are read as {@link Number} rather than
     * cast directly, since a round trip through JSON task-output storage may deserialize them as
     * {@code Integer} or {@code Long} depending on magnitude. Returns {@link #EMPTY} for a null or
     * empty map.
     */
    public static TaskRunStatistic fromMap(@Nullable Map<String, Object> map) {
        if (MapUtils.isEmpty(map)) {
            return EMPTY;
        }

        long count = ((Number) map.get("count")).longValue();
        if (count == 0) {
            return EMPTY;
        }

        return new TaskRunStatistic(
            count,
            ((Number) map.get("durationSumMs")).longValue(),
            ((Number) map.get("durationMinMs")).longValue(),
            ((Number) map.get("durationMaxMs")).longValue()
        );
    }
}
