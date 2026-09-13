package io.kestra.jdbc.tables;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.fethr.table.IndexService;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.fethr.table.TableIndexDefinition;
import io.kestra.fethr.table.TableRepositoryInterface;
import io.kestra.fethr.table.TableService;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Index management over JDBC, with the same physical-first, revert-on-registry-failure shape. */
@Singleton
@Requires(beans = TableRepositoryInterface.class)
@Slf4j
public class JdbcIndexService implements IndexService {

    private final TableService tableService;
    private final TableRepositoryInterface tableRepository;
    private final TableDdlSupport ddl;

    public JdbcIndexService(TableService tableService, TableRepositoryInterface tableRepository, TableDdlSupport ddl) {
        this.tableService = tableService;
        this.tableRepository = tableRepository;
        this.ddl = ddl;
    }

    @Override
    public TableDefinition createIndex(String tenantId, String namespace, String name, TableIndexDefinition index) {
        TableDefinition current = tableService.getTable(tenantId, namespace, name);
        ddl.validateIndexName(index.name());

        if (index.columns().isEmpty()) {
            throw new IllegalArgumentException("An index must cover at least one column");
        }

        if (current.getIndexes().stream().anyMatch(existing -> existing.name().equals(index.name()))) {
            throw ddl.nameConflict("An index named '" + index.name() + "' already exists", "index.name", index.name());
        }

        // Every covered column has to exist; findColumn throws for the first that does not.
        index.columns().forEach(column -> ddl.findColumn(current, column));

        ddl.createPhysicalIndex(current.getPhysicalTableName(), index);

        List<TableIndexDefinition> indexes = new ArrayList<>(current.getIndexes());
        indexes.add(new TableIndexDefinition(index.name(), List.copyOf(index.columns()), index.unique()));
        TableDefinition updated = current.toBuilder().indexes(indexes).updated(Instant.now()).build();

        try {
            tableRepository.update(updated, current);
        } catch (RuntimeException e) {
            log.error("Registry write failed after creating index '{}' on '{}'; dropping the index", index.name(), name, e);
            ddl.dropPhysicalIndex(current.getPhysicalTableName(), index.name());
            throw e;
        }

        return updated;
    }

    @Override
    public TableDefinition dropIndex(String tenantId, String namespace, String name, String indexName) {
        TableDefinition current = tableService.getTable(tenantId, namespace, name);

        if (current.getIndexes().stream().noneMatch(index -> index.name().equals(indexName))) {
            throw new NotFoundException("Index not found: " + indexName);
        }

        ddl.dropPhysicalIndex(current.getPhysicalTableName(), indexName);

        List<TableIndexDefinition> indexes = current.getIndexes().stream()
            .filter(index -> !index.name().equals(indexName))
            .toList();
        TableDefinition updated = current.toBuilder().indexes(indexes).updated(Instant.now()).build();
        tableRepository.update(updated, current);

        return updated;
    }
}
