package io.kestra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

import javax.sql.DataSource;

import org.jooq.ConnectionProvider;
import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConnectionProvider;
import org.jooq.impl.DefaultTransactionProvider;

import io.kestra.core.models.tasks.retrys.Random;
import io.kestra.core.utils.RetryUtils;

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class JooqDSLContextWrapper {
    private static final Random RETRY_POLICY = Random.builder()
        .minInterval(Duration.ofMillis(50))
        .maxAttempts(-1)
        .maxDuration(Duration.ofSeconds(60))
        .maxInterval(Duration.ofMillis(1000))
        .build();

    private static final DeadlockPredicate DEADLOCK_PREDICATE = new DeadlockPredicate();

    private final DSLContext dslContext;
    private final DataSource rawDataSource;
    private final SynchronousTransactionManager<Connection> transactionManager;

    /**
     * @param dataSource used by {@link #requireNewTransaction(TransactionalRunnable)}, and an
     *        explicit dependency to ensure Micronaut destroys this bean before the DataSource.
     *        Without it, the @EachBean-derived DSLContext/Configuration may be destroyed
     *        together with the DataSource, leaving this wrapper with a stale DSLContext.
     * @param transactionManager used by {@link #nestedTransactionResult(TransactionalCallable)}
     *        to open a {@code Propagation.NESTED} transaction directly — jOOQ's own
     *        {@code TransactionProvider} always requests {@code TransactionDefinition.DEFAULT}
     *        (REQUIRED), so nested/savepoint semantics can only be reached through this manager.
     */
    @Inject
    public JooqDSLContextWrapper(DSLContext dslContext, DataSource dataSource, SynchronousTransactionManager<Connection> transactionManager) {
        this.dslContext = dslContext;
        // Unwrap any Micronaut Data AOP proxy: the wrapped DataSource hands back the current
        // thread's transaction-bound connection instead of a new one.
        this.rawDataSource = DelegatingDataSource.unwrapDataSource(dataSource);
        this.transactionManager = transactionManager;
    }

    /**
     * For a wrapper built manually around an ad-hoc datasource (e.g. a dedicated log database)
     * that has no Micronaut-managed transaction manager of its own; {@link #nestedTransactionResult}
     * is unavailable on an instance built this way.
     */
    public JooqDSLContextWrapper(DSLContext dslContext, DataSource dataSource) {
        this(dslContext, dataSource, null);
    }

    /**
     * Shared retryer for every transaction run by this wrapper, built once at class initialisation.
     * <p>
     * Building it per call rebuilt the retry policy, the fallback and their two listeners on every
     * database operation, which is the hottest path in the application.
     */
    private static final RetryUtils.Retryer DEADLOCK_RETRYER = RetryUtils
        .<Object, RuntimeException> of(RETRY_POLICY)
        .retryerIf(DEADLOCK_PREDICATE);

    public void transaction(TransactionalRunnable transactional) {
        DEADLOCK_RETRYER.<Void> run(
            () ->
            {
                dslContext.transaction(transactional);
                return null;
            }
        );
    }

    public <T> T transactionResult(TransactionalCallable<T> transactional) {
        return DEADLOCK_RETRYER.run(
            () -> dslContext.transactionResult(transactional)
        );
    }

    /**
     * Runs the given work in a transaction on a dedicated connection acquired directly from the
     * underlying pool, so it is committed before this method returns and immediately visible to
     * other connections — even when a transaction is already open on the current thread.
     * <p>
     * Regular {@link #transaction(TransactionalRunnable)} calls are bound to the calling thread
     * and silently join a caller-owned transaction (e.g. the dispatch-queue poll transaction),
     * deferring their writes until that transaction commits.
     * <p>
     * When the calling thread already has a connection checked out, this briefly holds a second
     * one — keep the work short.
     */
    public void requireNewTransaction(TransactionalRunnable transactional) {
        DEADLOCK_RETRYER.<Void> run(
            () ->
            {
                try (Connection connection = rawDataSource.getConnection()) {
                    // Same configuration (dialect, settings, execute listeners), but jOOQ-managed
                    // transactions on this connection instead of the thread-bound ones.
                    ConnectionProvider connectionProvider = new DefaultConnectionProvider(connection);
                    DSL.using(
                        dslContext.configuration()
                            .derive(connectionProvider)
                            .derive(new DefaultTransactionProvider(connectionProvider))
                    )
                        .transaction(transactional);
                } catch (SQLException e) {
                    throw new DataAccessException("Unable to run a transaction on a new connection", e);
                }
                return null;
            }
        );
    }

    /**
     * Runs {@code transactional} inside a {@code Propagation.NESTED} transaction: if a
     * transaction is already open on the current thread (e.g. the dispatch-queue poll
     * transaction), this opens a SAVEPOINT and, on failure, rolls back to it instead of
     * dooming the caller's transaction — the only way to clear Postgres's
     * aborted-transaction state without aborting the outer transaction too (see
     * {@link #transaction(TransactionalRunnable)}). With no open transaction, this behaves
     * like a plain new one. Not retried on deadlock: a deadlock inside a savepoint can't be
     * retried in isolation once the parent transaction is already dead.
     */
    public <T> T nestedTransactionResult(TransactionalCallable<T> transactional) {
        if (transactionManager == null) {
            throw new UnsupportedOperationException("This JooqDSLContextWrapper has no transaction manager (built for an ad-hoc datasource); nestedTransactionResult is unavailable.");
        }
        return transactionManager.execute(
            TransactionDefinition.of(TransactionDefinition.Propagation.NESTED),
            status ->
            {
                try {
                    return transactional.run(dslContext.configuration());
                } catch (Exception e) {
                    throw e;
                } catch (Throwable t) {
                    throw new DataAccessException("Unable to run a nested transaction", t);
                }
            }
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
