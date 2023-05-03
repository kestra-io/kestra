package io.kestra.core.models.executions.metrics;

import lombok.Builder;
import lombok.Getter;

import javax.validation.constraints.NotNull;
import java.util.List;

@Builder
@Getter
public class MetricAggregations {
    @NotNull
    public String groupBy;
    @NotNull
    public List<MetricAggregation> aggregations;

}
