package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.core.validations.ExecutionsDataFilterKPIValidation;
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
    title = "Display a chart with executions in success in a given namespace.",
    description = "Change."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with executions in success in a given namespace.",
            full = true,
            code = {
                "id: kpi_success_ratio\n" +
                "type: io.kestra.plugin.core.dashboard.chart.KPI\n" +
                "chartOptions:\n" +
                "  displayName: Success Ratio\n" +
                "  numberType: PERCENTAGE\n" +
                "  width: 3\n" +
                "data:\n" +
                "  type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI\n" +
                "  columns:\n" +
                "    field: ID\n" +
                "    agg: COUNT\n" +
                "  numerator:\n" +
                "    - type: IN\n" +
                "      field: STATE\n" +
                "      values:\n" +
                "        - SUCCESS\n"
            }
        )
    }
)
@JsonTypeName("ExecutionsKPI")
@ExecutionsDataFilterKPIValidation
public class ExecutionsKPI<C extends ColumnDescriptor<ExecutionsKPI.Fields>> extends DataFilterKPI<ExecutionsKPI.Fields, C> implements IExecutions {
    @Override
    public Class<? extends QueryBuilderInterface<ExecutionsKPI.Fields>> repositoryClass() {
        return ExecutionRepositoryInterface.class;
    }
}
