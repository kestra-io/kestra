package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@Schema(
    title = "Display Log data in a dashboard chart.",
    description = "Log data can be displayed in a chart with certain parameters such as Exectution date or Log level."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with a count of Logs per date grouped by level.",
            full = true,
            code = { """
            charts:
              - id: logs_timeseries
                type: io.kestra.plugin.core.dashboard.chart.TimeSeries
                chartOptions:
                  displayName: Logs
                  description: Logs count per date grouped by level
                  legend:
                    enabled: true
                  column: date
                  colorByColumn: level
                data:
                  type: io.kestra.plugin.core.dashboard.data.Logs
                  columns:
                    date:
                      field: DATE
                      displayName: Execution Date
                    level:
                      field: LEVEL
                    total:
                      displayName: Total Executions
                      agg: COUNT
                      graphStyle: BARS
            """
          }
        )
    }
)
public class Logs<C extends ColumnDescriptor<ILogs.Fields>> extends DataFilter<ILogs.Fields, C> implements ILogs {
    @Override
    public Class<? extends QueryBuilderInterface<ILogs.Fields>> repositoryClass() {
        return LogRepositoryInterface.class;
    }

    @Override
    public Set<Fields> aggregationForbiddenFields() {
        return Set.of(Fields.MESSAGE);
    }
}
