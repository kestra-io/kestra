package io.kestra.jdbc.repository;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Date;
import java.util.List;

@Singleton
public abstract class AbstractJdbcRepository {
    protected Condition defaultFilter() {
        return field("deleted", Boolean.class).eq(false);
    }

    public static Field<Object> field(String name) {
        return DSL.field(DSL.quotedName(name));
    }

    public static <T> Field<T> field(String name, Class<T> cls) {
        return DSL.field(DSL.quotedName(name), cls);
    }

    protected List<Field<?>> groupByFields(Long dayCount) {
        return groupByFields(dayCount, null);
    }

    protected List<Field<?>> groupByFields(Long dayCount, @Nullable String dateField) {
        String field = dateField != null ? dateField : "timestamp";
        Field<Integer> month = DSL.month(DSL.timestamp(field(field, Date.class))).as("month");
        Field<Integer> year = DSL.year(DSL.timestamp(field(field, Date.class))).as("year");
        Field<Integer> day = DSL.day(DSL.timestamp(field(field, Date.class))).as("day");
        Field<Integer> week = DSL.week(DSL.timestamp(field(field, Date.class))).as("week");
        Field<Integer> hour = DSL.hour(DSL.timestamp(field(field, Date.class))).as("hour");

        if (dayCount > 365) {
            return List.of(year, month);
        } else if (dayCount > 180) {
            return List.of(year, week);
        } else if (dayCount > 1) {
            return List.of(year, month, day);
        } else {
            return List.of(year, month, day, hour);
        }
    }



}
