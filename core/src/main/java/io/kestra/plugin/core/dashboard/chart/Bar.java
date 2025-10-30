package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.plugin.core.dashboard.chart.bars.BarOption;
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
    title = "Compare categorical data visually with bar charts."
    )
@Plugin(
    examples = {
        @Example(
            title = "Display a bar chart with Executions per Namespace.",
            full = true,
            code = { """
                charts:
                  - id: executions_per_namespace_bars
                    type: io.kestra.plugin.core.dashboard.chart.Bar
                    chartOptions:
                      displayName: Executions (per namespace)
                      description: Executions count per namespace
                    legend:
                      enabled: true
                    column: namespace
                    data:
                      type: io.kestra.plugin.core.dashboard.data.Executions
                      columns:
                        namespace:
                          field: NAMESPACE
                        state:
                          field: STATE
                        total:
                          displayName: Execution
                          agg: COUNT
                """
            }
        )
    }
)
public class Bar<F extends Enum<F>, D extends DataFilter<F, ? extends ColumnDescriptor<F>>> extends DataChart<BarOption, D> {
    @Override
    public Integer minNumberOfAggregations() {
        return 1;
    }

    @Override
    public Integer maxNumberOfAggregations() {
        return 1;
    }
}
