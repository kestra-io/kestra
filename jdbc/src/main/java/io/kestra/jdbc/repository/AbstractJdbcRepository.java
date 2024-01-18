package io.kestra.jdbc.repository;

import io.kestra.core.utils.DateUtils;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Date;
import java.util.List;

@Singleton
public abstract class AbstractJdbcRepository {
    protected Condition defaultFilter() {
        return field("deleted", Boolean.class).eq(false);
    }

    protected Condition defaultFilter(String tenantId) {
        var tenant = buildTenantCondition(tenantId) ;
        return tenant.and(field("deleted", Boolean.class).eq(false));
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

    protected Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        return DSL.week(timestampField);
    }

    protected List<Field<?>> groupByFields(Duration duration, @Nullable String dateField, @Nullable DateUtils.GroupType groupBy) {
        String field = dateField != null ? dateField : "timestamp";
        Field<Integer> month = DSL.month(DSL.timestamp(field(field, Date.class))).as("month");
        Field<Integer> year = DSL.year(DSL.timestamp(field(field, Date.class))).as("year");
        Field<Integer> day = DSL.day(DSL.timestamp(field(field, Date.class))).as("day");
        Field<Integer> week = weekFromTimestamp(DSL.timestamp(field(field, Date.class))).as("week");
        Field<Integer> hour = DSL.hour(DSL.timestamp(field(field, Date.class))).as("hour");
        Field<Integer> minute = DSL.minute(DSL.timestamp(field(field, Date.class))).as("minute");

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
}
