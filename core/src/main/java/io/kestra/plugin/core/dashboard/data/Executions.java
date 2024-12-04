package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.core.validations.ExecutionsDataFilterValidation;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@ExecutionsDataFilterValidation
public class Executions<C extends ColumnDescriptor<Executions.Fields>> extends DataFilter<Executions.Fields, C> {
    @Override
    public Class<? extends QueryBuilderInterface<Executions.Fields>> repositoryClass() {
        return ExecutionRepositoryInterface.class;
    }

    public enum Fields {
        ID,
        NAMESPACE,
        FLOW_ID,
        FLOW_REVISION,
        STATE,
        DURATION,
        LABELS,
        START_DATE,
        END_DATE
    }
}
