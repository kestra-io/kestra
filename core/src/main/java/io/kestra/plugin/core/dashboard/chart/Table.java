package io.kestra.plugin.core.dashboard.chart;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.plugin.core.dashboard.chart.tables.TableColumnDescriptor;
import io.kestra.plugin.core.dashboard.chart.tables.TableOption;
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
    title = "Display structured data in a clear, sortable table."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a table with Log counts for each level by Namespace.",
            full = true,
            code = """
                charts:
                  - id: table_logs
                    type: io.kestra.plugin.core.dashboard.chart.Table
                    chartOptions:
                      displayName: Log count by level for filtered namespace
                    data:
                      type: io.kestra.plugin.core.dashboard.data.Logs
                      columns:
                        level:
                          field: LEVEL
                          count:
                           agg: COUNT
                      where:
                        - field: NAMESPACE
                          type: IN
                          values:
                            - dev_graph
                            - prod_graph
                """
        )
    }
)
public class Table<F extends Enum<F>, D extends DataFilter<F, ? extends TableColumnDescriptor<F>>> extends DataChart<TableOption, D> {
}
