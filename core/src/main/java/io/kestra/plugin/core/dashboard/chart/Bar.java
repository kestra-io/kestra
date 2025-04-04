package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;

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
            title = "Display a bar chart with with Executions per Namespace.",
            full = true,
            code = {
                "charts:\n" +
                    "- id: executions_per_namespace_bars\n" +
                    "type: io.kestra.plugin.core.dashboard.chart.Bar\n" +
                    "chartOptions:\n" +
                        "displayName: Executions (per namespace)\n" +
                        "description: Executions count per namespace\n" +
                        "legend:\n" +
                            "enabled: true\n" +
                        "column: namespace\n" +
                    "data:\n" +
                        "type: io.kestra.plugin.core.dashboard.data.Executions\n" +
                        "columns:\n" +
                            "namespace:\n" +
                                "field: NAMESPACE\n" +
                            "state:\n" +
                                "field: STATE\n" +
                            "total:\n" +
                                "displayName: Execution\n" +
                                "agg: COUNT\n"
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
