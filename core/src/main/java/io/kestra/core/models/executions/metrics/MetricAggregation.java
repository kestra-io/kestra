package io.kestra.core.models.executions.metrics;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public class MetricAggregation {
    @NotNull
    public String name;

    public Double value;

    @NotNull
    public Instant date;
}
