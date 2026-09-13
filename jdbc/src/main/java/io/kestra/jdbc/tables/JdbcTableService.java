package io.kestra.jdbc.tables;

import java.time.Instant;
import java.util.List;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.fethr.table.ColumnDefinition;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.fethr.table.TableRepositoryInterface;
import io.kestra.fethr.table.TableService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Schema management over JDBC.
 *
 * <p>
 * Each operation touches two things that cannot share a transaction: the physical table, which is
 * DDL, and the registry row, which is a normal write. So each does the physical half first and
 * undoes it if the registry half fails -- with one exception. A drop is not undone, because a
 * dropped table cannot be put back; there the registry is cleared only after the drop succeeds, so
 * a failure leaves a live table with a registry row still pointing at it rather than the reverse.
 */
@Singleton
@Requires(beans = TableRepositoryInterface.class)
@Slf4j
public class JdbcTableService implements TableService {

    private final TableRepositoryInterface tableRepository;
    private final TableDdlSupport ddl;

    public JdbcTableService(TableRepositoryInterface tableRepository, TableDdlSupport ddl) {
        this.tableRepository = tableRepository;
        this.ddl = ddl;
    }

    @Override
    public TableDefinition createTable(TableDefinition definition) {
        if (tableRepository.findByName(definition.getTenantId(), definition.getNamespace(), definition.getName()).isPresent()) {
            throw ddl.nameConflict(
                "A table named '" + definition.getName() + "' already exists",
                "table.name",
                definition.getName()
            );
        }

        List<ColumnDefinition> normalized = ddl.normalizeColumns(definition.getColumns());
        String physical = ddl.physicalName(definition.getTenantId(), definition.getNamespace(), definition.getName());
        Instant now = Instant.now();

        TableDefinition table = definition.toBuilder()
            .physicalTableName(physical)
            .columns(normalized)
            .indexes(List.of())
            .created(now)
            .updated(now)
            .build();

        ddl.createPhysicalTable(physical, table.getPrimaryKeyType(), normalized);
        try {
            tableRepository.save(table);
        } catch (RuntimeException e) {
            log.error("Registry write failed for table '{}'; dropping the physical table it would have described", table.getName(), e);
            ddl.dropPhysicalTable(physical);
            throw e;
        }

        return getTable(table.getTenantId(), table.getNamespace(), table.getName());
    }

    @Override
    public TableDefinition getTable(String tenantId, String namespace, String name) {
        return tableRepository.findByName(tenantId, namespace, name)
            .orElseThrow(() -> new NotFoundException("Table not found: " + name));
    }

    @Override
    public ArrayListTotal<TableDefinition> listTables(Pageable pageable, String tenantId, @Nullable List<QueryFilter> filters) {
        return tableRepository.find(pageable, tenantId, filters);
    }

    @Override
    public TableDefinition renameTable(TableDefinition current, String newName, String description) {
        // Editing only the description arrives here as a rename to the same name.
        if (current.getName().equals(newName)) {
            TableDefinition updated = current.toBuilder()
                .description(description)
                .updated(Instant.now())
                .build();
            tableRepository.update(updated, current);
            return updated;
        }

        String newPhysical = ddl.physicalName(current.getTenantId(), current.getNamespace(), newName);
        TableDefinition updated = current.toBuilder()
            .name(newName)
            .physicalTableName(newPhysical)
            .description(description)
            .updated(Instant.now())
            .build();

        ddl.renamePhysicalTable(current.getPhysicalTableName(), newPhysical);
        try {
            // The name is the registry key, so a rename re-keys the row rather than updating it.
            tableRepository.save(updated);
            tableRepository.delete(current.getTenantId(), current.getNamespace(), current.getName());
        } catch (RuntimeException e) {
            log.error("Registry re-key failed renaming '{}' to '{}'; reverting the physical rename", current.getName(), newName, e);
            ddl.renamePhysicalTable(newPhysical, current.getPhysicalTableName());
            throw e;
        }

        return updated;
    }

    @Override
    public void deleteTable(String tenantId, String namespace, String name) {
        TableDefinition current = getTable(tenantId, namespace, name);
        ddl.dropPhysicalTable(current.getPhysicalTableName());
        tableRepository.delete(tenantId, namespace, name);
    }
}
