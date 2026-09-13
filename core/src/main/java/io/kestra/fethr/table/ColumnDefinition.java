package io.kestra.fethr.table;

import io.micronaut.core.annotation.Introspected;

/**
 * One column of a user-defined table.
 *
 * <p>
 * The ordered list of these on a {@link TableDefinition} is the canonical column metadata: it drives
 * both the generated DDL and the order columns are projected in.
 *
 * <p>
 * {@code sensitive} masks the value in the UI only. It is not encryption -- unlike a
 * {@link io.kestra.fethr.vault.CryptographicValue}, the value is stored and returned in the clear.
 */
@Introspected
public record ColumnDefinition(
    String name,
    ColumnDataType type,
    boolean nullable,
    boolean sensitive,
    int position,
    String hint,
    String defaultValue) {
    public ColumnDefinition {
        hint = hint == null ? "" : hint;
        defaultValue = defaultValue == null ? "" : defaultValue;
    }
}
