package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.repositories.FlowRepositoryInterface;
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
    title = "Display a chart with Flows KPI.",
    description = "Change."
)
@Plugin(
    examples = {
        @Example(
            title = "Display count of Flows.",
            full = true,
            code = """
                charts:
                  - id: kpi
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    data:
                      type: io.kestra.plugin.core.dashboard.data.FlowsKPI
                      columns:
                        field: ID
                        agg: COUNT
                """
        )
    }
)
@JsonTypeName("FlowsKPI")
public class FlowsKPI<C extends ColumnDescriptor<FlowsKPI.Fields>> extends DataFilterKPI<FlowsKPI.Fields, C> implements IFlows {
    @Override
    public Class<? extends QueryBuilderInterface<Fields>> repositoryClass() {
        return FlowRepositoryInterface.class;
    }
}
