package io.kestra.jdbc.queue;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.queue.AbstractBroadcastQueueTest;
import io.kestra.queue.jdbc.client.JdbcQueueCleaner;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcQueueCleanerTest {
    @Inject
    private JdbcQueueCleaner jdbcQueueCleaner;

    @Inject
    private BroadcastQueueInterface<AbstractBroadcastQueueTest.TestBroadcast> testQueue;

    @Test
    public void shouldClean() throws QueueException {
        var message = new AbstractBroadcastQueueTest.TestBroadcast("key", 1);
        testQueue.emit(message);

        long cleaned = jdbcQueueCleaner.deleteQueue();
        assertThat(cleaned).isGreaterThanOrEqualTo(1);
    }
}
