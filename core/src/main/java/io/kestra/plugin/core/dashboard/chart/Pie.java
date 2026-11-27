package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.plugin.core.dashboard.chart.pies.PieOption;

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
@Schema(
    title = "Show proportions and distributions using pie charts."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a pie chart with Executions per State.",
            full = true,
            code = """
                charts:
                  - id: executions_pie
                    type: io.kestra.plugin.core.dashboard.chart.Pie
                    chartOptions:
                      displayName: Total Executions
                      description: Total executions per state
                    legend:
                      enabled: true
                      colorByColumn: state
                    data:
                      type: io.kestra.plugin.core.dashboard.data.Executions
                      columns:
                        state:
                          field: STATE
                        total:
                          agg: COUNT
                """
        ),
        @Example(
            title = "Display open issues by priority, with a color and a display name per priority, listed from critical to low.",
            full = true,
            code = """
                charts:
                  - id: open_by_priority
                    type: io.kestra.plugin.core.dashboard.chart.Pie
                    chartOptions:
                      displayName: Open issues by priority
                      colorByColumn: name
                      colorByValues:
                        - value: by_priority.critical
                          label: Critical
                          color: failed
                        - value: by_priority.high
                          label: High
                          color: warning
                        - value: by_priority.medium
                          label: Medium
                          color: "#f9a825"
                        - value: by_priority.low
                          label: Low
                          color: success
                    data:
                      type: io.kestra.plugin.core.dashboard.data.Metrics
                      columns:
                        name:
                          field: NAME
                        value:
                          field: VALUE
                          agg: MAX
                """
        )
    }
)
public class Pie<F extends Enum<F>, D extends DataFilter<F, ? extends ColumnDescriptor<F>>> extends DataChart<PieOption, D> {
    @Override
    public Integer minNumberOfAggregations() {
        return 1;
    }

    @Override
    public Integer maxNumberOfAggregations() {
        return 1;
    }
}
