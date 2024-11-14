package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.validations.TimeSeriesChartValidation;
import io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesColumnDescriptor;
import io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesOption;
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
@TimeSeriesChartValidation
public class TimeSeries<F extends Enum<F>, D extends DataFilter<F, ? extends TimeSeriesColumnDescriptor<F>>> extends DataChart<TimeSeriesOption, D> {
    @Override
    public Integer minNumberOfAggregations() {
        return 1;
    }
}
