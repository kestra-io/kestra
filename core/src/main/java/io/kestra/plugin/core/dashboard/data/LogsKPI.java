package io.kestra.plugin.core.dashboard.data;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.repositories.LogDataStoreInterface;
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
    title = "Display a chart with Logs KPI.",
    description = "Log data can be aggregated in a KPI chart to display metrics such as log count."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with a count of error logs.",
            full = true,
            code = """
                charts:
                  - id: logs_kpi
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    chartOptions:
                      displayName: Error Logs
                    data:
                      type: io.kestra.plugin.core.dashboard.data.LogsKPI
                      columns:
                        field: LEVEL
                        agg: COUNT
                      where:
                        - field: LEVEL
                          type: EQUAL_TO
                          value: ERROR
                """
        )
    }
)
public class LogsKPI<C extends ColumnDescriptor<LogsKPI.Fields>> extends DataFilterKPI<LogsKPI.Fields, C> implements ILogs {
    @Override
    public Class<? extends QueryBuilderInterface<Fields>> repositoryClass() {
        return LogDataStoreInterface.class;
    }

    @Override
    public Set<Fields> aggregationForbiddenFields() {
        return Set.of(Fields.MESSAGE);
    }
}
