package io.kestra.repository.mysql.migration;

import io.kestra.jdbc.LogJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractV2_0_06WidenLogsMigration;
import io.kestra.jdbc.migration.LogStoreTypeResolver;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import jakarta.inject.Singleton;

/**
 * MySQL log-store task_id/trigger_id widening migration.
 *
 * <p>
 * Runs whenever the effective log-store dialect is MySQL — a configured {@code kestra.logs.type=mysql}
 * or the fallback to a MySQL {@code kestra.repository.type} (logs kept in the main database).
 */
@Singleton
@Requires(condition = V2_0_06WidenLogsMigration.MysqlLogStoreEnabled.class)
public class V2_0_06WidenLogsMigration extends AbstractV2_0_06WidenLogsMigration {

    public static final class MysqlLogStoreEnabled implements Condition {
        @Override
        public boolean matches(final ConditionContext context) {
            return LogStoreTypeResolver.matches(context, "mysql");
        }
    }

    public V2_0_06WidenLogsMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        super("mysql", "/migrations/2.0.06-widen-logs-mysql.sql", logDataSourceProvider);
    }
}
