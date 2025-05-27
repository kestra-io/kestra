package io.kestra.core.models.dashboards.charts;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.validations.DataChartKPIValidation;
import io.kestra.plugin.core.dashboard.chart.kpis.KpiOption;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@DataChartKPIValidation
public abstract class DataChartKPI<P extends KpiOption, D extends DataFilterKPI<?, ?>> extends Chart<P> implements io.kestra.core.models.Plugin {
    @NotNull
    private D data;

    public Integer minNumberOfAggregations() {
        return null;
    }

    public Integer maxNumberOfAggregations() {
        return null;
    }
}
