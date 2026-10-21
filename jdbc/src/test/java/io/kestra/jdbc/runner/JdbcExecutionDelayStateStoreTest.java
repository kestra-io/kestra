package io.kestra.jdbc.runner;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.AbstractExecutionDelayStateStoreTest;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class JdbcExecutionDelayStateStoreTest extends AbstractExecutionDelayStateStoreTest {

    @Test
    void shouldRefuseToSaveDelayWhileItIsBeingProcessed() throws Exception {
        ExecutionDelay delay = delay(IdUtils.create(), Instant.now().minusSeconds(60));
        ExecutionDelay other = delay(IdUtils.create(), Instant.now().plusSeconds(3600));
        executionDelayStateStore.save(delay);
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> poller = CompletableFuture.runAsync(() -> executionDelayStateStore.processExpired(Instant.now(), consumed ->
        {
            if (consumed.getExecutionId().equals(delay.getExecutionId())) {
                processing.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }));

        try {
            assertThat(processing.await(30, TimeUnit.SECONDS)).isTrue();

            assertThat(executionDelayStateStore.saveUnlessProcessing(delay(delay.getExecutionId(), Instant.now().plusSeconds(60)))).isFalse();
            assertThat(executionDelayStateStore.saveUnlessProcessing(other)).isTrue();
        } finally {
            release.countDown();
            poller.get(30, TimeUnit.SECONDS);
        }
    }
}
