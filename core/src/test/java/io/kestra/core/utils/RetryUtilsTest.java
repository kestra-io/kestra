package io.kestra.core.utils;

import io.kestra.core.models.tasks.retrys.Constant;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryUtilsTest {

    private <T, E extends Throwable> RetryUtils.Instance<T, E> instance() {
        return RetryUtils.of(Constant.builder()
            .interval(Duration.ofMillis(10))
            .maxAttempts(3)
            .build());
    }

    @Test
    void resultExceptionThrowRetryExceeded() {
        RetryUtils.Instance<Boolean, Throwable> retrier = instance();
        AtomicInteger inc = new AtomicInteger(3);

        RetryUtils.RetryFailed retryFailed = assertThrows(RetryUtils.RetryFailed.class, () -> {
            retrier.run(
                (o, throwable) -> {
                    inc.decrementAndGet();
                    return true;
                },
                () -> true
            );

            assertThat(inc.get()).isZero();
        });

        assertThat(retryFailed.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void resultNoExceptionRetryNotExceeded() throws Throwable {
        RetryUtils.Instance<Boolean, Throwable> retrier = instance();
        AtomicInteger inc = new AtomicInteger(3);

        Boolean retry = retrier.run(
            (o) -> !o,
            () -> inc.getAndDecrement() == 1
        );

        assertThat(inc.get()).isZero();
        assertThat(retry).isTrue();
    }

    @Test
    void exceptionExceptionThrowRetryExceeded() {
        RetryUtils.Instance<Boolean, IOException> retrier = instance();
        AtomicInteger inc = new AtomicInteger(3);

        RetryUtils.RetryFailed retryFailed = assertThrows(RetryUtils.RetryFailed.class, () -> {
            retrier.run(
                IOException.class,
                () -> {
                    throw new IOException("test");
                }
            );

            assertThat(inc.get()).isZero();
        });

        assertThat(retryFailed.getAttemptCount()).isEqualTo(3);
    }

    @Test
    void exceptionNoExceptionRetryNotExceeded() throws Throwable {
        RetryUtils.Instance<Boolean, IOException> retrier = instance();
        AtomicInteger inc = new AtomicInteger(3);

        Boolean retry = retrier.run(
            IOException.class,
            () -> {
                boolean result = inc.getAndDecrement() == 1;
                if (!result) {
                    throw new IOException("test");
                }
                return result;
            }
        );

        assertThat(inc.get()).isZero();
        assertThat(retry).isTrue();
    }

    @Test
    void exceptionNoRetry() {
        RetryUtils.Instance<Boolean, ConcurrentModificationException> retrier = instance();

        assertThrows(IOException.class, () -> {
            retrier.run(
                ConcurrentModificationException.class,
                () -> {
                    throw new IOException("test");
                }
            );
        });
    }
}
