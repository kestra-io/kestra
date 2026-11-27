package io.kestra.plugin.core.dashboard.data;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
import io.kestra.core.utils.ListUtils;

public interface IExecutions extends IData<IExecutions.Fields> {
    Set<Fields> DURATION_FIELDS = Set.of(Fields.DURATION);

    @Override
    default Set<Fields> durationFields() {
        return DURATION_FIELDS;
    }

    default List<AbstractFilter<Fields>> whereWithGlobalFilters(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate, List<AbstractFilter<Fields>> where) {
        List<AbstractFilter<Fields>> updatedWhere = where != null ? new ArrayList<>(where) : new ArrayList<>();

        if (filters != null) {
            List<QueryFilter> leaves = filters.stream().filter(QueryFilter::isLeaf).toList();

            List<QueryFilter> namespaceFilters = leaves.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
            if (!namespaceFilters.isEmpty()) {
                namespaceFilters.forEach(f ->
                {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.NAMESPACE, f.value()));
                });
            }

            List<QueryFilter> labelFilters = leaves.stream().filter(f -> f.field().equals(QueryFilter.Field.LABELS)).toList();
            if (!labelFilters.isEmpty()) {
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

            List<QueryFilter> flowFilters = leaves.stream().filter(f -> f.field().equals(QueryFilter.Field.FLOW_ID)).toList();
            if (!flowFilters.isEmpty()) {
                flowFilters.forEach(f ->
                {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.FLOW_ID, f.value()));
                });
            }

            List<QueryFilter> kindFilters = leaves.stream().filter(f -> f.field().equals(QueryFilter.Field.KIND)).toList();
            if (!kindFilters.isEmpty()) {
                kindFilters.forEach(f -> updatedWhere.add(kindFilter(f)));
            }

            filters.stream().filter(QueryFilter::isNode).forEach(group ->
            {
                if (QueryFilter.Logical.AND == group.logical()) {
                    updatedWhere.addAll(whereWithGlobalFilters(group.children(), null, null, null));
                } else {
                    orFilter(group).ifPresent(updatedWhere::add);
                }
            });

            List<QueryFilter> stateFilters = leaves.stream().filter(f -> f.field().equals(QueryFilter.Field.STATE)).toList();
            if (!stateFilters.isEmpty()) {
                stateFilters.forEach(f ->
                {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.STATE, f.value()));
                });
            }

            List<QueryFilter> scopeFilters = leaves.stream().filter(f -> f.field().equals(QueryFilter.Field.SCOPE)).toList();
            if (!scopeFilters.isEmpty()) {
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

    /** Also reports the fields of the OR groups {@link #whereWithGlobalFilters} cannot map, since it leaves those groups out. */
    @Override
    default List<QueryFilter.Field> ignoredGlobalFilterFields(List<QueryFilter> filters) {
        return Stream.concat(IData.super.ignoredGlobalFilterFields(filters).stream(), unmappedGroupFields(ListUtils.emptyOnNull(filters)))
            .filter(field -> !TIME_FILTER_FIELDS.contains(field))
            .distinct()
            .toList();
    }

    /** An OR group maps to an {@link Or} filter only when each branch maps to exactly one filter on the same field, since repositories read the field of an {@link Or} from its root. */
    private Optional<AbstractFilter<Fields>> orFilter(QueryFilter group) {
        List<List<AbstractFilter<Fields>>> branches = group.children().stream()
            .map(child -> whereWithGlobalFilters(List.of(child), null, null, null))
            .toList();
        if (branches.stream().anyMatch(branch -> branch.size() != 1)) {
            return Optional.empty();
        }
        List<AbstractFilter<Fields>> values = branches.stream().map(List::getFirst).toList();
        if (values.stream().map(AbstractFilter::getField).distinct().count() != 1) {
            return Optional.empty();
        }
        return Optional.of(Or.<Fields>builder().field(values.getFirst().getField()).values(values).build());
    }

    private Stream<QueryFilter.Field> unmappedGroupFields(List<QueryFilter> filters) {
        return filters.stream()
            .filter(QueryFilter::isNode)
            .flatMap(group -> QueryFilter.Logical.AND == group.logical()
                ? unmappedGroupFields(group.children())
                : orFilter(group).isPresent() ? Stream.empty() : IData.leafFields(group.children()));
    }

    private static AbstractFilter<Fields> kindFilter(QueryFilter filter) {
        return filter.toDashboardFilterBuilder(Fields.KIND, kindFilterValue(filter.value()));
    }

    private static Object kindFilterValue(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().map(Object::toString).toList();
        }
        return value == null ? null : value.toString();
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

        return List.of(value);
    }

    enum Fields {
        ID,
        NAMESPACE,
        FLOW_ID,
        FLOW_REVISION,
        KIND,
        STATE,
        DURATION,
        LABELS,
        START_DATE,
        END_DATE,
        TRIGGER_EXECUTION_ID,
        SCOPE
    }
}
