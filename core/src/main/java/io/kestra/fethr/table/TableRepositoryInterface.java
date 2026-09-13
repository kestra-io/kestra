package io.kestra.fethr.table;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

/**
 * Registry persistence for user-defined table schemas.
 *
 * <p>
 * Only the schema lives here, one JSON row per table, keyed by name. The rows a table holds live in
 * its own physical table, which the JDBC layer creates and drops.
 */
public interface TableRepositoryInterface {

    Optional<TableDefinition> findByName(String tenantId, String namespace, String name);

    List<TableDefinition> findByNamespace(String tenantId, String namespace);

    ArrayListTotal<TableDefinition> find(Pageable pageable, String tenantId, @Nullable List<QueryFilter> filters);

    TableDefinition save(TableDefinition table);

    /**
     * @see TableDefinition#validateUpdate(TableDefinition)
     */
    TableDefinition update(TableDefinition table, TableDefinition previous);

    Optional<TableDefinition> delete(String tenantId, String namespace, String name);
}
