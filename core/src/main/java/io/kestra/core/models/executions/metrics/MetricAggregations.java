package io.kestra.core.models.executions.metrics;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import jakarta.validation.constraints.NotNull;

@Builder
@Getter
public class MetricAggregations {
    @NotNull
    public String groupBy;

    @NotNull
    public List<MetricAggregation> aggregations;
}
