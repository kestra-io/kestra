package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
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
    title = "Metrics are data exposed by tasks after execution.",
    description = "A chart using Metrics could display the number of rows loaded in a bigQuery task or an output count from a SQL Query; anything exposed by an execution." 
    )
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with rows inserted by Namespace.",
            full = true,
            code = { """
            charts:
              - id: table_metrics
                type: io.kestra.plugin.core.dashboard.chart.Table
                chartOptions:
                  displayName: Rows Inserted by Namespace
                data:
                  type: io.kestra.plugin.core.dashboard.data.Metrics
                  columns:
                    namespace:
                      field: NAMESPACE
                    inserted_rows:
                      field: VALUE
                      agg: SUM
                  where:
                    - field: NAME
                      type: EQUAL_TO
                      value: rows
                  orderBy:
                    - column: inserted_rows
                      order: DESC
            """
          }
        )
    }
)
public class Metrics<C extends ColumnDescriptor<Metrics.Fields>> extends DataFilter<Metrics.Fields, C> implements IMetrics {
    @Override
    public Class<? extends QueryBuilderInterface<Metrics.Fields>> repositoryClass() {
        return MetricRepositoryInterface.class;
    }
}
