package io.micronaut.retry.intercept;

import io.kestra.core.annotations.Retryable;
import io.micronaut.retry.event.RetryEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.channels.AlreadyBoundException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class OverrideRetryInterceptorTest {
    @Inject
    RetryEvents retryEvents;

    @Inject
    TestRetry retry;

    @Test
    void test() {
        assertThrows(AlreadyBoundException.class, retry::failedMethod);

        assertThat(retryEvents.count, is(5));
    }

    @Singleton
    public static class TestRetry {
        @Retryable(delay = "10ms", multiplier = "2.0")
        public String failedMethod() {
            throw new AlreadyBoundException();
        }
    }

    @Singleton
    @Slf4j
    public static class RetryEvents {
        public int count = 0;
        @EventListener
        void onRetry(final RetryEvent event) {
            this.count++;
        }
    }
}