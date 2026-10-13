package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;

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
    title = "Display a chart with Metrics KPI.",
    description = "Metrics data can be aggregated in a KPI chart to track performance indicators."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with total rows inserted.",
            full = true,
            code = """
                charts:
                  - id: metrics_kpi
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    chartOptions:
                      displayName: Total Rows Inserted
                    data:
                      type: io.kestra.plugin.core.dashboard.data.MetricsKPI
                      columns:
                        field: VALUE
                        agg: SUM
                      where:
                        - field: NAME
                          type: EQUAL_TO
                          value: rows
                """
        )
    }
)
public class MetricsKPI<C extends ColumnDescriptor<MetricsKPI.Fields>> extends DataFilterKPI<MetricsKPI.Fields, C> implements IMetrics {
    @Override
    public Class<? extends QueryBuilderInterface<Fields>> repositoryClass() {
        return MetricRepositoryInterface.class;
    }
}
