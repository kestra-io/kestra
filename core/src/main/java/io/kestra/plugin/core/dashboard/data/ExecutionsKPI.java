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
            code = """
                charts:
                  - id: kpi_success_ratio
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    chartOptions:
                      displayName: Success Ratio
                      numberType: PERCENTAGE
                      width: 3
                    data:
                      type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                      columns:
                        field: ID
                        agg: COUNT
                      numerator:
                        - type: IN
                          field: STATE
                          values:
                            - SUCCESS
                """
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
