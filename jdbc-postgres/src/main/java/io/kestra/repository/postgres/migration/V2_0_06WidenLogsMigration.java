package io.kestra.repository.postgres.migration;

import io.kestra.jdbc.LogJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractV2_0_06WidenLogsMigration;
import io.kestra.jdbc.migration.LogStoreTypeResolver;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import jakarta.inject.Singleton;

/**
 * Postgres log-store task_id/trigger_id widening migration.
 *
 * <p>
 * Runs whenever the effective log-store dialect is Postgres — a configured {@code kestra.logs.type=postgres}
 * or the fallback to a Postgres {@code kestra.repository.type} (logs kept in the main database).
 */
@Singleton
@Requires(condition = V2_0_06WidenLogsMigration.PostgresLogStoreEnabled.class)
public class V2_0_06WidenLogsMigration extends AbstractV2_0_06WidenLogsMigration {

    public static final class PostgresLogStoreEnabled implements Condition {
        @Override
        public boolean matches(final ConditionContext context) {
            return LogStoreTypeResolver.matches(context, "postgres");
        }
    }

    public V2_0_06WidenLogsMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        super("postgres", "/migrations/2.0.06-widen-logs-postgres.sql", logDataSourceProvider);
    }
}
