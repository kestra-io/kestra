package io.kestra.jdbc;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
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

    @Inject
    public JooqDSLContextWrapper(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    private <T> RetryUtils.Instance<T, RuntimeException> retryer() {
        return RetryUtils.of(
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
            // Walk the whole exception graph, not just the cause chain:
            // - the thrown exception itself can already be the SQLException;
            // - once Postgres aborts a transaction after a deadlock, a later statement in the same
            //   failed attempt surfaces a secondary "current transaction is aborted" exception that
            //   wraps the original deadlock one level deeper;
            // - MySQL rolls the transaction back server-side on a deadlock, so the rollback jOOQ
            //   then attempts can fail and be attached as a suppressed exception;
            // - drivers chain secondary SQLExceptions on getNextException(), which is neither the
            //   cause nor a suppressed exception.
            // Track visited throwables by identity to stop on a cyclic or diamond-shaped graph.
            Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Deque<Throwable> pending = new ArrayDeque<>();
            pending.add(e);

            while (!pending.isEmpty()) {
                Throwable current = pending.poll();
                if (!seen.add(current)) {
                    continue;
                }

                if (isDeadlockOrLockTimeout(current)) {
                    return true;
                }

                if (current.getCause() != null) {
                    pending.add(current.getCause());
                }
                Collections.addAll(pending, current.getSuppressed());
                // JDBC chains secondary failures on their own list rather than as causes
                if (current instanceof SQLException sqlException && sqlException.getNextException() != null) {
                    pending.add(sqlException.getNextException());
                }
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
