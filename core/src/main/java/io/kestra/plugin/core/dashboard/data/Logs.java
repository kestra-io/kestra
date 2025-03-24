package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.GreaterThanOrEqualTo;
import io.kestra.core.models.dashboards.filters.LessThanOrEqualTo;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Plugin
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@Schema(title = "Logs")
public class Logs<C extends ColumnDescriptor<Logs.Fields>> extends DataFilter<Logs.Fields, C> {
    @Override
    public Class<? extends QueryBuilderInterface<Logs.Fields>> repositoryClass() {
        return LogRepositoryInterface.class;
    }

    @Override
    public void setGlobalFilter(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate) {
        List<AbstractFilter<Fields>> where = this.getWhere() != null ? new ArrayList<>(this.getWhere()) : new ArrayList<>();

        if (filters == null) {
            return;
        }

        List<QueryFilter> namespaceFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
        if (!namespaceFilters.isEmpty()) {
            where.removeIf(filter -> filter.getField().equals(Logs.Fields.NAMESPACE));
            namespaceFilters.forEach(f -> {
                where.add(f.toDashboardFilterBuilder(Logs.Fields.NAMESPACE, f.value()));
            });
        }

        if (startDate != null || endDate != null) {
            where.removeIf(f -> f.getField().equals(Fields.DATE));
            if (startDate != null) {
                where.add(GreaterThanOrEqualTo.<Logs.Fields>builder().field(Fields.DATE).value(startDate.toInstant()).build());
            }
            if (endDate != null) {
                where.add(LessThanOrEqualTo.<Logs.Fields>builder().field(Fields.DATE).value(endDate.toInstant()).build());
            }
        }

        this.setWhere(where);
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
