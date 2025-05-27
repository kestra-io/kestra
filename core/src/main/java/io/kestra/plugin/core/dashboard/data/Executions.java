package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.core.validations.ExecutionsDataFilterValidation;
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
@ExecutionsDataFilterValidation
@Schema(
    title = "Display Execution data in a dashboard chart.",
    description = "Execution data can be displayed in charts broken out by Namespace and filtered by State, for example."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with a Executions per Namespace broken out by State.",
            full = true,
            code = {
                "id: executions_per_namespace_bars\n" +
                "type: io.kestra.plugin.core.dashboard.chart.Bar\n" +
                "chartOptions:\n" +
                  "displayName: Executions (per namespace)\n" +
                  "description: Executions count per namespace\n" +
                  "legend:\n" +
                    "enabled: true\n" +
                  "column: namespace\n" +
                "data\n" +
                  "type: io.kestra.plugin.core.dashboard.data.Executions\n" +
                  "columns:\n" +
                    "namespace:\n" +
                      "field: NAMESPACE\n" +
                    "state:\n" +
                      "field: STATE\n" +
                    "total:\n" +
                      "displayName: Executions\n" +
                      "agg: COUNT\n"
            }
        )
    }
)
@JsonTypeName("Executions")
public class Executions<C extends ColumnDescriptor<Executions.Fields>> extends DataFilter<Executions.Fields, C> implements IExecutions {
    @Override
    public Class<? extends QueryBuilderInterface<Executions.Fields>> repositoryClass() {
        return ExecutionRepositoryInterface.class;
    }
}
