package org.kestra.core.models.executions.metrics;

import lombok.Builder;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
public class ExecutionMetricsAggregation {
    @NotNull
    private String namespace;
    @NotNull
    private String id;
    @NotNull
    private List<ExecutionMetrics> metrics;

    private Stats periodDurationStats;

    private Stats lastDayDurationStats;

    private Trend trend;

    public enum Trend {
        UP,
        DOWN,
        NEUTRAL
    }
}
