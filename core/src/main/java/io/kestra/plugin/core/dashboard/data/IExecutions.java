package io.kestra.plugin.core.dashboard.data;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.Contains;
import io.kestra.core.models.dashboards.filters.EqualTo;
import io.kestra.core.models.dashboards.filters.GreaterThanOrEqualTo;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.dashboards.filters.IsNotNull;
import io.kestra.core.models.dashboards.filters.IsNull;
import io.kestra.core.models.dashboards.filters.LessThanOrEqualTo;
import io.kestra.core.models.dashboards.filters.NotContains;
import io.kestra.core.models.dashboards.filters.NotEqualTo;
import io.kestra.core.models.dashboards.filters.NotIn;
import io.kestra.core.models.dashboards.filters.Or;

public interface IExecutions extends IData<IExecutions.Fields> {

    default List<AbstractFilter<Fields>> whereWithGlobalFilters(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate, List<AbstractFilter<Fields>> where) {
        List<AbstractFilter<Fields>> updatedWhere = where != null ? new ArrayList<>(where) : new ArrayList<>();

        if (filters != null) {
            List<QueryFilter> namespaceFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
            if (!namespaceFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.NAMESPACE));
                namespaceFilters.forEach(f ->
                {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.NAMESPACE, f.value()));
                });
            }

            List<QueryFilter> labelFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.LABELS)).toList();
            if (!labelFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.LABELS));
                labelFilters.forEach(f ->
                {
                    if (f.value() instanceof Map<?, ?> m) {
                        if (QueryFilter.Op.IN.equals(f.operation())) {
                            updatedWhere.add(Or.<Fields>builder()
                                .field(Fields.LABELS)
                                .values(m.entrySet().stream()
                                    .map(entry -> labelFilter(f.operation(), entry.getKey().toString(), entry.getValue()))
                                    .toList())
                                .build());
                        } else {
                            m.forEach((key, value) -> updatedWhere.add(labelFilter(f.operation(), key.toString(), value)));
                        }
                    } else {
                        updatedWhere.add(labelFilter(f.operation(), null, f.value()));
                    }
                });
            }

            List<QueryFilter> flowFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.FLOW_ID)).toList();
            if (!flowFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.FLOW_ID));
                flowFilters.forEach(f ->
                {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.FLOW_ID, f.value()));
                });
            }

            List<QueryFilter> stateFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.STATE)).toList();
            if (!stateFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.STATE));
                stateFilters.forEach(f ->
                {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.STATE, f.value()));
                });
            }

            List<QueryFilter> scopeFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.SCOPE)).toList();
            if (!scopeFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.SCOPE));
                scopeFilters.forEach(f -> updatedWhere.add(f.toDashboardFilterBuilder(Fields.SCOPE, f.value())));
            }
        }

        if (startDate != null || endDate != null) {
            if (startDate != null) {
                updatedWhere.removeIf(f -> f.getField().equals(Fields.START_DATE));
                updatedWhere.add(GreaterThanOrEqualTo.<Fields> builder().field(Fields.START_DATE).value(startDate.toInstant()).build());
            }
            if (endDate != null) {
                updatedWhere.removeIf(f -> f.getField().equals(Fields.END_DATE));
                updatedWhere.add(LessThanOrEqualTo.<Fields> builder().field(Fields.END_DATE).value(endDate.toInstant()).build());
            }
        }

        return updatedWhere;
    }

    private static AbstractFilter<Fields> labelFilter(QueryFilter.Op operation, String key, Object value) {
        return switch (operation) {
            case EQUALS -> EqualTo.<Fields>builder().field(Fields.LABELS).key(key).value(value).build();
            case NOT_EQUALS -> NotEqualTo.<Fields>builder().field(Fields.LABELS).key(key).value(value).build();
            case IN -> In.<Fields>builder().field(Fields.LABELS).key(key).values(asValues(value)).build();
            case NOT_IN -> NotIn.<Fields>builder().field(Fields.LABELS).key(key).values(asValues(value)).build();
            case CONTAINS -> Contains.<Fields>builder().field(Fields.LABELS).key(key).value(value).build();
            case NOT_CONTAINS -> NotContains.<Fields>builder().field(Fields.LABELS).key(key).value(value).build();
            case IS_NULL -> IsNull.<Fields>builder().field(Fields.LABELS).key(labelKey(key, value)).build();
            case IS_NOT_NULL -> IsNotNull.<Fields>builder().field(Fields.LABELS).key(labelKey(key, value)).build();
            default -> throw new UnsupportedOperationException("Unsupported dashboard label filter operation: %s.".formatted(operation));
        };
    }

    private static String labelKey(String key, Object value) {
        if (key != null) {
            return key;
        }

        if (value != null) {
            return value.toString();
        }

        throw new IllegalArgumentException("Label key is required for dashboard label existence filters.");
    }

    private static List<Object> asValues(Object value) {
        if (value instanceof List<?> values) {
            return new ArrayList<>(values);
        }

        if (value instanceof List<?> values) {
            return new ArrayList<>(values);
        }

        return List.of(value);
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
        TRIGGER_EXECUTION_ID,
        SCOPE
    }
}
