package io.kestra.jdbc.migration;

/**
 * Abstract base for the 2.0.16 metrics task_id widening migration (main/primary datasource).
 *
 * <p>
 * Widens the generated {@code task_id} column of the {@code metrics} table from {@code VARCHAR(150)}
 * to {@code VARCHAR(256)} to match {@code Task.id}'s {@code @Size(max = 256)}. A plugin-generated
 * {@code taskId} (e.g. Ansible {@code "<host> | <play> : <task>"}) can exceed 150 chars and overflow
 * the column, crash-looping the JDBC indexer. Concrete subclasses provide the per-dialect SQL resource
 * and the primary {@link javax.sql.DataSource}.
 *
 * <p>
 * This migration targets the {@code metrics} table only; the log table's {@code task_id} is widened
 * separately by the {@code 2.0.16-widen-logs-task-id-<dialect>} migration against the log datasource.
 */
public abstract class AbstractV2_0_16WidenMetricsTaskIdMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.16-widen-metrics-task-id";
    }

    @Override
    public String description() {
        return "Widen the metrics task_id column to VARCHAR(256) to match Task.id max size";
    }
}
