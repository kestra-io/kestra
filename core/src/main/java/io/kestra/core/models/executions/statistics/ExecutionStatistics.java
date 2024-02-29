package io.kestra.core.models.executions.statistics;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Builder
@Data
public class ExecutionStatistics {
    @NotNull
    private String stateCurrent;

    @NotNull
    private Instant date;

    @NotNull
    private Long count;

    @NotNull
    private Long durationMin;

    @NotNull
    private Long durationMax;

    @NotNull
    private Long durationSum;
}
