package io.kestra.core.models.executions.metrics;

import lombok.Builder;

import javax.validation.constraints.NotNull;
import java.time.Instant;

@Builder
public class MetricAggregation {
    @NotNull
    public String name;
    public Double value;
    @NotNull
    public Instant date;

}
