package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.validations.TimeSeriesChartValidation;
import io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesColumnDescriptor;
import io.kestra.plugin.core.dashboard.chart.timeseries.TimeSeriesOption;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@TimeSeriesChartValidation
@Schema(
    title = "Track trends over time with dynamic time series charts."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with Executions over the last week.",
            full = true,
            code = """
                charts:
                  - id: executions_timeseries
                    type: io.kestra.plugin.core.dashboard.chart.TimeSeries
                    chartOptions:
                      displayName: Total Executions
                      description: Executions last week
                      legend:
                        enabled: true
                      column: date
                      colorByColumn: state
                    data:
                      type: io.kestra.plugin.core.dashboard.data.Executions
                      columns:
                        date:
                          field: START_DATE
                          displayName: Date
                        state:
                          field: STATE
                        total:
                          displayName: Executions
                          agg: COUNT
                          graphStyle: BARS
                        duration:
                          displayName: Duration
                          field: DURATION
                          agg: SUM
                          graphStyle: LINES
                """
        )
    }
)
public class TimeSeries<F extends Enum<F>, D extends DataFilter<F, ? extends TimeSeriesColumnDescriptor<F>>> extends DataChart<TimeSeriesOption, D> {

    @Override
    public Integer minNumberOfAggregations() {
        return 1;
    }

    @Override
    public Integer maxNumberOfAggregations() {
        return 2;
    }
}
