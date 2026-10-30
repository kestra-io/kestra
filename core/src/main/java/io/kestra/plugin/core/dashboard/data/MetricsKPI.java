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
    title = "Display a single Metric value in a KPI chart.",
    description = "Aggregates Metrics exposed by tasks into a single value, such as the total number of rows loaded by a BigQuery task or an output count from a SQL query, to be displayed in a KPI chart."
)
@Plugin(
    examples = {
        @Example(
            title = "Display the total number of rows inserted in a given namespace.",
            full = true,
            code = { """
                charts:
                  - id: kpi_inserted_rows
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    chartOptions:
                      displayName: Rows Inserted
                      numberType: FLAT
                      width: 3
                    data:
                      type: io.kestra.plugin.core.dashboard.data.MetricsKPI
                      columns:
                        field: VALUE
                        agg: SUM
                      where:
                        - field: NAME
                          type: EQUAL_TO
                          value: rows
                        - field: NAMESPACE
                          type: EQUAL_TO
                          value: "company.team"
                """
            }
        )
    }
)
public class MetricsKPI<C extends ColumnDescriptor<MetricsKPI.Fields>> extends DataFilterKPI<MetricsKPI.Fields, C> implements IMetrics {
    @Override
    public Class<? extends QueryBuilderInterface<Fields>> repositoryClass() {
        return MetricRepositoryInterface.class;
    }
}
