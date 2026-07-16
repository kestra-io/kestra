package io.kestra.jdbc.migration;

/**
 * Abstract base for the 2.0.17 primary-datasource identifier-column migration.
 *
 * <p>
 * Widens every generated column on the primary datasource that holds a {@code Trigger.id} —
 * {@code triggers.trigger_id} and {@code executions.trigger_id} (the latter added by
 * {@code 2.0.01-upgrade}, extracting {@code .trigger.id}) — from {@code VARCHAR(150)} to
 * {@code VARCHAR(256)} to match {@code Trigger.id}'s {@code @Size(max = 256)}. A trigger id longer than
 * 150 chars would otherwise overflow the column and crash-loop the JDBC indexer (see kestra-ee #9268).
 * On H2/MySQL it also normalizes {@code executions.trigger_execution_id} from {@code VARCHAR(100)} to
 * {@code VARCHAR(150)} so all dialects match (Postgres already declares it at 150).
 *
 * <p>
 * The log-store table's {@code trigger_id} is widened separately by the
 * {@code 2.0.17-widen-logs-trigger-id-<dialect>} migration, which runs against the (possibly dedicated)
 * log datasource. Concrete subclasses provide the per-dialect SQL resource and the primary
 * {@link javax.sql.DataSource}.
 */
public abstract class AbstractV2_0_17WidenIdentifierColumnsMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.17-widen-identifier-columns";
    }

    @Override
    public String description() {
        return "Widen trigger_id identifier columns to VARCHAR(256) and normalize trigger_execution_id";
    }
}
