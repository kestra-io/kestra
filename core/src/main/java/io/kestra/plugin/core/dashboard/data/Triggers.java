package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
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
    title = "Display Execution data in a dashboard chart.",
    description = "Execution data can be displayed in charts broken out by Namespace and filtered by State, for example."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with a Triggers per Namespace broken out by State.",
            full = true,
            code = {
                "id: executions_per_namespace_bars\n"
            }
        )
    }
)
@JsonTypeName("Triggers")
public class Triggers<C extends ColumnDescriptor<Triggers.Fields>> extends DataFilter<Triggers.Fields, C> implements ITriggers {
    @Override
    public Class<? extends QueryBuilderInterface<Triggers.Fields>> repositoryClass() {
        return TriggerRepositoryInterface.class;
    }
}
