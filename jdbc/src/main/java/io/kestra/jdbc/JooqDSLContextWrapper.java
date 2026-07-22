package io.kestra.jdbc;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;

import io.kestra.core.models.tasks.retrys.Random;
import io.kestra.core.utils.RetryUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class JooqDSLContextWrapper {
    private final DSLContext dslContext;

    private final RetryUtils retryUtils;

    @Inject
    public JooqDSLContextWrapper(DSLContext dslContext, RetryUtils retryUtils) {
        this.dslContext = dslContext;
        this.retryUtils = retryUtils;
    }

    private <T> RetryUtils.Instance<T, RuntimeException> retryer() {
        return retryUtils.of(
            Random.builder()
                .minInterval(Duration.ofMillis(50))
                .maxAttempts(-1)
                .maxDuration(Duration.ofSeconds(60))
                .maxInterval(Duration.ofMillis(1000))
                .build()
        );
    }

    private static <E extends Throwable> Predicate<E> predicate() {
        return (e) ->
        {
            if (!(e.getCause() instanceof SQLException)) {
                return false;
            }

            SQLException cause = (SQLException) e.getCause();

            return
            // standard deadlock
            cause.getSQLState().equals("40001") ||
            // postgres deadlock
                cause.getSQLState().equals("40P01") ||
            // MySQL lock wait timeout (ER_LOCK_WAIT_TIMEOUT), so a transient lock contention is retried
            // instead of bubbling up and crashing the producing thread (e.g. a worker emitting a result).
                cause.getErrorCode() == 1205;
        };
    }

    public void transaction(TransactionalRunnable transactional) {
        this.<Void> retryer().runRetryIf(
            predicate(),
            () ->
            {
                dslContext.transaction(transactional);
                return null;
            }
        );
    }

    public <T> T transactionResult(TransactionalCallable<T> transactional) {
        return this.<T> retryer().runRetryIf(
            predicate(),
            () -> dslContext.transactionResult(transactional)
        );
    }

    /**
     * Predicate that matches retryable database deadlock exceptions.
     */
    static final class DeadlockPredicate implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable e) {
            // Walk the full cause chain: once Postgres aborts a transaction after a deadlock,
            // a later statement in the same failed attempt surfaces a secondary "current transaction is aborted" exception
            // that wraps the original deadlock one level deeper.
            // Track visited causes by identity to stop on a cyclic chain (e.g. a cause pointing back to an exception already seen).
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Throwable cause = e.getCause();
            while (cause != null && seen.add(cause)) {
                if (isDeadlockOrLockTimeout(cause)) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }

        private static boolean isDeadlockOrLockTimeout(Throwable cause) {
            if (!(cause instanceof SQLException sqlException)) {
                return false;
            }

            // MySQL/MariaDB vendor codes:
            // 1213 = ER_LOCK_DEADLOCK
            // 1205 = ER_LOCK_WAIT_TIMEOUT
            int vendorCode = sqlException.getErrorCode();
            if (vendorCode == 1213 || vendorCode == 1205) {
                return true;
            }

            return
            // standard deadlock
            "40001".equals(sqlException.getSQLState()) ||
            // postgres deadlock
                "40P01".equals(sqlException.getSQLState());
        }
    }
}
