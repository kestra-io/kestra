package io.kestra.jdbc.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;

/**
 * Abstract base for the Queue 2.0 upgrade migration, split across two resources:
 * {@link #dropLegacyResource()} drops a genuine pre-Queue-2.0 (Flyway-managed) table, and
 * {@link #convergeResource()} builds/converges the final Queue 2.0 shape. The queue is only
 * partially transient: broadcast messages are safe to lose, but dispatch messages ({@code
 * WorkerJobEvent}, {@code WorkerTaskResult}, {@code Execution}, {@code SubflowExecutionEnd}, …) are
 * removed only by consumption and are never replayed on restart. So {@link #dropLegacyResource()}
 * only runs when {@link #isLegacyQueueTable()} confirms the table predates Queue 2.0 (no {@code
 * routing_key} column) — never against an already-migrated table that may hold live rows.
 */
public abstract class AbstractV2_0_02QueueMigration extends AbstractSQLMigrationScript {

    @Override
    public final String scriptId() {
        return "2.0.02-queue";
    }

    @Override
    public String description() {
        return "Queue 2.0 upgrade: rebuild the queues table on top of a pre-2.0 (Flyway-managed) or fresh-baseline schema";
    }

    /**
     * Classpath resource that drops a genuine pre-Queue-2.0 {@code queues} table.
     */
    protected abstract String dropLegacyResource();

    /**
     * Classpath resource that builds the final Queue 2.0 {@code queues} shape and converges any
     * pre-existing shape (fresh baseline or an already-migrated database) to it.
     */
    protected abstract String convergeResource();

    @Override
    public final List<String> sqlResources() {
        return List.of(dropLegacyResource(), convergeResource());
    }

    @Override
    public void migrate() throws Exception {
        if (isLegacyQueueTable()) {
            executeSqlResource(dataSource(), dropLegacyResource());
        }
        executeSqlResource(dataSource(), convergeResource());
    }

    /**
     * @return {@code true} if a {@code queues} table exists but has no {@code routing_key} column
     *         — i.e. it predates Queue 2.0 and cannot hold live 2.0 queue messages.
     */
    private boolean isLegacyQueueTable() throws SQLException {
        DataSource raw = DelegatingDataSource.unwrapDataSource(dataSource());
        try (Connection connection = raw.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            boolean tableExists = false;
            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    if ("queues".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                        tableExists = true;
                        break;
                    }
                }
            }
            if (!tableExists) {
                return false;
            }

            try (ResultSet columns = metaData.getColumns(null, null, "%", "%")) {
                while (columns.next()) {
                    if ("queues".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && "routing_key".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
