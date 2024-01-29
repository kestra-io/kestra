package io.kestra.core.models.executions.statistics;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.flows.State;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class DailyExecutionStatistics {
    @NotNull
    protected Instant startDate;

    @NotNull
    private Duration duration;

    @Builder.Default
    @JsonInclude
    private Map<State.Type, Long> executionCounts = new HashMap<>(ImmutableMap.<State.Type, Long>builder()
        .put(State.Type.CREATED, 0L)
        .put(State.Type.RUNNING, 0L)
        .put(State.Type.RESTARTED, 0L)
        .put(State.Type.KILLING, 0L)
        .put(State.Type.SUCCESS, 0L)
        .put(State.Type.WARNING, 0L)
        .put(State.Type.FAILED, 0L)
        .put(State.Type.KILLED, 0L)
        .put(State.Type.PAUSED, 0L)
        .put(State.Type.QUEUED, 0L)
        .put(State.Type.CANCELLED, 0L)
        .build()
    );

    private String groupBy;

    @Value
    @Builder
    static public class Duration {
        @NotNull
        java.time.Duration min;

        @NotNull
        java.time.Duration avg;

        @NotNull
        java.time.Duration max;

        @NotNull
        java.time.Duration sum;

        @NotNull
        long count;
    }
}
