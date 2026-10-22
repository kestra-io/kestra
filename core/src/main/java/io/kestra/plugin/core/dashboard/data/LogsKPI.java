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
    title = "Display a single Log value in a KPI chart.",
    description = "Aggregates Log data into a single value, such as a count of logs or the ratio of logs matching the `numerator` filters, to be displayed in a KPI chart."
)
@Plugin(
    examples = {
        @Example(
            title = "Display the percentage of ERROR logs in a given namespace.",
            full = true,
            code = { """
                charts:
                  - id: kpi_error_logs_ratio
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    chartOptions:
                      displayName: Error Logs Ratio
                      numberType: PERCENTAGE
                      width: 3
                    data:
                      type: io.kestra.plugin.core.dashboard.data.LogsKPI
                      columns:
                        field: LEVEL
                        agg: COUNT
                      numerator:
                        - type: IN
                          field: LEVEL
                          values:
                            - ERROR
                      where:
                        - field: NAMESPACE
                          type: EQUAL_TO
                          value: "company.team"
                """
            }
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
