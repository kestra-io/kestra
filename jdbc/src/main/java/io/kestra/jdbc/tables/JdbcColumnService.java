package io.kestra.jdbc.tables;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jooq.exception.DataAccessException;

import io.kestra.fethr.table.ColumnDataType;
import io.kestra.fethr.table.ColumnDefinition;
import io.kestra.fethr.table.ColumnService;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.fethr.table.TableIndexDefinition;
import io.kestra.fethr.table.TableRepositoryInterface;
import io.kestra.fethr.table.TableService;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Column management over JDBC. */
@Singleton
@Requires(beans = TableRepositoryInterface.class)
@Slf4j
public class JdbcColumnService implements ColumnService {

    private final TableService tableService;
    private final TableRepositoryInterface tableRepository;
    private final TableDdlSupport ddl;

    public JdbcColumnService(TableService tableService, TableRepositoryInterface tableRepository, TableDdlSupport ddl) {
        this.tableService = tableService;
        this.tableRepository = tableRepository;
        this.ddl = ddl;
    }

    @Override
    public TableDefinition addColumn(String tenantId, String namespace, String name, ColumnDefinition column) {
        TableDefinition current = tableService.getTable(tenantId, namespace, name);
        ddl.validateColumnName(column.name());

        if (ddl.columnExists(current, column.name())) {
            throw ddl.nameConflict("A column named '" + column.name() + "' already exists", "column.name", column.name());
        }

        List<ColumnDefinition> columns = new ArrayList<>(current.getColumns());
        ColumnDefinition added = new ColumnDefinition(
            column.name(),
            column.type(),
            column.nullable(),
            column.sensitive(),
            columns.size(),
            column.hint(),
            column.defaultValue()
        );
        columns.add(added);

        ddl.addPhysicalColumn(current.getPhysicalTableName(), added);
        TableDefinition updated = current.toBuilder().columns(columns).updated(Instant.now()).build();

        try {
            tableRepository.update(updated, current);
        } catch (RuntimeException e) {
            log.error("Registry write failed after adding column '{}' to '{}'; dropping the column", column.name(), name, e);
            ddl.dropPhysicalColumn(current.getPhysicalTableName(), added.name());
            throw e;
        }

        return updated;
    }

    @Override
    public TableDefinition renameColumn(String tenantId, String namespace, String name, String columnName, String newColumnName) {
        TableDefinition current = tableService.getTable(tenantId, namespace, name);
        ddl.validateColumnName(newColumnName);
        ddl.findColumn(current, columnName);

        if (ddl.columnExists(current, newColumnName)) {
            throw ddl.nameConflict("A column named '" + newColumnName + "' already exists", "column.name", newColumnName);
        }

        List<ColumnDefinition> columns = current.getColumns().stream()
            .map(column -> column.name().equals(columnName) ? renamed(column, newColumnName) : column)
            .toList();

        // PostgreSQL carries a column rename into every index over it, so the registry has to follow.
        List<TableIndexDefinition> indexes = current.getIndexes().stream()
            .map(
                index -> new TableIndexDefinition(
                    index.name(),
                    index.columns().stream().map(c -> c.equals(columnName) ? newColumnName : c).toList(),
                    index.unique()
                )
            )
            .toList();

        ddl.renamePhysicalColumn(current.getPhysicalTableName(), columnName, newColumnName);
        TableDefinition updated = current.toBuilder().columns(columns).indexes(indexes).updated(Instant.now()).build();

        try {
            tableRepository.update(updated, current);
        } catch (RuntimeException e) {
            log.error("Registry write failed renaming column '{}' to '{}' on '{}'; reverting", columnName, newColumnName, name, e);
            ddl.renamePhysicalColumn(current.getPhysicalTableName(), newColumnName, columnName);
            throw e;
        }

        return updated;
    }

    @Override
    public TableDefinition updateColumn(String tenantId, String namespace, String name, String columnName, ColumnDataType newType) {
        TableDefinition current = tableService.getTable(tenantId, namespace, name);
        ColumnDefinition column = ddl.findColumn(current, columnName);

        if (column.type() == newType) {
            return current;
        }

        List<ColumnDefinition> columns = current.getColumns().stream()
            .map(c -> c.name().equals(columnName) ? retyped(c, newType) : c)
            .toList();

        try {
            ddl.alterPhysicalColumnType(current.getPhysicalTableName(), columnName, newType);
        } catch (DataAccessException e) {
            // A stored value that will not cast is the caller asking for something impossible, not a
            // server fault, so it surfaces as a bad request rather than a 500.
            log.error("Type change refused for column '{}' on table '{}' to {}", columnName, name, newType, e);
            throw new IllegalArgumentException(
                "Cannot change the type of column '" + columnName + "' to " + newType + " (existing data is incompatible)"
            );
        }

        TableDefinition updated = current.toBuilder().columns(columns).updated(Instant.now()).build();

        try {
            tableRepository.update(updated, current);
        } catch (RuntimeException e) {
            log.error("Registry write failed retyping column '{}' on '{}'; reverting", columnName, name, e);
            ddl.alterPhysicalColumnType(current.getPhysicalTableName(), columnName, column.type());
            throw e;
        }

        return updated;
    }

    @Override
    public TableDefinition dropColumn(String tenantId, String namespace, String name, String columnName) {
        TableDefinition current = tableService.getTable(tenantId, namespace, name);
        ddl.findColumn(current, columnName);

        // Indexes over the column go first, so the column drop has nothing depending on it.
        current.getIndexes().stream()
            .filter(index -> index.columns().contains(columnName))
            .forEach(index -> ddl.dropPhysicalIndex(current.getPhysicalTableName(), index.name()));

        ddl.dropPhysicalColumn(current.getPhysicalTableName(), columnName);

        // Not reverted, for the same reason a table drop is not: the column's values are already gone.
        List<ColumnDefinition> remaining = ddl.renumber(
            current.getColumns().stream().filter(column -> !column.name().equals(columnName)).toList()
        );
        List<TableIndexDefinition> indexes = current.getIndexes().stream()
            .filter(index -> !index.columns().contains(columnName))
            .toList();

        TableDefinition updated = current.toBuilder().columns(remaining).indexes(indexes).updated(Instant.now()).build();
        tableRepository.update(updated, current);

        return updated;
    }

    private static ColumnDefinition renamed(ColumnDefinition column, String newName) {
        return new ColumnDefinition(
            newName, column.type(), column.nullable(), column.sensitive(),
            column.position(), column.hint(), column.defaultValue()
        );
    }

    private static ColumnDefinition retyped(ColumnDefinition column, ColumnDataType newType) {
        return new ColumnDefinition(
            column.name(), newType, column.nullable(), column.sensitive(),
            column.position(), column.hint(), column.defaultValue()
        );
    }
}
