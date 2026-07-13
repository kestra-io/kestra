package io.kestra.queue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.queue.poller.QueuePollerConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractPollingSubscriberTest {

    private QueueService queueService;
    private MetricRegistry metricRegistry;
    private IgnoreExecutionService ignoreExecutionService;

    @BeforeEach
    void setUp() {
        queueService = mock(QueueService.class);
        metricRegistry = mock(MetricRegistry.class);
        ignoreExecutionService = mock(IgnoreExecutionService.class);
        when(metricRegistry.timer(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(mock(io.micrometer.core.instrument.Timer.class));
        // run the polling loop on a real thread, like the real QueueService does
        doAnswer(invocation ->
        {
            Thread.ofVirtual().start((Runnable) invocation.getArgument(0));
            return null;
        }).when(queueService).execute(any());
    }

    @Test
    void shouldNotStartThePollingLoopWhenSubscribingADeactivatedSubscriber() throws InterruptedException {
        // Given: a subscriber that was NOT marked ready (e.g. closed while the component was subscribing)
        var subscriber = new TestPollingSubscriber(queueService, metricRegistry, ignoreExecutionService);

        // When
        subscriber.subscribe(either ->
        {
        });

        // Then: the loop exits without ever polling
        assertThat(subscriber.polled.await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(subscriber.isActive()).isFalse();
    }

    @Test
    void shouldStartPollingLoopWhenReady() throws InterruptedException {
        // Given
        var subscriber = new TestPollingSubscriber(queueService, metricRegistry, ignoreExecutionService);
        subscriber.markReady();

        // When
        subscriber.subscribe(either ->
        {
        });

        // Then: the polling loop runs
        assertThat(subscriber.polled.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(subscriber.isActive()).isTrue();

        subscriber.close();
        assertThat(subscriber.isActive()).isFalse();
    }

    /**
     * Minimal concrete implementation for testing AbstractPollingSubscriber.
     */
    static class TestPollingSubscriber extends AbstractPollingSubscriber<TestEvent> {
        final CountDownLatch polled = new CountDownLatch(1);

        TestPollingSubscriber(QueueService queueService, MetricRegistry metricRegistry, IgnoreExecutionService ignoreExecutionService) {
            super(
                TestEvent.class, "test-queue", queueService, metricRegistry, ignoreExecutionService,
                new QueuePollerConfiguration(Duration.ofMillis(10), Duration.ofMillis(10), Duration.ofSeconds(1), 10, 1, false)
            );
        }

        @Override
        protected Integer poll(Consumer<byte[]> messageConsumer) {
            polled.countDown();
            return 0;
        }

        @Override
        protected Integer pollBatch(Consumer<List<byte[]>> messageConsumer) {
            polled.countDown();
            return 0;
        }
    }

    record TestEvent(String key) implements Event {
    }
}
