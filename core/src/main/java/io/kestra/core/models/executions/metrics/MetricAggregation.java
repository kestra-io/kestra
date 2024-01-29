package io.kestra.core.models.executions.metrics;

import lombok.Builder;

import java.time.Instant;
import jakarta.validation.constraints.NotNull;

@Builder
public class MetricAggregation {
    @NotNull
    public String name;

    public Double value;

    @NotNull
    public Instant date;
}
