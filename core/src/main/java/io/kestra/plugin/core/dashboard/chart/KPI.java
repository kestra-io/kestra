package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.plugin.core.dashboard.chart.kpis.KpiOption;
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
    title = "Track a specific value.",
    description = "KPI charts are used to display a single value, such as a count or percentage, often used to track performance indicators."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a Success Ratio per Flow",
            full = true,
            code = { """
                charts:
                  - id: KPI_SUCCESS_PERCENTAGE
                    type: io.kestra.plugin.core.dashboard.chart.KPI # io.kestra.plugin.core.dashboard.chart.Trends
                    chartOptions:
                      displayName: Success Ratio
                      numberType: PERCENTAGE
                      width: 3
                    data:
                      type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI # io.kestra.plugin.core.dashboard.data.ExecutionsTrends
                      columns:
                        field: FLOW_ID
                        agg: COUNT
                      numerator:
                        - field: STATE
                          type: IN
                          values:
                            - SUCCESS
                      where: # optional if you filter by namespace
                        - field: NAMESPACE
                          type: EQUAL_TO
                          value: "company.team"
                """
            }
        )
    }
)
public class KPI <F extends Enum<F>, D extends DataFilterKPI<F, ? extends ColumnDescriptor<F>>> extends DataChartKPI<KpiOption, D> {

    @Override
    public Integer minNumberOfAggregations() {
        return 1;
    }

    @Override
    public Integer maxNumberOfAggregations() {
        return 1;
    }
}
