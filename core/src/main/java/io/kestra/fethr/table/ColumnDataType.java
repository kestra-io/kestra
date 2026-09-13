package io.kestra.fethr.table;

/**
 * The data types a user-defined table column can take.
 *
 * <p>
 * Each maps to a concrete jOOQ type in the persistence layer, which is what the per-table DDL is
 * generated from. {@code primaryKey} marks the two that can back a primary key -- a generated
 * {@code UUID} or an auto-incrementing {@code NUMBER} -- so a primary key's type is chosen from
 * this same enum rather than a parallel one.
 */
public enum ColumnDataType {
    STRING(false),
    NUMBER(true),
    BOOLEAN(false),
    DATETIME(false),
    DATE(false),
    JSON(false),
    UUID(true);

    private final boolean primaryKey;

    ColumnDataType(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }
}
