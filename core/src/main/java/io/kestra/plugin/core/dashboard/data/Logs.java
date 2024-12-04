package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
public class Logs<C extends ColumnDescriptor<Logs.Fields>> extends DataFilter<Logs.Fields, C> {
    @Override
    public Class<? extends QueryBuilderInterface<Logs.Fields>> repositoryClass() {
        return LogRepositoryInterface.class;
    }

    @Override
    public Set<Fields> aggregationForbiddenFields() {
        return Set.of(Fields.MESSAGE);
    }

    public enum Fields {
        NAMESPACE,
        FLOW_ID,
        EXECUTION_ID,
        TASK_ID,
        DATE,
        TASK_RUN_ID,
        ATTEMPT_NUMBER,
        TRIGGER_ID,
        LEVEL,
        MESSAGE
    }
}
