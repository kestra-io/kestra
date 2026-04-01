package io.kestra.jdbc;

import java.sql.SQLException;
import java.time.Duration;
import java.util.function.Predicate;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;

import io.kestra.core.models.tasks.retrys.Random;
import io.kestra.core.utils.RetryUtils;

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

    /**
     * @param dataSource explicit dependency to ensure Micronaut destroys this bean before the DataSource.
     *        Without it, the @EachBean-derived DSLContext/Configuration may be destroyed
     *        together with the DataSource, leaving this wrapper with a stale DSLContext.
     */
    @Inject
    public JooqDSLContextWrapper(DSLContext dslContext, DataSource dataSource) {
        this.dslContext = dslContext;
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
