package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
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
//@ExecutionsDataFilterValidation
@Schema(
    title = "Display Execution data in a dashboard chart.",
    description = "Change."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with a Executions in success in given Namespace.",
            full = true,
            code = {
                "id: executions_success_in_namespace\n" +
                "type: io.kestra.plugin.core.dashboard.chart.KPI\n" +
                "chartOptions:\n" +
                  "displayName: Executions (per namespace)\n" +
                  "description: Executions count per namespace\n" +
                "data\n" +
                  "type: io.kestra.plugin.core.dashboard.data.Executions\n" +
                  "columns:\n" +
                    "state:\n" +
                      "field: STATE\n"
            }
        )
    }
)
@JsonTypeName("ExecutionsKPI")
public class ExecutionsKPI<C extends ColumnDescriptor<ExecutionsKPI.Fields>> extends DataFilterKPI<ExecutionsKPI.Fields, C> implements IExecutions {
    @Override
    public Class<? extends QueryBuilderInterface<ExecutionsKPI.Fields>> repositoryClass() {
        return ExecutionRepositoryInterface.class;
    }
}
