package io.kestra.jdbc.queue;

import org.junit.jupiter.api.Test;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.queue.AbstractBroadcastQueueTest;
import io.kestra.queue.jdbc.client.JdbcQueueCleaner;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcQueueCleanerTest {
    @Inject
    private JdbcQueueCleaner jdbcQueueCleaner;

    @Inject
    private BroadcastQueueInterface<AbstractBroadcastQueueTest.TestBroadcast> testQueue;

    @Test
    protected void shouldClean() throws QueueException, InterruptedException {
        var message = new AbstractBroadcastQueueTest.TestBroadcast("key", 1);
        testQueue.emit(message);

        Thread.sleep(100); // wait a little as queue cleaner is datetime based

        long cleaned = jdbcQueueCleaner.deleteQueue();
        assertThat(cleaned).isGreaterThanOrEqualTo(1);
    }
}
