package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
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
public class Metrics<C extends ColumnDescriptor<Metrics.Fields>> extends DataFilter<Metrics.Fields, C> {
    @Override
    public Class<? extends QueryBuilderInterface<Metrics.Fields>> repositoryClass() {
        return MetricRepositoryInterface.class;
    }

    public enum Fields {
        NAMESPACE,
        FLOW_ID,
        TASK_ID,
        EXECUTION_ID,
        TASK_RUN_ID,
        TYPE,
        NAME,
        VALUE,
        DATE
    }
}
