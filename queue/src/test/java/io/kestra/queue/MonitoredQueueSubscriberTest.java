package io.kestra.queue;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.Event;
import io.kestra.core.utils.Either;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoredQueueSubscriberTest {

    private static final String QUEUE_NAME = "test_queue";

    private MeterRegistry meterRegistry;
    private MetricRegistry metricRegistry;
    @SuppressWarnings("rawtypes")
    private AbstractQueue queue;
    @SuppressWarnings("unchecked")
    private QueueSubscriber<TestEvent> delegate;
    private MonitoredQueueSubscriber<TestEvent> monitored;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricRegistry = new MetricRegistry(meterRegistry, new MetricConfig());
        queue = mock(AbstractQueue.class);
        when(queue.queueName()).thenReturn(QUEUE_NAME);
        delegate = mock(QueueSubscriber.class);
        monitored = new MonitoredQueueSubscriber<>(delegate, queue, metricRegistry);
    }

    private double pauseCount() {
        return meterRegistry.get(MetricRegistry.METRIC_QUEUE_SUBSCRIBERS_PAUSED_TOTAL).counter().count();
    }

    private double resumeCount() {
        return meterRegistry.get(MetricRegistry.METRIC_QUEUE_SUBSCRIBERS_RESUMED_TOTAL).counter().count();
    }

    @Test
    void shouldIncrementPauseCounterAndDelegate() {
        // When
        monitored.pause();
        monitored.pause();

        // Then
        assertThat(pauseCount()).isEqualTo(2.0);
        assertThat(resumeCount()).isEqualTo(0.0);
        verify(delegate, times(2)).pause();
    }

    @Test
    void shouldIncrementResumeCounterAndDelegate() {
        // When
        monitored.resume();
        monitored.resume();
        monitored.resume();

        // Then
        assertThat(resumeCount()).isEqualTo(3.0);
        assertThat(pauseCount()).isEqualTo(0.0);
        verify(delegate, times(3)).resume();
    }

    @Test
    void shouldTagCountersWithQueueName() {
        // When
        monitored.pause();
        monitored.resume();

        // Then
        assertThat(meterRegistry.get(MetricRegistry.METRIC_QUEUE_SUBSCRIBERS_PAUSED_TOTAL)
            .tag(MetricRegistry.TAG_QUEUE_NAME, QUEUE_NAME)
            .counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get(MetricRegistry.METRIC_QUEUE_SUBSCRIBERS_RESUMED_TOTAL)
            .tag(MetricRegistry.TAG_QUEUE_NAME, QUEUE_NAME)
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void subscribeShouldReturnWrapperNotDelegate() {
        // Given
        Consumer<Either<TestEvent, DeserializationException>> consumer = e -> {};

        // When
        QueueSubscriber<TestEvent> returned = monitored.subscribe(consumer);

        // Then
        assertThat(returned).isSameAs(monitored);
        verify(delegate).subscribe(consumer);
    }

    @Test
    void subscribeBatchShouldUseDelegateNativeBatchAndReturnWrapper() {
        // Given
        Consumer<List<Either<TestEvent, DeserializationException>>> batchConsumer = items -> {};

        // When
        QueueSubscriber<TestEvent> returned = monitored.subscribeBatch(batchConsumer);

        // Then
        assertThat(returned).isSameAs(monitored);
        verify(delegate).subscribeBatch(batchConsumer);
        // Must hit the delegate's native batch method, not the default routing through subscribe()
        verify(delegate, never()).subscribe(any());
    }

    @Test
    void closeShouldDelegateAndUntrack() {
        // When
        monitored.close();

        // Then
        verify(delegate).close();
        verify(queue).untrackSubscriber(monitored);
    }

    @Test
    void closeShouldUntrackEvenIfDelegateThrows() {
        // Given
        doThrow(new RuntimeException("boom")).when(delegate).close();

        // When / Then
        assertThatThrownBy(monitored::close)
            .isInstanceOf(RuntimeException.class)
            .hasMessage("boom");
        verify(queue).untrackSubscriber(monitored);
    }

    @Test
    void isActiveShouldDelegate() {
        // Given
        when(delegate.isActive()).thenReturn(true).thenReturn(false);

        // Then
        assertThat(monitored.isActive()).isTrue();
        assertThat(monitored.isActive()).isFalse();
    }

    private record TestEvent(String key) implements Event {
    }
}
