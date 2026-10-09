package io.kestra.core.models.executions.statistics;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;

import jakarta.annotation.Nullable;

/**
 * Number of task runs per task id and current state, accumulated across all the iterations of a Loop
 * (nested loops included) since the task runs of its sub-executions are not reachable from the parent execution.
 */
public record TaskRunStateCounts(Map<String, Map<State.Type, Long>> counts) {
    private static final TaskRunStateCounts EMPTY = new TaskRunStateCounts(Map.of());

    public TaskRunStateCounts {
        counts = counts == null ? Map.of() : counts;
    }

    public static TaskRunStateCounts empty() {
        return EMPTY;
    }

    public static TaskRunStateCounts of(@Nullable List<TaskRun> taskRuns) {
        if (ListUtils.isEmpty(taskRuns)) {
            return EMPTY;
        }

        Map<String, Map<State.Type, Long>> counts = new HashMap<>();
        for (TaskRun taskRun : taskRuns) {
            counts.computeIfAbsent(taskRun.getTaskId(), k -> new EnumMap<>(State.Type.class))
                .merge(taskRun.getState().getCurrent(), 1L, Long::sum);
        }
        return new TaskRunStateCounts(counts);
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    public TaskRunStateCounts plus(@Nullable TaskRunStateCounts other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }

        Map<String, Map<State.Type, Long>> merged = new HashMap<>();
        this.counts.forEach((taskId, byState) -> merged.put(taskId, new EnumMap<>(byState)));
        other.counts.forEach((taskId, byState) -> {
            Map<State.Type, Long> target = merged.computeIfAbsent(taskId, k -> new EnumMap<>(State.Type.class));
            byState.forEach((state, count) -> target.merge(state, count, Long::sum));
        });
        return new TaskRunStateCounts(merged);
    }

    /**
     * Serializes this accumulator as {@code taskId -> stateName -> count} so it can be carried inside a task run's
     * JSON outputs (see {@link io.kestra.plugin.core.flow.Loop#TASK_RUN_STATE_COUNTS_OUTPUT}).
     */
    public Map<String, Map<String, Long>> toMap() {
        Map<String, Map<String, Long>> map = HashMap.newHashMap(counts.size());
        counts.forEach((taskId, byState) -> {
            Map<String, Long> states = HashMap.newHashMap(byState.size());
            byState.forEach((state, count) -> states.put(state.name(), count));
            map.put(taskId, states);
        });
        return map;
    }

    /**
     * Reads back a map produced by {@link #toMap()}. Counts are read as {@link Number} since a JSON round trip
     * may deserialize them as {@code Integer} or {@code Long}. Returns an empty instance for a null or empty map.
     */
    public static TaskRunStateCounts fromMap(@Nullable Map<String, Map<String, Number>> map) {
        if (MapUtils.isEmpty(map)) {
            return EMPTY;
        }

        Map<String, Map<State.Type, Long>> counts = HashMap.newHashMap(map.size());
        map.forEach((taskId, byState) -> {
            Map<State.Type, Long> states = new EnumMap<>(State.Type.class);
            byState.forEach((state, count) -> states.put(State.Type.valueOf(state), count.longValue()));
            counts.put(taskId, states);
        });
        return new TaskRunStateCounts(counts);
    }
}
