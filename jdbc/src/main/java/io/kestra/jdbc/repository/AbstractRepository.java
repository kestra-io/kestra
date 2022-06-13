package io.kestra.jdbc.repository;

import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

@Singleton
public abstract class AbstractRepository {
    public Condition defaultFilter() {
        return field("deleted", Boolean.class).eq(false);
    }

    public static Field<Object> field(String name) {
        return DSL.field(DSL.quotedName(name));
    }

    public static <T> Field<T> field(String name, Class<T> cls) {
        return DSL.field(DSL.quotedName(name), cls);
    }
}
