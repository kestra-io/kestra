package io.kestra.repository.h2.migration;

import io.kestra.jdbc.LogJdbcDataSourceProvider;
import io.kestra.jdbc.migration.AbstractV2_0_06WidenLogsMigration;
import io.kestra.jdbc.migration.LogStoreTypeResolver;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import jakarta.inject.Singleton;

/**
 * H2 log-store task_id/trigger_id widening migration.
 *
 * <p>
 * Runs whenever the effective log-store dialect is H2 — a configured {@code kestra.logs.type=h2|memory}
 * or the fallback to an H2 {@code kestra.repository.type} (logs kept in the main database).
 */
@Singleton
@Requires(condition = V2_0_06WidenLogsMigration.H2LogStoreEnabled.class)
public class V2_0_06WidenLogsMigration extends AbstractV2_0_06WidenLogsMigration {

    public static final class H2LogStoreEnabled implements Condition {
        @Override
        @SuppressWarnings("rawtypes") // Condition.matches() declares a raw ConditionContext parameter
        public boolean matches(final ConditionContext context) {
            return LogStoreTypeResolver.matches(context, "h2");
        }
    }

    public V2_0_06WidenLogsMigration(final LogJdbcDataSourceProvider logDataSourceProvider) {
        super("h2", "/migrations/2.0.06-widen-logs-h2.sql", logDataSourceProvider);
    }
}
