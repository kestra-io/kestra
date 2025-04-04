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
            title = "Display a pie chart with with Executions per State.",
            full = true,
            code = {
                "charts:\n" +
                    "- id: executions_pie\n" +
                    "type: io.kestra.plugin.core.dashboard.chart.Pie\n" +
                    "chartOptions:\n" +
                        "displayName: Total Executions\n" +
                        "description: Total executions per state\n" +
                        "legend:\n" +
                            "enabled: true\n" +
                        "colorByColumn: state\n" +
                    "data:\n" +
                        "type: io.kestra.plugin.core.dashboard.data.Executions\n" +
                        "columns:\n" +
                            "state:\n" +
                                "field: STATE\n" +
                            "total:\n" +
                                "agg: COUNT\n"
            }
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
