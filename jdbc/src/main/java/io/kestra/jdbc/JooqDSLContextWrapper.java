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

import io.micronaut.context.annotation.EachBean;
import jakarta.inject.Inject;

/**
 * Wraps a jOOQ {@link DSLContext} with deadlock-retrying transaction helpers.
 *
 * <p>Declared as {@code @EachBean(DataSource.class)} so that one instance is created per
 * configured datasource. Micronaut's EachBean qualifier propagation ensures the correctly-named
 * {@code DSLContext} (e.g. {@code "h2"}) is injected rather than an unqualified one, which
 * avoids ambiguity when both {@code micronaut-jooq} and {@code micronaut-data-jdbc} are on the
 * classpath (the latter creates an additional unqualified DSLContext for transaction management).
 */
@EachBean(DataSource.class)
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
     * @param dslContext the datasource-specific DSLContext; resolved by name through EachBean
     *                   qualifier propagation to avoid ambiguity with other DSLContext beans.
     * @param dataSource explicit dependency to ensure Micronaut destroys this bean before the
     *                   DataSource. Without it, the @EachBean-derived DSLContext/Configuration
     *                   may be destroyed together with the DataSource, leaving this wrapper with
     *                   a stale DSLContext.
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
