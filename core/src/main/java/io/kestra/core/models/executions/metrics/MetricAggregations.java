package io.kestra.core.models.executions.metrics;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class MetricAggregations {
    @NotNull
    public String groupBy;

    @NotNull
    public List<MetricAggregation> aggregations;
}
