package io.kestra.core.utils;

import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class RetryUtilsTest {
    @Inject
    RetryUtils retryUtils;

    private <T, E extends Throwable> RetryUtils.Instance<T, E> instance() {
        return retryUtils.of(Constant.builder()
            .interval(Duration.ofMillis(10))
            .maxAttempt(3)
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

            assertThat(inc.get(), is(0));
        });

        assertThat(retryFailed.getAttemptCount(), is(3));
    }

    @Test
    void resultNoExceptionRetryNotExceeded() throws Throwable {
        RetryUtils.Instance<Boolean, Throwable> retrier = instance();
        AtomicInteger inc = new AtomicInteger(3);

        Boolean retry = retrier.run(
            (o) -> !o,
            () -> inc.getAndDecrement() == 1
        );

        assertThat(inc.get(), is(0));
        assertThat(retry, is(true));
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

            assertThat(inc.get(), is(0));
        });

        assertThat(retryFailed.getAttemptCount(), is(3));
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

        assertThat(inc.get(), is(0));
        assertThat(retry, is(true));
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
