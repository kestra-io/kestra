package io.kestra.jdbc;

import io.kestra.core.models.tasks.retrys.Random;
import io.kestra.core.utils.RetryUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;

import java.sql.SQLException;
import java.time.Duration;
import java.util.function.Predicate;

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
                .maxAttempt(-1)
                .maxDuration(Duration.ofSeconds(60))
                .maxInterval(Duration.ofMillis(1000))
                .build()
        );
    }

    private static  <E extends Throwable> Predicate<E> predicate() {
        return (e) -> {
            if (!(e.getCause() instanceof SQLException)) {
                return false;
            }

            SQLException cause = (SQLException) e.getCause();

            return
                // standard deadlock
                cause.getSQLState().equals("40001") ||
                    // postgres deadlock
                    cause.getSQLState().equals("40P01");
        };
    }

    public void transaction(TransactionalRunnable transactional) {
        this.<Void>retryer().runRetryIf(
            predicate(),
            () -> {
                dslContext.transaction(transactional);
                return null;
            }
        );
    }

    public <T> T transactionResult(TransactionalCallable<T> transactional) {
        return this.<T>retryer().runRetryIf(
            predicate(),
            () -> dslContext.transactionResult(transactional)
        );
    }
}
