package io.kestra.queue.jdbc.client;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcQueueBacklogMetricsTest {
    @Mock
    private JdbcQueueClient client;
    @Mock
    private DispatchQueueInterface<Execution> dispatchQueue;
    @Mock
    private BroadcastQueueInterface<FlowInterface> broadcastQueue;

    @Test
    void shouldCachePendingCountsAndExcludeRetainedBroadcastMessages() {
        when(dispatchQueue.queueName()).thenReturn("executions");
        when(client.pendingMessages(List.of("executions")))
            .thenReturn(Map.of("executions", 5L)).thenReturn(Map.of());
        var metrics = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig());
        var poller = new JdbcQueueBacklogMetrics(client, List.of(dispatchQueue, broadcastQueue), metrics);
        assertThat(metrics.findGauge(MetricRegistry.METRIC_QUEUE_MESSAGE_PENDING).value()).isNaN();

        poller.refresh();
        clearInvocations(client);

        var gauges = metrics.findGauges(MetricRegistry.METRIC_QUEUE_MESSAGE_PENDING);
        assertThat(gauges).hasSize(1);
        var gauge = gauges.iterator().next();
        assertThat(gauge.getId().getTag(MetricRegistry.TAG_QUEUE_NAME)).isEqualTo("executions");
        assertThat(gauge.value()).isEqualTo(5);
        verifyNoInteractions(client);

        poller.refresh();
        assertThat(gauge.value()).isZero();
    }

    @Test
    void shouldPreserveTheLastSampleWhenTheQueueQueryFails() {
        when(dispatchQueue.queueName()).thenReturn("executions");
        when(client.pendingMessages(List.of("executions")))
            .thenReturn(Map.of("executions", 5L)).thenThrow(new IllegalStateException("Queue is unavailable."));
        var metrics = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig());
        var poller = new JdbcQueueBacklogMetrics(client, List.of(dispatchQueue), metrics);

        poller.refresh();
        poller.refresh();

        assertThat(metrics.findGauge(MetricRegistry.METRIC_QUEUE_MESSAGE_PENDING).value()).isEqualTo(5);
    }
}
