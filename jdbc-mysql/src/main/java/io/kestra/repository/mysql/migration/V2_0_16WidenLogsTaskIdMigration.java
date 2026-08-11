package io.kestra.repository.mysql.migration;

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
 * MySQL log-store task_id widening migration.
 *
 * <p>
 * Widens the generated {@code task_id} column to {@code VARCHAR(256)} on the log-store table resolved
 * by {@link LogJdbcDataSourceProvider}. Guarded by an information_schema length check because
 * modifying a {@code STORED GENERATED} column rebuilds the table ({@code ALGORITHM=COPY}). Mirrors
 * {@code V2_0_14LogsMigration}: the scriptId encodes the backend ("-mysql") and a non-default table
 * name, so switching the log-repo backend re-applies it.
 *
 * <p>
 * Runs whenever the effective log-store dialect is MySQL — a configured {@code kestra.logs.type=mysql}
 * or the fallback to a MySQL {@code kestra.repository.type} (logs kept in the main database).
 */
@Singleton
@Requires(condition = V2_0_16WidenLogsTaskIdMigration.MysqlLogStoreEnabled.class)
public class V2_0_16WidenLogsTaskIdMigration extends AbstractSQLMigrationScript {

    public static final class MysqlLogStoreEnabled implements Condition {
        @Override
        public boolean matches(final ConditionContext context) {
            return LogStoreTypeResolver.matches(context, "mysql");
        }
    }

    private static final String SCRIPT_ID = "2.0.16-widen-logs-task-id-mysql";
    private static final String SQL_RESOURCE = "/migrations/2.0.16-widen-logs-task-id-mysql.sql";

    private final LogJdbcDataSourceProvider logDataSourceProvider;

    public V2_0_16WidenLogsTaskIdMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        this.logDataSourceProvider = logDataSourceProvider;
    }

    @Override
    public String scriptId() {
        String table = logDataSourceProvider.table();
        return "logs".equals(table) ? SCRIPT_ID : SCRIPT_ID + "-" + table;
    }

    @Override
    public String description() {
        return "MySQL dedicated log store: widen task_id to VARCHAR(256)";
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
