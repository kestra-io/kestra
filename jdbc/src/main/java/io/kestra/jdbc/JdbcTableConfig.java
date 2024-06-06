package io.kestra.jdbc;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

@EachProperty("kestra.jdbc.tables")
public class JdbcTableConfig {
    private final String name;
    private final Class<?> cls;
    private final String table;

    public JdbcTableConfig(
        @Parameter String name,
        @Nullable Class<?> cls,
        @NotNull String table
    ) {
        this.name = name;
        this.cls = cls;
        this.table = table;
    }

    public String name() {
        return this.name;
    }

    public Class<?> cls() {
        return this.cls;
    }

    public String table() {
        return this.table;
    }
}
