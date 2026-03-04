package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
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
    title = "Display Flow data in a dashboard chart."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with a list of Flows.",
            full = true,
            code = """
                charts:
                  - id: list_flows
                    type: io.kestra.plugin.core.dashboard.chart.Table
                    data:
                      type: io.kestra.plugin.core.dashboard.data.Flows
                      columns:
                        namespace:
                          field: NAMESPACE
                        id:
                          field: ID
                """
        )
    }
)
@JsonTypeName("Flows")
public class Flows<C extends ColumnDescriptor<Flows.Fields>> extends DataFilter<Flows.Fields, C> implements IFlows {
    @Override
    public Class<? extends QueryBuilderInterface<Fields>> repositoryClass() {
        return FlowRepositoryInterface.class;
    }
}
