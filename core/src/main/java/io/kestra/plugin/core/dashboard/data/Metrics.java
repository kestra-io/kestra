package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.GlobalFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.EqualTo;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@Schema(title = "Metrics")
public class Metrics<C extends ColumnDescriptor<Metrics.Fields>> extends DataFilter<Metrics.Fields, C> {
    @Override
    public Class<? extends QueryBuilderInterface<Metrics.Fields>> repositoryClass() {
        return MetricRepositoryInterface.class;
    }

    @Override
    public void setGlobalFilter(GlobalFilter globalFilter) {
        List<AbstractFilter<Fields>> where = this.getWhere() != null ? new ArrayList<>(this.getWhere()) : new ArrayList<>();

        if (globalFilter.getNamespace() != null) {
            where.removeIf(f -> f.getField().equals(Fields.NAMESPACE));
            where.add(EqualTo.<Fields>builder().field(Fields.NAMESPACE).value(globalFilter.getNamespace()).build());
        }

        this.setWhere(where);
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
