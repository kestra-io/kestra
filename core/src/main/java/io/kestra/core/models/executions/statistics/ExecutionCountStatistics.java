package io.kestra.core.models.executions.statistics;

import io.kestra.core.models.flows.State;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * ExecutionCounts.
 *
 * @param counts    The execution counts by state.
 * @param total     The total execution count.
 */
public record ExecutionCountStatistics(
    @NotNull Map<State.Type, Long> counts,
    @NotNull Long total
) implements Comparable<ExecutionCountStatistics> {

    private static final Map<State.Type, Long> DEFAULT_COUNTS = Map.ofEntries(
        Map.entry(State.Type.CREATED, 0L),
        Map.entry(State.Type.RUNNING, 0L),
        Map.entry(State.Type.RESTARTED, 0L),
        Map.entry(State.Type.KILLING, 0L),
        Map.entry(State.Type.SUCCESS, 0L),
        Map.entry(State.Type.WARNING, 0L),
        Map.entry(State.Type.FAILED, 0L),
        Map.entry(State.Type.KILLED, 0L),
        Map.entry(State.Type.PAUSED, 0L),
        Map.entry(State.Type.QUEUED, 0L),
        Map.entry(State.Type.CANCELLED, 0L)
    );

    public ExecutionCountStatistics(final @NotNull Map<State.Type, Long> counts) {
        this(withAllStatesZero(counts), counts.values().stream().mapToLong(l -> l).sum());
    }

    /** {@inheritDoc} **/
    @Override
    public int compareTo(ExecutionCountStatistics that) {
        return Long.compare(this.total, that.total);
    }

    private static Map<State.Type, Long> withAllStatesZero(final Map<State.Type, Long> counts) {
        Map<State.Type, Long> map = new HashMap<>(DEFAULT_COUNTS);
        map.putAll(counts);
        return map;
    }
}
