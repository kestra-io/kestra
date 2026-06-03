package io.kestra.controller.grpc.services;

import java.util.List;
import java.util.Set;

import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.worker.QueueSubscription;

import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkerCapacityMetricsPublisher} focused on gauge lifecycle:
 * registration on first contributor, persistence across additional contributors,
 * removal on last disconnect, and subscription-change deltas. Per-policy reservation
 * math is covered by the EE-side reservation test.
 */
class WorkerCapacityMetricsPublisherTest {

    private SimpleMeterRegistry meterRegistry;
    private MetricRegistry metricRegistry;
    private WorkerJobDispatcher dispatcher;
    private WorkerCapacityMetricsPublisher publisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricRegistry = new MetricRegistry(meterRegistry, new MetricConfig());
        dispatcher = Mockito.mock(WorkerJobDispatcher.class);
        publisher = new WorkerCapacityMetricsPublisher(metricRegistry);
        publisher.init(dispatcher);
    }

    @Test
    void shouldRegisterGaugesWhenFirstWorkerForPairConnects() {
        WorkerStreamContext<WorkerJobResponse> w = context("w1", "group-a", 10,
            new QueueSubscription("gpu", QueueSubscription.NO_RESERVATION));
        when(dispatcher.activeStreams()).thenReturn(List.of(w));

        publisher.onWorkerRegistered(w);

        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "gpu"))
            .isNotNull();
        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_USED, "group-a", "gpu"))
            .isNotNull();
        assertThat(findGroupGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_ALLOCATED, "group-a"))
            .isNotNull();
        assertThat(findGroupGauge(MetricRegistry.METRIC_CONTROLLER_WORKER_GROUP_JOB_INFLIGHT, "group-a"))
            .isNotNull();
    }

    @Test
    void shouldKeepGaugesWhenAdditionalWorkerForSamePairConnects() {
        WorkerStreamContext<WorkerJobResponse> w1 = context("w1", "group-a", 10,
            new QueueSubscription("gpu", QueueSubscription.NO_RESERVATION));
        WorkerStreamContext<WorkerJobResponse> w2 = context("w2", "group-a", 20,
            new QueueSubscription("gpu", QueueSubscription.NO_RESERVATION));
        when(dispatcher.activeStreams()).thenReturn(List.of(w1, w2));

        publisher.onWorkerRegistered(w1);
        publisher.onWorkerRegistered(w2);
        // Now disconnect only w1.
        when(dispatcher.activeStreams()).thenReturn(List.of(w2));
        publisher.onWorkerUnregistered(w1);

        // Gauge must still exist — w2 is still contributing.
        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "gpu"))
            .isNotNull();
        assertThat(findGroupGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_ALLOCATED, "group-a"))
            .isNotNull();
    }

    @Test
    void shouldRemoveGaugesWhenLastWorkerForPairDisconnects() {
        WorkerStreamContext<WorkerJobResponse> w = context("w1", "group-a", 10,
            new QueueSubscription("gpu", QueueSubscription.NO_RESERVATION));
        when(dispatcher.activeStreams()).thenReturn(List.of(w));
        publisher.onWorkerRegistered(w);
        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "gpu"))
            .isNotNull();

        when(dispatcher.activeStreams()).thenReturn(List.of());
        publisher.onWorkerUnregistered(w);

        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "gpu"))
            .isNull();
        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_USED, "group-a", "gpu"))
            .isNull();
        assertThat(findGroupGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_ALLOCATED, "group-a"))
            .isNull();
        assertThat(findGroupGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_USED, "group-a"))
            .isNull();
        assertThat(findGroupGauge(MetricRegistry.METRIC_CONTROLLER_WORKER_GROUP_JOB_INFLIGHT, "group-a"))
            .isNull();
    }

    @Test
    void shouldAddAndRemoveGaugesWhenSubscriptionsChange() {
        WorkerStreamContext<WorkerJobResponse> w = context("w1", "group-a", 10,
            new QueueSubscription("gpu", QueueSubscription.NO_RESERVATION));
        when(dispatcher.activeStreams()).thenReturn(List.of(w));
        publisher.onWorkerRegistered(w);
        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "gpu"))
            .isNotNull();

        // Swap "gpu" -> "tpu" on the same worker.
        w.replaceQueueSubscriptions(List.of(new QueueSubscription("tpu", QueueSubscription.NO_RESERVATION)));
        publisher.onWorkerSubscriptionsChanged(w, Set.of("tpu"), Set.of("gpu"));

        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "gpu"))
            .isNull();
        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "tpu"))
            .isNotNull();
    }

    @Test
    void shouldKeepGroupGaugesAliveWhenOnlyOneQueueIsDropped() {
        WorkerStreamContext<WorkerJobResponse> w = context("w1", "group-a", 10,
            new QueueSubscription("gpu", QueueSubscription.NO_RESERVATION),
            new QueueSubscription("cpu", QueueSubscription.NO_RESERVATION));
        when(dispatcher.activeStreams()).thenReturn(List.of(w));
        publisher.onWorkerRegistered(w);

        // Drop "cpu", keep "gpu".
        w.replaceQueueSubscriptions(List.of(new QueueSubscription("gpu", QueueSubscription.NO_RESERVATION)));
        publisher.onWorkerSubscriptionsChanged(w, Set.of(), Set.of("cpu"));

        // (group-a, cpu) gauges removed, (group-a, gpu) still there, group-level still there.
        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "cpu"))
            .isNull();
        assertThat(findSubGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SUBSCRIPTION_ALLOCATED, "group-a", "gpu"))
            .isNotNull();
        assertThat(findGroupGauge(MetricRegistry.METRIC_CONTROLLER_CAPACITY_SHARED_ALLOCATED, "group-a"))
            .isNotNull();
    }

    private io.micrometer.core.instrument.Gauge findSubGauge(String name, String group, String queue) {
        return meterRegistry.find(name)
            .tag(MetricRegistry.TAG_WORKER_GROUP, group)
            .tag(MetricRegistry.TAG_WORKER_QUEUE, queue)
            .gauge();
    }

    private io.micrometer.core.instrument.Gauge findGroupGauge(String name, String group) {
        return meterRegistry.find(name)
            .tag(MetricRegistry.TAG_WORKER_GROUP, group)
            .gauge();
    }

    @SuppressWarnings("unchecked")
    private static WorkerStreamContext<WorkerJobResponse> context(String id, String groupId, int max, QueueSubscription... subs) {
        StreamObserver<WorkerJobResponse> obs = Mockito.mock(StreamObserver.class);
        WorkerStreamContext<WorkerJobResponse> ctx = new WorkerStreamContext<>(id, groupId, List.of(subs), max, obs);
        ctx.setPermits(max);
        return ctx;
    }
}
