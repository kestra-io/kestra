package io.kestra.fethr.table;

import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * Schema management for user-defined tables: create, read, list, rename, delete.
 *
 * <p>
 * Its whole job is keeping two things consistent that live in different places -- the registry row
 * describing a table, and the physical table holding its rows. Columns and indexes are split into
 * {@link ColumnService} and {@link IndexService} so each has one controller and one service.
 *
 * <p>
 * Persistence-agnostic by design: the implementation renders DDL and so belongs to the JDBC layer,
 * while the controllers depend only on this.
 */
public interface TableService {

    TableDefinition createTable(TableDefinition definition);

    TableDefinition getTable(String tenantId, String namespace, String name);

    ArrayListTotal<TableDefinition> listTables(Pageable pageable, String tenantId, @Nullable List<QueryFilter> filters);

    TableDefinition renameTable(TableDefinition current, String newName, String description);

    void deleteTable(String tenantId, String namespace, String name);
}
