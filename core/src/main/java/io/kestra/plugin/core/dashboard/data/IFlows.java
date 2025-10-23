package io.kestra.plugin.core.dashboard.data;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.utils.ListUtils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public interface IFlows extends IData<IFlows.Fields> {

    default List<AbstractFilter<IFlows.Fields>> whereWithGlobalFilters(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate, List<AbstractFilter<IFlows.Fields>> where) {
        List<AbstractFilter<IFlows.Fields>> updatedWhere = where != null ? new ArrayList<>(where) : new ArrayList<>();

        if (ListUtils.isEmpty(filters)) {
            return updatedWhere;
        }

        List<QueryFilter> namespaceFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
        if (!namespaceFilters.isEmpty()) {
            updatedWhere.removeIf(filter -> filter.getField().equals(IFlows.Fields.NAMESPACE));
            namespaceFilters.forEach(f -> {
                updatedWhere.add(f.toDashboardFilterBuilder(IFlows.Fields.NAMESPACE, f.value()));
            });
        }


        return updatedWhere;
    }

    enum Fields {
        ID,
        NAMESPACE,
        REVISION
    }
}
