package io.kestra.plugin.core.dashboard.data;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.Contains;
import io.kestra.core.models.dashboards.filters.GreaterThanOrEqualTo;
import io.kestra.core.models.dashboards.filters.LessThanOrEqualTo;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public interface IExecutions extends IData<IExecutions.Fields> {

    default List<AbstractFilter<Fields>> whereWithGlobalFilters(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate, List<AbstractFilter<Fields>> where) {
        List<AbstractFilter<Fields>> updatedWhere = where != null ? new ArrayList<>(where) : new ArrayList<>();

        if (filters != null) {
            List<QueryFilter> namespaceFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
            if (!namespaceFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.NAMESPACE));
                namespaceFilters.forEach(f -> {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.NAMESPACE, f.value()));
                });
            }

            List<QueryFilter> labelFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.LABELS)).toList();
            if (!labelFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.LABELS));
                labelFilters.forEach(f -> {
                    updatedWhere.add(Contains.<Fields>builder().field(Fields.LABELS).value(f.value()).build());
                });
            }

            List<QueryFilter> flowFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.FLOW_ID)).toList();
            if (!flowFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.FLOW_ID));
                flowFilters.forEach(f -> {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.FLOW_ID, f.value()));
                });
            }

            List<QueryFilter> stateFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.STATE)).toList();
            if (!stateFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.STATE));
                stateFilters.forEach(f -> {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.STATE, f.value()));
                });
            }


        }


        if (startDate != null || endDate != null) {
            if (startDate != null) {
                updatedWhere.removeIf(f -> f.getField().equals(Fields.START_DATE));
                updatedWhere.add(GreaterThanOrEqualTo.<Fields>builder().field(Fields.START_DATE).value(startDate.toInstant()).build());
            }
            if (endDate != null) {
                updatedWhere.removeIf(f -> f.getField().equals(Fields.END_DATE));
                updatedWhere.add(LessThanOrEqualTo.<Fields>builder().field(Fields.END_DATE).value(endDate.toInstant()).build());
            }
        }

        return updatedWhere;
    }

    enum Fields {
        ID,
        NAMESPACE,
        FLOW_ID,
        FLOW_REVISION,
        STATE,
        DURATION,
        LABELS,
        START_DATE,
        END_DATE,
        TRIGGER_EXECUTION_ID
    }
}
