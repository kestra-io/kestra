package io.kestra.fethr.table;

import java.util.List;

import io.micronaut.core.annotation.Introspected;

/** An index on a user-defined table, over one or more of its columns. */
@Introspected
public record TableIndexDefinition(
    String name,
    List<String> columns,
    boolean unique) {
    public TableIndexDefinition {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
