package io.kestra.jdbc;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

@EachProperty("kestra.jdbc.tables")
public record JdbcTableConfig(
    @Parameter String name,
    @Nullable Class<?> cls,
    @NotNull String table
) {
}
