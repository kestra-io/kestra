package io.kestra.jdbc.repository;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.Order;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class AbstractJdbcRepository {

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
}
