package io.kestra.jdbc.repository;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.Order;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface.ChildFilter;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import lombok.Getter;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.*;

import static io.kestra.core.utils.NamespaceUtils.SYSTEM_FLOWS_DEFAULT_NAMESPACE;

public abstract class AbstractJdbcRepository {

    @Getter
    @Value("${kestra.system-flows.namespace:" + SYSTEM_FLOWS_DEFAULT_NAMESPACE + "}")
    private String systemFlowNamespace;

    private static final Field<String> NAMESPACE_FIELD = field("namespace", String.class);

    protected Condition defaultFilter() {
        return field("deleted", Boolean.class).eq(false);
    }

    protected Condition defaultFilter(Boolean allowDeleted) {
        return allowDeleted ? DSL.trueCondition() : field("deleted", Boolean.class).eq(false);
    }

    protected Condition defaultFilter(String tenantId) {
        return this.defaultFilter(tenantId, false);
    }

    protected Condition defaultFilter(String tenantId, boolean allowDeleted) {
        var tenant = buildTenantCondition(tenantId);
        return allowDeleted ? tenant : tenant.and(field("deleted", Boolean.class).eq(false));
    }

    protected Condition defaultFilterWithNoACL(String tenantId) {
        return defaultFilterWithNoACL(tenantId, false);
    }

    protected Condition defaultFilterWithNoACL(String tenantId, boolean deleted) {
        var tenant = buildTenantCondition(tenantId);
        return deleted ? tenant : tenant.and(field("deleted", Boolean.class).eq(false));
    }

    protected Condition buildTenantCondition(String tenantId) {
        return tenantId == null ? field("tenant_id").isNull() : field("tenant_id").eq(tenantId);
    }

    public static Field<Object> field(String name) {
        return DSL.field(DSL.quotedName(name));
    }

    public static <T> Field<T> field(String name, Class<T> cls) {
        return DSL.field(DSL.quotedName(name), cls);
    }

    protected List<Field<?>> groupByFields(Duration duration) {
        return groupByFields(duration, null, null);
    }

    protected List<Field<?>> groupByFields(Duration duration, boolean withAs) {
        return groupByFields(duration, null, null, withAs);
    }

    protected Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        return DSL.week(timestampField);
    }

    protected List<Field<?>> groupByFields(Duration duration, @Nullable String dateField, @Nullable DateUtils.GroupType groupBy) {
        return groupByFields(duration, dateField, groupBy, true);
    }

    protected List<Field<?>> groupByFields(Duration duration, @Nullable String dateField, @Nullable DateUtils.GroupType groupBy, boolean withAs) {
        String field = dateField != null ? dateField : "timestamp";
        Field<Integer> month = withAs ? DSL.month(DSL.timestamp(field(field, Date.class))).as("month") : DSL.month(DSL.timestamp(field(field, Date.class)));
        Field<Integer> year = withAs ? DSL.year(DSL.timestamp(field(field, Date.class))).as("year") : DSL.year(DSL.timestamp(field(field, Date.class)));
        Field<Integer> day = withAs ? DSL.day(DSL.timestamp(field(field, Date.class))).as("day") : DSL.day(DSL.timestamp(field(field, Date.class)));
        Field<Integer> week = withAs ? weekFromTimestamp(DSL.timestamp(field(field, Date.class))).as("week") : weekFromTimestamp(DSL.timestamp(field(field, Date.class)));
        Field<Integer> hour = withAs ? DSL.hour(DSL.timestamp(field(field, Date.class))).as("hour") : DSL.hour(DSL.timestamp(field(field, Date.class)));
        Field<Integer> minute = withAs ? DSL.minute(DSL.timestamp(field(field, Date.class))).as("minute") : DSL.minute(DSL.timestamp(field(field, Date.class)));

        if (groupBy == DateUtils.GroupType.MONTH || duration.toDays() > DateUtils.GroupValue.MONTH.getValue()) {
            return List.of(year, month);
        } else if (groupBy == DateUtils.GroupType.WEEK || duration.toDays() > DateUtils.GroupValue.WEEK.getValue()) {
            return List.of(year, week);
        } else if (groupBy == DateUtils.GroupType.DAY || duration.toDays() > DateUtils.GroupValue.DAY.getValue()) {
            return List.of(year, month, day);
        } else if (groupBy == DateUtils.GroupType.HOUR || duration.toHours() > DateUtils.GroupValue.HOUR.getValue()) {
            return List.of(year, month, day, hour);
        } else {
            return List.of(year, month, day, hour, minute);
        }
    }

    protected <F extends Enum<F>> SelectConditionStep<Record> select(
        DSLContext context,
        JdbcFilterService filterService,
        Map<String, ? extends ColumnDescriptor<F>> descriptors,
        List<Field<Date>> dateFields,
        Map<F, String> fieldsMapping,
        Table<Record> table,
        String tenantId) {

        return context
            .select(
                Stream.concat(
                    descriptors.entrySet().stream()
                        .map(entry -> {
                            ColumnDescriptor<F> col = entry.getValue();
                            String key = entry.getKey();
                            Field<?> field = columnToField(col, fieldsMapping);
                            if (col.getAgg() != null) {
                                field = filterService.buildAggregation(field, col.getAgg());
                            }
                            return field.as(key);
                        }),
                    dateFields.stream()
                ).toList()
            )
            .from(table)
            .where(this.defaultFilter(tenantId));
    }

    /**
     * Applies the filters from the provided descriptors to the given select condition step.
     * Used in the fetchData() method
     *
     * @param selectConditionStep the select condition step to which the filters will be applied
     * @param jdbcFilterService   the service used to apply the filters
     * @param descriptors         the data filter containing the filter conditions
     * @param fieldsMapping       a map of field enums to their corresponding database column names
     * @param <F>                 the type of the fields enum
     * @return the select condition step with the applied filters
     */
    protected <F extends Enum<F>> SelectConditionStep<Record> where(SelectConditionStep<Record> selectConditionStep, JdbcFilterService jdbcFilterService, DataFilter<F, ? extends ColumnDescriptor<F>> descriptors, Map<F, String> fieldsMapping) {
        return jdbcFilterService.addFilters(selectConditionStep, fieldsMapping, descriptors.getWhere());
    }

    /**
     * Groups the results of the given select condition step based on the provided descriptors and field mappings.
     * Used in the fetchData() method
     *
     * @param selectConditionStep the select condition step to which the grouping will be applied
     * @param columnsNoDate       the data filter containing the column descriptors for grouping
     * @param dateFields          the data filter containing the column descriptors for grouping
     * @param fieldsMapping       a map of field enums to their corresponding database column names
     * @param <F>                 the type of the fields enum
     * @return the select having step with the applied grouping
     */
    protected <F extends Enum<F>> SelectHavingStep<Record> groupBy(
        SelectConditionStep<Record> selectConditionStep,
        List<? extends ColumnDescriptor<F>> columnsNoDate,
        List<Field<Date>> dateFields,
        Map<F, String> fieldsMapping
    ) {
        return selectConditionStep.groupBy(
            Stream.concat(
                columnsNoDate.stream()
                    .filter(col -> col.getAgg() == null)
                    .map(col -> field(fieldsMapping.get(col.getField()))),
                dateFields.stream()
            ).toList()
        );
    }


    /**
     * Applies ordering to the given select step based on the provided descriptors.
     * Used in the fetchData() method
     *
     * @param selectHavingStep the select step to which the ordering will be applied
     * @param descriptors      the data filter containing the order by information
     * @param <F>              the type of the fields enum
     * @return the select step with the applied ordering
     */
    protected <F extends Enum<F>> SelectSeekStepN<Record> orderBy(SelectHavingStep<Record> selectHavingStep, DataFilter<F, ? extends ColumnDescriptor<F>> descriptors) {
        List<SortField<?>> orderFields = new ArrayList<>();
        if (!ListUtils.isEmpty(descriptors.getOrderBy())) {
            orderFields = descriptors.getOrderBy().stream()
                .map(orderBy -> {
                    Field<?> field = field(orderBy.getColumn());
                    return orderBy.getOrder() == Order.ASC ? field.asc() : field.desc();
                })
                .toList();

        }

        return selectHavingStep.orderBy(orderFields);
    }

    /**
     * Fetches the results of the given select step and applies pagination if a pageable object is provided.
     * Used in the fetchData() method
     *
     * @param selectSeekStep the select step to fetch the results from
     * @param pageable       the pageable object containing the pagination information
     * @return the list of fetched results
     */
    protected List<Map<String, Object>> fetchSeekStep(SelectSeekStepN<Record> selectSeekStep, @Nullable Pageable pageable) {

        return (pageable != null && pageable.getSize() != -1 ?
            selectSeekStep.limit(pageable.getSize()).offset(pageable.getOffset() - pageable.getSize()) :
            selectSeekStep
        ).fetch()
            .intoMaps();
    }

    protected <F extends Enum<F>> Field<?> columnToField(ColumnDescriptor<?> column, Map<F, String> fieldsMapping) {
        return column.getField() != null ? field(fieldsMapping.get(column.getField())) : null;
    }

    protected <T extends Record> SelectConditionStep<T> filter(
        SelectConditionStep<T> select,
        List<QueryFilter> filters,
        String dateColumn
    ) {
        if (filters != null) {
            for (QueryFilter filter : filters) {
                QueryFilter.Field field = filter.field();
                QueryFilter.Op operation = filter.operation();
                Object value = filter.value();
                select = getConditionOnField(select, field, value, operation, dateColumn);
            }
        }
        return select;
    }

    protected  <T extends Record> SelectConditionStep<T> getConditionOnField(
        SelectConditionStep<T> select,
        QueryFilter.Field field,
        Object value,
        QueryFilter.Op operation,
        String dateColumn)
    {
        if(field.equals(QueryFilter.Field.QUERY)) {
            return select;
        }
        // Handling for Field.STATE
        if (field.equals(QueryFilter.Field.STATE)) {

            return select.and(generateStateCondition(value, operation));
        }
        // Handle Field.CHILD_FILTER
        if (field.equals(QueryFilter.Field.CHILD_FILTER)) {
            return handleChildFilter(select, value);
        }
        // Handling for Field.MIN_LEVEL
        if (field.equals(QueryFilter.Field.MIN_LEVEL)) {
            return handleMinLevelField(select, value, operation);
        }

        // Special handling for START_DATE and END_DATE
        if (field == QueryFilter.Field.START_DATE || field == QueryFilter.Field.END_DATE) {
            OffsetDateTime dateTime = (value instanceof ZonedDateTime)
                ? ((ZonedDateTime) value).toOffsetDateTime()
                : ZonedDateTime.parse(value.toString()).toOffsetDateTime();
            return applyDateCondition(select, dateTime, operation, dateColumn);
        }

        if (field == QueryFilter.Field.SCOPE) {
            return applyScopeCondition(select, value, operation);
        }
        if (field == QueryFilter.Field.NAMESPACE) {
            return applyNamespaceCondition(select, value, operation);
        }

        // Convert the field name to lowercase and quote it
        Name columnName = DSL.quotedName(field.name().toLowerCase());

        // Default handling for other fields
        switch (operation) {
            case EQUALS -> select = select.and(DSL.field(columnName).eq(value));
            case NOT_EQUALS -> select = select.and(DSL.field(columnName).ne(value));
            case GREATER_THAN -> select = select.and(DSL.field(columnName).greaterThan(value));
            case LESS_THAN -> select = select.and(DSL.field(columnName).lessThan(value));
            case IN -> {
                if (value instanceof Collection<?>) {
                    select = select.and(DSL.field(columnName).in((Collection<?>) value));
                } else {
                    throw new IllegalArgumentException("IN operation requires a collection as value");
                }
            }
            case NOT_IN -> {
                if (value instanceof Collection<?>) {
                    select = select.and(DSL.field(columnName).notIn((Collection<?>) value));
                } else {
                    throw new IllegalArgumentException("NOT_IN operation requires a collection as value");
                }
            }
            case STARTS_WITH -> select = select.and(DSL.field(columnName).like(value + "%"));

            case ENDS_WITH -> select = select.and(DSL.field(columnName).like("%" + value));
            case CONTAINS -> select = select.and(DSL.field(columnName).like("%" + value + "%"));
            case REGEX -> select = select.and(DSL.field(columnName).likeRegex((String) value));
            default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
        }
        return select;
    }

    private <T extends Record> SelectConditionStep<T> applyNamespaceCondition(SelectConditionStep<T> select, Object value, QueryFilter.Op operation) {

         switch (operation) {
            case EQUALS -> select = select.and(NAMESPACE_FIELD.eq((String) value));
            case NOT_EQUALS -> select = select.and(NAMESPACE_FIELD.ne((String) value));
            case CONTAINS -> select = select.and(NAMESPACE_FIELD.eq((String) value)
                .or(NAMESPACE_FIELD.like( value + ".%"))
                .or(NAMESPACE_FIELD.like("%." + value)))
            ;
            case STARTS_WITH -> select = select.and(NAMESPACE_FIELD.like(value + ".%")
                .or(NAMESPACE_FIELD.eq((String) value)));
            case ENDS_WITH -> select = select.and(NAMESPACE_FIELD.like("%." + value));
            case IN ->  {
                if (value instanceof Collection<?> values) {
                select = select.and(NAMESPACE_FIELD.in(values.stream()
                    .map(String.class::cast)
                    .toList()));
                }
             }
             case NOT_IN ->  {
                 if (value instanceof Collection<?> values) {
                     select = select.and(NAMESPACE_FIELD.notIn(values.stream()
                         .map(String.class::cast)
                         .toList()));
                 }
             }
             default ->
                throw new UnsupportedOperationException("Unsupported operation '%s' for field 'namespace'.".formatted(operation));
        }
         return select;
    }

    // Generate the condition for Field.STATE
    @SuppressWarnings("unchecked")
    private Condition generateStateCondition(Object value, QueryFilter.Op operation) {
        List<State.Type> stateList = switch (value) {
            case List<?> list when !list.isEmpty() && list.getFirst() instanceof State.Type ->
                (List<State.Type>) list;
            case List<?> list ->
                list.stream().map(item -> State.Type.valueOf(item.toString())).toList();
            case State.Type state -> List.of(state);
            case String state -> List.of(State.Type.valueOf(state));
            default -> throw new IllegalArgumentException("Field 'state' requires a State.Type or List<State.Type> value");
        };

        return switch (operation) {
            case IN, EQUALS -> statesFilter(stateList);
            case NOT_IN, NOT_EQUALS -> DSL.not(statesFilter(stateList));
            default -> throw new IllegalArgumentException("Unsupported operation for State.Type: " + operation);
        };
    }
    protected Condition statesFilter(List<State.Type> state) {
        return DSL.field(DSL.quotedName("state_current"))
            .in(state.stream().map(Enum::name).toList());
    }

    // Handle CHILD_FILTER field logic
    private <T extends Record> SelectConditionStep<T> handleChildFilter(SelectConditionStep<T> select, Object value) {
        ChildFilter childFilter = (value instanceof String val)? ChildFilter.valueOf(val) : (ChildFilter) value;

        return switch (childFilter) {
            case CHILD -> select.and(DSL.field(DSL.quotedName("trigger_execution_id")).isNotNull());
            case MAIN -> select.and(DSL.field(DSL.quotedName("trigger_execution_id")).isNull());
        };
    }

    private <T extends Record> SelectConditionStep<T> handleMinLevelField(
        SelectConditionStep<T> select,
        Object value,
        QueryFilter.Op operation
    ) {
        Level minLevel = value instanceof Level ? (Level) value : Level.valueOf((String) value);

        switch (operation) {
            case EQUALS -> select = select.and(minLevelCondition(minLevel));
            case NOT_EQUALS -> select = select.and(minLevelCondition(minLevel).not());
            default -> throw new UnsupportedOperationException(
                "Unsupported operation for MIN_LEVEL: " + operation
            );
        }
        return select;
    }
    private Condition minLevelCondition(Level minLevel) {
        return levelsCondition(LogEntry.findLevelsByMin(minLevel));
    }

    protected Condition levelsCondition(List<Level> levels) {
        return field("level").in(levels.stream().map(level -> level.name()).toList());
    }

    private <T extends Record> SelectConditionStep<T> applyDateCondition(
        SelectConditionStep<T> select, OffsetDateTime dateTime, QueryFilter.Op operation,String fieldName
    ) {
        switch (operation) {
            case LESS_THAN -> select = select.and(DSL.field(fieldName).lessThan(dateTime));
            case GREATER_THAN -> select = select.and(DSL.field(fieldName).greaterThan(dateTime));
            case EQUALS -> select = select.and(DSL.field(fieldName).eq(dateTime));
            case NOT_EQUALS -> select = select.and(DSL.field(fieldName).ne(dateTime));
            default -> throw new UnsupportedOperationException("Unsupported operation for date condition: " + operation);
        }
        return select;
    }
    protected static String getQuery(List<QueryFilter> filters) {
        if (filters == null || filters.isEmpty()) return null;
        return filters.stream()
            .filter(filter -> filter.field() == QueryFilter.Field.QUERY)
            .map(filter -> filter.value().toString())
            .findFirst()
            .orElse(null);
    }
    private <T extends Record> SelectConditionStep<T> applyScopeCondition(
        SelectConditionStep<T> select, Object value, QueryFilter.Op operation) {

        if (!(value instanceof List<?> scopeValues)) {
            throw new IllegalArgumentException("Invalid value for SCOPE filtering");
        }

        List<FlowScope> validScopes = Arrays.stream(FlowScope.values()).toList();
        if (!validScopes.containsAll(scopeValues)) {
            throw new IllegalArgumentException("Scope values must be a subset of FlowScope");
        }
        if (operation != QueryFilter.Op.EQUALS && operation != QueryFilter.Op.NOT_EQUALS) {
            throw new UnsupportedOperationException("Unsupported operation for SCOPE: " + operation);
        }

        boolean isEqualsOperation = (operation == QueryFilter.Op.EQUALS);
        String systemNamespace = this.getSystemFlowNamespace();

        if (scopeValues.contains(FlowScope.USER)) {
            Condition userCondition = isEqualsOperation
                ? field("namespace").ne(systemNamespace)
                : field("namespace").eq(systemNamespace);
            select = select.and(userCondition);
        } else if (scopeValues.contains(FlowScope.SYSTEM)) {
            Condition systemCondition = isEqualsOperation
                ? field("namespace").eq(systemNamespace)
                : field("namespace").ne(systemNamespace);
            select = select.and(systemCondition);
        }

        return select;
    }
}
