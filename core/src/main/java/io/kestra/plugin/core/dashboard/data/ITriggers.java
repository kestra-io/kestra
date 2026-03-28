package io.kestra.plugin.core.dashboard.data;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;

public interface ITriggers extends IData<ITriggers.Fields> {

    default List<AbstractFilter<ITriggers.Fields>> whereWithGlobalFilters(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate,
        List<AbstractFilter<ITriggers.Fields>> where) {
        List<AbstractFilter<ITriggers.Fields>> updatedWhere = where != null ? new ArrayList<>(where) : new ArrayList<>();

        if (filters == null) {
            return updatedWhere;
        }

        List<QueryFilter> namespaceFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
        if (!namespaceFilters.isEmpty()) {
            updatedWhere.removeIf(filter -> filter.getField().equals(ITriggers.Fields.NAMESPACE));
            namespaceFilters.forEach(f -> {
                updatedWhere.add(f.toDashboardFilterBuilder(ITriggers.Fields.NAMESPACE, f.value()));
            });
        }

        List<QueryFilter> flowFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.FLOW_ID)).toList();
        if (!flowFilters.isEmpty()) {
            updatedWhere.removeIf(filter -> filter.getField().equals(Fields.FLOW_ID));
            flowFilters.forEach(f -> {
                updatedWhere.add(f.toDashboardFilterBuilder(Fields.FLOW_ID, f.value()));
            });
        }

        return updatedWhere;
    }

    enum Fields {
        ID,
        NAMESPACE,
        FLOW_ID,
        TRIGGER_ID,
        EXECUTION_ID,
        NEXT_EXECUTION_DATE,
        WORKER_ID
    }
}