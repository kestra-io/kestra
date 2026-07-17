package io.kestra.repository.h2.migration;

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
 * H2 log-store trigger_id widening migration.
 *
 * <p>
 * Widens the generated {@code trigger_id} column to {@code VARCHAR(256)} on the log-store table resolved
 * by {@link LogJdbcDataSourceProvider} (a dedicated database when {@code kestra.logs.h2.url} is set,
 * otherwise the primary datasource), matching {@code Trigger.id}'s {@code @Size(max = 256)}. Mirrors
 * {@code V2_0_16WidenLogsTaskIdMigration}: the scriptId encodes the backend ("-h2") and a non-default
 * table name, so switching the log-repo backend re-applies it.
 *
 * <p>
 * Runs whenever the effective log-store dialect is H2 — a configured {@code kestra.logs.type=h2|memory}
 * or the fallback to an H2 {@code kestra.repository.type} (logs kept in the main database).
 */
@Singleton
@Requires(condition = V2_0_17WidenLogsTriggerIdMigration.H2LogStoreEnabled.class)
public class V2_0_17WidenLogsTriggerIdMigration extends AbstractSQLMigrationScript {

    public static final class H2LogStoreEnabled implements Condition {
        @Override
        public boolean matches(final ConditionContext context) {
            return LogStoreTypeResolver.matches(context, "h2");
        }
    }

    private static final String SCRIPT_ID = "2.0.17-widen-logs-trigger-id-h2";
    private static final String SQL_RESOURCE = "/migrations/2.0.17-widen-logs-trigger-id-h2.sql";

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
        return "H2 dedicated log store: widen trigger_id to VARCHAR(256)";
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
