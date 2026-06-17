package io.kestra.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
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

    /**
     * @param dataSource used by {@link #requireNewTransaction(TransactionalRunnable)}, and an
     *        explicit dependency to ensure Micronaut destroys this bean before the DataSource.
     *        Without it, the @EachBean-derived DSLContext/Configuration may be destroyed
     *        together with the DataSource, leaving this wrapper with a stale DSLContext.
     */
    @Inject
    public JooqDSLContextWrapper(DSLContext dslContext, DataSource dataSource) {
        this.dslContext = dslContext;
        // Unwrap any Micronaut Data AOP proxy: the wrapped DataSource hands back the current
        // thread's transaction-bound connection instead of a new one.
        this.rawDataSource = DelegatingDataSource.unwrapDataSource(dataSource);
    }

    private static <T> RetryUtils.Instance<T, RuntimeException> retryer() {
        return RetryUtils.of(RETRY_POLICY);
    }

    public void transaction(TransactionalRunnable transactional) {
        JooqDSLContextWrapper.<Void>retryer().runRetryIf(
            DEADLOCK_PREDICATE,
            () ->
            {
                dslContext.transaction(transactional);
                return null;
            }
        );
    }

    public <T> T transactionResult(TransactionalCallable<T> transactional) {
        return JooqDSLContextWrapper.<T>retryer().runRetryIf(
            DEADLOCK_PREDICATE,
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
        JooqDSLContextWrapper.<Void>retryer().runRetryIf(
            DEADLOCK_PREDICATE,
            () ->
            {
                try (Connection connection = rawDataSource.getConnection()) {
                    // Same configuration (dialect, settings, execute listeners), but jOOQ-managed
                    // transactions on this connection instead of the thread-bound ones.
                    ConnectionProvider connectionProvider = new DefaultConnectionProvider(connection);
                    DSL.using(dslContext.configuration()
                            .derive(connectionProvider)
                            .derive(new DefaultTransactionProvider(connectionProvider)))
                        .transaction(transactional);
                } catch (SQLException e) {
                    throw new DataAccessException("Unable to run a transaction on a new connection", e);
                }
                return null;
            }
        );
    }

    /**
     * Predicate that matches retryable database deadlock exceptions.
     */
    static final class DeadlockPredicate implements Predicate<Throwable> {
        @Override
        public boolean test(Throwable e) {
            if (!(e.getCause() instanceof SQLException cause)) {
                return false;
            }

            // MySQL/MariaDB vendor codes:
            // 1213 = ER_LOCK_DEADLOCK
            // 1205 = ER_LOCK_WAIT_TIMEOUT
            int vendorCode = cause.getErrorCode();
            if (vendorCode == 1213 || vendorCode == 1205) {
                return true;
            }

            return
                // standard deadlock
                "40001".equals(cause.getSQLState()) ||
                // postgres deadlock
                "40P01".equals(cause.getSQLState());
        }
    }
}
