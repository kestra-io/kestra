package io.kestra.jdbc.repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.Order;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface.ChildFilter;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.Enums;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.services.JdbcFilterService;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import lombok.Getter;

public abstract class AbstractJdbcRepository {
    public static final Field<Boolean> DELETED_FIELD = field("deleted", Boolean.class);
    public static final Field<String> TENANT_ID_FIELD = field("tenant_id", String.class);
    public static final Field<String> KEY_FIELD = field("key", String.class);
    public static final Field<Object> VALUE_FIELD = field("value", Object.class);

    protected static final int FETCH_SIZE = 100;

    @Getter
    @Inject
    private SystemFlowsConfiguration systemFlowsConfiguration;

    protected Condition defaultFilter() {
        return DELETED_FIELD.eq(false);
    }

    protected Condition defaultFilter(Boolean allowDeleted) {
        return allowDeleted ? DELETED_FIELD.in(true, false) : DELETED_FIELD.eq(false);
    }

    protected Condition defaultFilter(String tenantId) {
        return this.defaultFilter(tenantId, false);
    }

    protected Condition defaultFilter(String tenantId, boolean allowDeleted) {
        var tenant = buildTenantCondition(tenantId);

        // Always include `deleted` in the query filters as most database optimizers can only use and index if the leftmost columns are used in the query
        return allowDeleted ? tenant.and(DELETED_FIELD.in(true, false)) : tenant.and(DELETED_FIELD.eq(false));
    }

    protected Condition defaultFilterWithNoACL(String tenantId) {
        return defaultFilterWithNoACL(tenantId, false);
    }

    protected Condition defaultFilterWithNoACL(String tenantId, boolean deleted) {
        var tenant = buildTenantCondition(tenantId);

        // Always include `deleted` in the query filters as most database optimizers can only use and index if the leftmost columns are used in the query
        return deleted ? tenant.and(DELETED_FIELD.in(true, false)) : tenant.and(DELETED_FIELD.eq(false));
    }

    protected Condition buildTenantCondition(String tenantId) {
        return tenantId == null ? TENANT_ID_FIELD.isNull() : TENANT_ID_FIELD.eq(tenantId);
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
     * @param jdbcFilterService the service used to apply the filters
     * @param filters the data filter containing the filter conditions
     * @param fieldsMapping a map of field enums to their corresponding database column names
     * @param <F> the type of the fields enum
     * @return the select condition step with the applied filters
     */
    protected <F extends Enum<F>> SelectConditionStep<Record> where(SelectConditionStep<Record> selectConditionStep, JdbcFilterService jdbcFilterService, List<AbstractFilter<F>> filters,
        Map<F, String> fieldsMapping) {
        return jdbcFilterService.addFilters(selectConditionStep, fieldsMapping, filters);
    }

    /**
     * Groups the results of the given select condition step based on the provided descriptors and field mappings.
     * Used in the fetchData() method
     *
     * @param selectConditionStep the select condition step to which the grouping will be applied
     * @param columnsNoDate the data filter containing the column descriptors for grouping
     * @param dateFields the data filter containing the column descriptors for grouping
     * @param fieldsMapping a map of field enums to their corresponding database column names
     * @param <F> the type of the fields enum
     * @return the select having step with the applied grouping
     */
    protected <F extends Enum<F>> SelectHavingStep<Record> groupBy(
        SelectConditionStep<Record> selectConditionStep,
        List<? extends ColumnDescriptor<F>> columnsNoDate,
        List<Field<Date>> dateFields,
        Map<F, String> fieldsMapping) {
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
     * @param descriptors the data filter containing the order by information
     * @param <F> the type of the fields enum
     * @return the select step with the applied ordering
     */
    protected <F extends Enum<F>> SelectSeekStepN<Record> orderBy(SelectHavingStep<Record> selectHavingStep, DataFilter<F, ? extends ColumnDescriptor<F>> descriptors) {
        List<SortField<?>> orderFields = ListUtils.emptyOnNull(descriptors.getOrderBy()).stream()
            .map(orderBy -> {
                Field<?> field = field(orderBy.getColumn());
                return orderBy.getOrder() == Order.ASC ? field.asc() : field.desc();
            })
            .toList();

        return selectHavingStep.orderBy(orderFields);
    }

    /**
     * Fetches the results of the given select step and applies pagination if a pageable object is provided.
     * Used in the fetchData() method
     *
     * @param selectSeekStep the select step to fetch the results from
     * @param pageable the pageable object containing the pagination information
     * @return the list of fetched results
     */
    protected ArrayListTotal<Map<String, Object>> fetchSeekStep(SelectSeekStepN<Record> selectSeekStep, @Nullable Pageable pageable) {

        int totalCount = DSL.using(selectSeekStep.configuration())
            .fetchCount(selectSeekStep);
        var results = (pageable != null && pageable.getSize() != -1 ? selectSeekStep.limit(pageable.getSize()).offset(pageable.getOffset() - pageable.getSize()) : selectSeekStep).fetch()
            .intoMaps();

        return new ArrayListTotal<>(results, totalCount);
    }

    protected <F extends Enum<F>> Field<?> columnToField(ColumnDescriptor<?> column, Map<F, String> fieldsMapping) {
        return column.getField() != null ? field(fieldsMapping.get(column.getField())) : null;
    }

    protected Condition filter(
        List<QueryFilter> filters,
        String dateColumn,
        Resource resource) {
        if (filters == null || filters.isEmpty()) {
            return DSL.noCondition();
        }
        QueryFilter.validateQueryFilters(filters, resource);
        return andOf(filters, dateColumn);
    }

    private Condition andOf(List<QueryFilter> items, String dateColumn) {
        return items.stream()
            .map(f -> toCondition(f, dateColumn))
            .reduce(DSL.noCondition(), Condition::and);
    }

    private Condition orOf(List<QueryFilter> items, String dateColumn) {
        return items.stream()
            .map(f -> toCondition(f, dateColumn))
            .reduce(DSL.noCondition(), Condition::or);
    }

    private Condition toCondition(QueryFilter filter, String dateColumn) {
        if (filter.isLeaf()) {
            return getConditionOnField(filter.field(), filter.value(), filter.operation(), dateColumn);
        }
        return switch (filter.logical()) {
            case AND -> andOf(filter.children(), dateColumn);
            case OR -> orOf(filter.children(), dateColumn);
        };
    }

    /**
     *
     * @param dateColumn the JDBC column name of the logical date to filter on with {@link io.kestra.core.models.QueryFilter.Field#START_DATE} and/or {@link QueryFilter.Field#END_DATE}
     */
    protected final Condition getConditionOnField(
        QueryFilter.Field field,
        Object value,
        QueryFilter.Op operation,
        @Nullable String dateColumn) {
        if (field.equals(QueryFilter.Field.QUERY)) {
            return handleQuery(value, operation);
        }
        // Handling for Field.STATE
        if (field.equals(QueryFilter.Field.STATE)) {

            return generateStateCondition(value, operation);
        }
        // Handle Field.CHILD_FILTER
        if (field.equals(QueryFilter.Field.CHILD_FILTER)) {
            return handleChildFilter(value, operation);
        }
        // Handling for Field.LEVEL
        if (field.equals(QueryFilter.Field.LEVEL)) {
            return handleLevelField(value, operation);
        }
        // Handling for Field.ATTEMPT_NUMBER — integer column, URL value arrives as String
        // and Postgres won't auto-coerce '1' to integer. Parse before binding.
        if (field.equals(QueryFilter.Field.ATTEMPT_NUMBER)) {
            return handleAttemptNumberField(value, operation);
        }

        // Special handling for START_DATE and END_DATE
        if (field == QueryFilter.Field.START_DATE || field == QueryFilter.Field.END_DATE || field == QueryFilter.Field.UPDATED) {
            if (dateColumn == null) {
                throw new InvalidQueryFiltersException("When creating filtering on START_DATE and/or END_DATE, dateColumn is required but was null");
            }
            return getDateCondition(value, operation, dateColumn);
        }

        if (field == QueryFilter.Field.CREATED) {
            return createdCondition(value, operation, dateColumn);
        }

        if (field == QueryFilter.Field.ENABLED) {
            return getEnabledCondition(value, operation);
        }

        if (field == QueryFilter.Field.SUPER_ADMIN) {
            return getSuperAdminCondition(value, operation);
        }

        if (field == QueryFilter.Field.STATUS) {
            return statusCondition(value, operation);
        }
        if (field == QueryFilter.Field.GROUP) {
            return groupCondition(value, operation);
        }

        if (field == QueryFilter.Field.NAME) {
            return nameCondition(value, operation);
        }

        if (field == QueryFilter.Field.TAGS) {
            return tagsCondition(value, operation);
        }

        if (field == QueryFilter.Field.EXPIRATION_DATE) {
            return getDateCondition(value, operation, QueryFilter.Field.EXPIRATION_DATE.name().toLowerCase());
        }

        if (field == QueryFilter.Field.SCOPE) {
            return applyScopeCondition(value, operation);
        }

        if (field.equals(QueryFilter.Field.LABELS)) {
            if (value instanceof Map<?, ?> map) {
                return findLabelCondition(Either.left(map), operation);
            } else if (value instanceof String string) {
                return findLabelCondition(Either.right(string), operation);
            } else {
                throw new InvalidQueryFiltersException("Label field value must be instance of Map or String");
            }
        }

        if (field == QueryFilter.Field.TRIGGER_STATE) {
            return applyTriggerStateCondition(value, operation);
        }

        if (field.equals(QueryFilter.Field.METADATA)) {
            return findMetadataCondition((Map<?, ?>) value, operation);
        }

        if (field == QueryFilter.Field.TYPE) {
            return typeCondition(value, operation);
        }

        if (field == QueryFilter.Field.TAGS) {
            return tagsCondition(value, operation);
        }

        if (field == QueryFilter.Field.RESOURCES) {
            return resourceTypesCondition(value, operation);
        }

        if (field == QueryFilter.Field.DETAILS) {
            return detailsCondition(value, operation);
        }

        if (field == QueryFilter.Field.TAGS) {
            return tagsCondition(value, operation);
        }

        if (field == QueryFilter.Field.LOCKED) {
            return lockedCondition(value, operation);
        }

        if (field == QueryFilter.Field.LAST_TRIGGERED_DATE) {
            return lastTriggeredDateCondition(value, operation);
        }

        if (field == QueryFilter.Field.NEXT_EXECUTION_DATE) {
            return nextExecutionDateCondition(value, operation);
        }

        if (field == QueryFilter.Field.TIME_RANGE) {
            return timeRangeCondition(value, operation);
        }

        return defaultHandlers(field, value, operation);
    }

    protected Condition defaultHandlers(
        QueryFilter.Field field,
        Object value,
        QueryFilter.Op operation) {
        // Convert the field name to lowercase and quote it
        Name columnName = getColumnName(field);

        // Default handling for other fields
        return switch (operation) {
            case EQUALS -> DSL.field(columnName).eq(primitiveOrToString(value));
            case NOT_EQUALS -> DSL.field(columnName).ne(primitiveOrToString(value));
            case GREATER_THAN -> DSL.field(columnName).greaterThan(value);
            case LESS_THAN -> DSL.field(columnName).lessThan(value);
            case IN -> DSL.field(columnName).in(ListUtils.convertToListString(value));
            case NOT_IN -> DSL.field(columnName).notIn(ListUtils.convertToListString(value));
            case STARTS_WITH -> {
                String s = requireStringValue(value, "STARTS_WITH");
                yield DSL.field(columnName).like(s + "%");
            }
            case ENDS_WITH -> {
                String s = requireStringValue(value, "ENDS_WITH");
                yield DSL.field(columnName).like("%" + s);
            }
            case CONTAINS -> {
                String s = requireStringValue(value, "CONTAINS");
                yield DSL.field(columnName).like("%" + s + "%");
            }
            case REGEX -> {
                String s = requireStringValue(value, "REGEX");
                yield DSL.field(columnName).likeRegex(s);
            }
            case PREFIX -> {
                String s = requireStringValue(value, "PREFIX");
                yield DSL.field(columnName).eq(s)
                    .or(DSL.field(columnName).startsWith(s + "."));
            }
            default -> throw new InvalidQueryFiltersException("Unsupported operation: " + operation);
        };
    }

    private static String requireStringValue(Object value, String operationName) {
        if (value == null) {
            throw new InvalidQueryFiltersException(operationName + " operation requires a non-null string value");
        }
        if (value instanceof List<?>) {
            throw new InvalidQueryFiltersException(operationName + " operation requires a string value, got a List");
        }
        Object converted = primitiveOrToString(value);
        return converted == null ? null : converted.toString();
    }

    private Condition getDateCondition(Object value, Op operation, String dateColumn) {
        OffsetDateTime dateTime = (value instanceof ZonedDateTime)
            ? ((ZonedDateTime) value).toOffsetDateTime()
            : ZonedDateTime.parse(value.toString()).toOffsetDateTime();
        return applyDateCondition(dateTime, operation, dateColumn);
    }

    protected static Object primitiveOrToString(Object o) {
        if (o == null)
            return null;

        if (
            o instanceof Boolean
                || o instanceof Number
                || o instanceof Character
                || o instanceof String
        ) {
            return o;
        }

        return o.toString();
    }

    protected Name getColumnName(QueryFilter.Field field) {
        return DSL.quotedName(field.name().toLowerCase());
    }

    protected Condition findQueryCondition(String query) {
        throw new InvalidQueryFiltersException("Unsupported operation: ");
    }

    public Condition findLabelCondition(Either<Map<?, ?>, String> value, QueryFilter.Op operation) {
        throw new InvalidQueryFiltersException("Unsupported operation: " + operation);
    }

    protected Condition findMetadataCondition(Map<?, ?> metadata, QueryFilter.Op operation) {
        throw new InvalidQueryFiltersException("Unsupported operation: " + operation);
    }

    protected Condition getEnabledCondition(Object value, Op operation) {
        return defaultHandlers(QueryFilter.Field.ENABLED, value, operation);
    }

    protected Condition getSuperAdminCondition(Object value, Op operation) {
        throw new InvalidQueryFiltersException("getSuperAdminCondition must be overridden for JSONB-backed superAdmin field");
    }

    protected Condition tagsCondition(Object value, QueryFilter.Op operation) {
        return defaultHandlers(QueryFilter.Field.TAGS, value, operation);
    }

    // Generate the condition for Field.STATE
    @SuppressWarnings("unchecked")
    protected Condition generateStateCondition(Object value, QueryFilter.Op operation) {
        List<State.Type> stateList = switch (value) {
            case List<?> list when !list.isEmpty() && list.getFirst() instanceof State.Type -> (List<State.Type>) list;
            case List<?> list -> list.stream().map(item -> State.Type.valueOf(item.toString())).toList();
            case State.Type state -> List.of(state);
            case String state -> List.of(State.Type.valueOf(state));
            default ->
                throw new InvalidQueryFiltersException("Field 'state' requires a State.Type or List<State.Type> value");
        };

        return switch (operation) {
            case IN, EQUALS -> statesFilter(stateList);
            case NOT_IN, NOT_EQUALS -> DSL.not(statesFilter(stateList));
            default -> throw new InvalidQueryFiltersException("Unsupported operation for State.Type: " + operation);
        };
    }

    protected Condition lockedCondition(Object value, QueryFilter.Op operation) {
        throw new InvalidQueryFiltersException("Unsupported field: LOCKED");
    }

    protected Condition lastTriggeredDateCondition(Object value, QueryFilter.Op operation) {
        throw new InvalidQueryFiltersException("Unsupported field: LAST_TRIGGERED_DATE");
    }

    protected Condition nextExecutionDateCondition(Object value, QueryFilter.Op operation) {
        throw new InvalidQueryFiltersException("Unsupported field: NEXT_EXECUTION_DATE");
    }

    protected Condition timeRangeCondition(Object value, QueryFilter.Op operation) {
        throw new InvalidQueryFiltersException("Unsupported field: TIME_RANGE");
    }

    protected Condition createdCondition(Object value, QueryFilter.Op operation, @Nullable String dateColumn) {
        if (dateColumn == null) {
            throw new InvalidQueryFiltersException("When filtering on CREATED, dateColumn is required but was null");
        }
        return getDateCondition(value, operation, dateColumn);
    }

    protected Condition statusCondition(Object value, QueryFilter.Op operation) {
        return defaultHandlers(QueryFilter.Field.STATUS, value, operation);
    }

    protected Condition groupCondition(Object value, QueryFilter.Op operation) {
        throw new InvalidQueryFiltersException("Unsupported operation: " + operation);
    }

    protected Condition nameCondition(Object value, QueryFilter.Op operation) {
        return defaultHandlers(QueryFilter.Field.NAME, value, operation);
    }

    protected Condition typeCondition(Object value, QueryFilter.Op operation) {
        return defaultHandlers(QueryFilter.Field.TYPE, value, operation);
    }

    protected Condition resourceTypesCondition(Object value, QueryFilter.Op operation) {
        return defaultHandlers(QueryFilter.Field.RESOURCES, value, operation);
    }

    protected Condition detailsCondition(Object value, QueryFilter.Op operation) {
        return defaultHandlers(QueryFilter.Field.DETAILS, value, operation);
    }

    protected Condition statesFilter(List<State.Type> state) {
        return field("state_current")
            .in(state.stream().map(Enum::name).toList());
    }

    private Condition handleQuery(Object value, QueryFilter.Op operation) {
        Condition condition = findQueryCondition(value.toString());

        return switch (operation) {
            case EQUALS -> condition;
            case NOT_EQUALS -> condition.not();
            default -> throw new InvalidQueryFiltersException("Unsupported operation for QUERY field: " + operation);
        };
    }

    // Handle CHILD_FILTER field logic
    private Condition handleChildFilter(Object value, Op operation) {
        ChildFilter childFilter = (value instanceof String val) ? ChildFilter.valueOf(val) : (ChildFilter) value;

        return switch (operation) {
            case EQUALS -> childFilter.equals(ChildFilter.CHILD) ? field("trigger_execution_id").isNotNull() : field("trigger_execution_id").isNull();
            case NOT_EQUALS -> childFilter.equals(ChildFilter.CHILD) ? field("trigger_execution_id").isNull() : field("trigger_execution_id").isNotNull();
            default -> throw new InvalidQueryFiltersException("Unsupported operation for child filter field: " + operation);
        };
    }

    private Condition handleLevelField(Object value, QueryFilter.Op operation) {
        Level level = value instanceof Level ? (Level) value : Level.valueOf((String) value);

        return switch (operation) {
            case GREATER_THAN_OR_EQUAL_TO -> levelsCondition(LogEntry.findLevelsByMin(level));
            case LESS_THAN_OR_EQUAL_TO -> levelsCondition(LogEntry.findLevelsByMax(level));
            default -> throw new InvalidQueryFiltersException(
                "Unsupported operation for LEVEL: " + operation
            );
        };
    }

    protected Condition levelsCondition(List<Level> levels) {
        return field("level").in(levels.stream().map(level -> level.name()).toList());
    }

    protected Condition handleAttemptNumberField(Object value, QueryFilter.Op operation) {
        Name columnName = getColumnName(QueryFilter.Field.ATTEMPT_NUMBER);
        return switch (operation) {
            case EQUALS -> DSL.field(columnName).eq(toInteger(value));
            case NOT_EQUALS -> DSL.field(columnName).ne(toInteger(value));
            case IN -> DSL.field(columnName).in(toIntegerList(value));
            case NOT_IN -> DSL.field(columnName).notIn(toIntegerList(value));
            default -> throw new InvalidQueryFiltersException(
                "Unsupported operation for ATTEMPT_NUMBER: " + operation
            );
        };
    }

    // ToDo: We should create reusable classes for type conversion
    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static List<Integer> toIntegerList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(AbstractJdbcRepository::toInteger).toList();
        }
        return List.of(toInteger(value));
    }

    Condition applyDateCondition(OffsetDateTime dateTime, QueryFilter.Op operation, String fieldName) {
        return switch (operation) {
            case LESS_THAN -> field(fieldName).lessThan(dateTime);
            case LESS_THAN_OR_EQUAL_TO -> field(fieldName).lessOrEqual(dateTime);
            case GREATER_THAN -> field(fieldName).greaterThan(dateTime);
            case GREATER_THAN_OR_EQUAL_TO -> field(fieldName).greaterOrEqual(dateTime);
            case EQUALS -> field(fieldName).eq(dateTime);
            case NOT_EQUALS -> field(fieldName).ne(dateTime);
            default ->
                throw new InvalidQueryFiltersException("Unsupported operation for date condition: " + operation);
        };
    }

    private Condition applyScopeCondition(Object value, QueryFilter.Op operation) {
        List<FlowScope> flowScopes = Enums.fromList(value, FlowScope.class);
        String systemNamespace = this.systemFlowsConfiguration.namespace();

        return switch (operation) {
            case EQUALS, NOT_EQUALS -> {
                if (flowScopes.size() > 1) {
                    throw new InvalidQueryFiltersException("Only one scope can be used at a time with " + operation);
                }
                FlowScope scope = flowScopes.getFirst();
                yield switch (operation) {
                    case EQUALS -> FlowScope.USER.equals(scope) ? field("namespace").ne(systemNamespace) : field("namespace").eq(systemNamespace);
                    case NOT_EQUALS -> FlowScope.USER.equals(scope) ? field("namespace").eq(systemNamespace) : field("namespace").ne(systemNamespace);
                    default -> throw new InvalidQueryFiltersException("Unreachable");
                };
            }
            case IN -> {
                boolean includesUser = flowScopes.contains(FlowScope.USER);
                boolean includesSystem = flowScopes.contains(FlowScope.SYSTEM);
                if (includesUser && includesSystem) yield DSL.noCondition();
                else if (includesUser) yield field("namespace").ne(systemNamespace);
                else yield field("namespace").eq(systemNamespace);
            }
            case NOT_IN -> {
                boolean excludesUser = flowScopes.contains(FlowScope.USER);
                boolean excludesSystem = flowScopes.contains(FlowScope.SYSTEM);
                if (excludesUser && excludesSystem) yield DSL.falseCondition();
                else if (excludesUser) yield field("namespace").eq(systemNamespace);
                else yield field("namespace").ne(systemNamespace);
            }
            default -> throw new InvalidQueryFiltersException("Unsupported operation for SCOPE: " + operation);
        };
    }

    private Condition applyTriggerStateCondition(Object value, QueryFilter.Op operation) {
        String triggerState = value.toString();
        Boolean isDisabled = switch (triggerState) {
            case "disabled" -> true;
            case "enabled" -> false;
            default -> null;
        };
        if (isDisabled == null) {
            return DSL.noCondition();
        }
        return switch (operation) {
            case EQUALS -> field("disabled").eq(isDisabled);
            case NOT_EQUALS -> field("disabled").ne(isDisabled);
            default -> throw new InvalidQueryFiltersException("Unsupported operation for Trigger State: " + operation);
        };
    }

    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        throw new UnsupportedOperationException("formatDateField() not implemented");
    }

    protected <F extends Enum<F>> List<Field<Date>> generateDateFields(
        DataFilter<F, ? extends ColumnDescriptor<F>> descriptors,
        Map<F, String> fieldsMapping,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Set<F> dateFields,
        @Nullable DateUtils.GroupType groupType) {
        return descriptors.getColumns().entrySet().stream()
            .filter(entry -> entry.getValue().getAgg() == null && dateFields.contains(entry.getValue().getField()))
            .map(entry -> {
                Duration duration = Duration.between(startDate, endDate == null ? ZonedDateTime.now() : endDate);
                DateUtils.GroupType effectiveGroupType = groupType != null ? groupType : DateUtils.groupByType(duration);
                return formatDateField(fieldsMapping.get(entry.getValue().getField()), effectiveGroupType).as(entry.getKey());
            })
            .toList();

    }
}