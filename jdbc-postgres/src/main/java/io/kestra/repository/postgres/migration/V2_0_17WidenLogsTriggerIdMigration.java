package io.kestra.repository.postgres.migration;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import io.kestra.jdbc.LogJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;
import io.kestra.jdbc.migration.LogStoreTypeResolver;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import jakarta.inject.Singleton;

/**
 * Postgres log-store trigger_id widening migration.
 *
 * <p>
 * Widens the generated {@code trigger_id} column to {@code VARCHAR(256)} on the log-store table resolved
 * by {@link LogJdbcDataSourceProvider} (a dedicated database when {@code kestra.logs.postgres.url} is
 * set, otherwise the primary datasource), matching {@code Trigger.id}'s {@code @Size(max = 256)}. Mirrors
 * {@code V2_0_16WidenLogsTaskIdMigration}: the scriptId encodes the backend ("-postgres") and a
 * non-default table name, so switching the log-repo backend re-applies it.
 *
 * <p>
 * Runs whenever the effective log-store dialect is Postgres — a configured {@code kestra.logs.type=postgres}
 * or the fallback to a Postgres {@code kestra.repository.type} (logs kept in the main database).
 */
@Singleton
@Requires(condition = V2_0_17WidenLogsTriggerIdMigration.PostgresLogStoreEnabled.class)
public class V2_0_17WidenLogsTriggerIdMigration extends AbstractSQLMigrationScript {

    public static final class PostgresLogStoreEnabled implements Condition {
        @Override
        public boolean matches(final ConditionContext context) {
            return LogStoreTypeResolver.matches(context, "postgres");
        }
    }

    private static final String SCRIPT_ID = "2.0.17-widen-logs-trigger-id-postgres";
    private static final String SQL_RESOURCE = "/migrations/2.0.17-widen-logs-trigger-id-postgres.sql";

    private final LogJdbcDataSourceProvider logDataSourceProvider;

    public V2_0_17WidenLogsTriggerIdMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        this.logDataSourceProvider = logDataSourceProvider;
    }

    @Override
    public String scriptId() {
        String table = logDataSourceProvider.table();
        return "logs".equals(table) ? SCRIPT_ID : SCRIPT_ID + "-" + table;
    }

    @Override
    public String description() {
        return "Postgres dedicated log store: widen trigger_id to VARCHAR(256)";
    }

    @Override
    public List<String> sqlResources() {
        return List.of(SQL_RESOURCE);
    }

    @Override
    protected DataSource dataSource() {
        return logDataSourceProvider.dataSource();
    }

    @Override
    public void migrate() throws Exception {
        executeSqlScript(dataSource(), SQL_RESOURCE, Map.of("table", logDataSourceProvider.table()));
    }
}
