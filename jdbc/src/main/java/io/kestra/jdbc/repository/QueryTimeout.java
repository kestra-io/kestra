package io.kestra.jdbc.repository;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;

import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.conf.SettingsTools;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultExecuteListenerProvider;

import io.kestra.core.exceptions.QueryTimeoutException;

import io.micronaut.core.annotation.Nullable;

/**
 * Bounds how long the queries built from the returned context may run.
 * <p>
 * The limit goes through {@link java.sql.Statement#setQueryTimeout(int)}, so it is rounded up to whole seconds, and a
 * query that hits it surfaces as a {@link QueryTimeoutException} rather than the driver's own error.
 */
public final class QueryTimeout {
    // Postgres reports a cancelled statement under this state instead of throwing an SQLTimeoutException.
    private static final String QUERY_CANCELED_STATE = "57014";

    private QueryTimeout() {
    }

    public static DSLContext apply(Configuration configuration, @Nullable Duration timeout) {
        if (timeout == null || !timeout.isPositive()) {
            return DSL.using(configuration);
        }

        int seconds = (int) Math.min(Integer.MAX_VALUE, Math.max(1, timeout.toSeconds()));
        return DSL.using(
            configuration
                .derive(SettingsTools.clone(configuration.settings()).withQueryTimeout(seconds))
                .deriveAppending(new DefaultExecuteListenerProvider(new TimeoutListener(timeout)))
        );
    }

    private record TimeoutListener(Duration timeout) implements ExecuteListener {
        @Override
        public void exception(ExecuteContext ctx) {
            SQLException cause = ctx.sqlException();
            if (cause instanceof SQLTimeoutException || (cause != null && QUERY_CANCELED_STATE.equals(cause.getSQLState()))) {
                ctx.exception(new QueryTimeoutException(timeout, cause));
            }
        }
    }
}
