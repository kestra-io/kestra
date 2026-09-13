package io.kestra.fethr.table;

/**
 * Column management for a user-defined table: add, rename, retype, drop.
 *
 * <p>
 * Every operation applies physical DDL and updates the registry, and has to leave the two agreeing
 * even when the second half fails.
 */
public interface ColumnService {

    TableDefinition addColumn(String tenantId, String namespace, String name, ColumnDefinition column);

    TableDefinition renameColumn(String tenantId, String namespace, String name, String columnName, String newColumnName);

    TableDefinition updateColumn(String tenantId, String namespace, String name, String columnName, ColumnDataType newType);

    TableDefinition dropColumn(String tenantId, String namespace, String name, String columnName);
}
