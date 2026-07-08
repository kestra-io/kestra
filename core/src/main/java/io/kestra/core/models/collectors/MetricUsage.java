package io.kestra.core.models.collectors;

import java.util.Optional;

import io.kestra.core.metrics.MetricRegistry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class MetricUsage {
    private final ExecutorMetric executor;
    private final QueueMetric queue;
    private final WorkerMetric worker;
    private final ServerMetric server;

    public static MetricUsage of(MetricRegistry metricRegistry) {
        return MetricUsage.builder()
            .executor(ExecutorMetric.of(metricRegistry))
            .queue(QueueMetric.of(metricRegistry))
            .worker(WorkerMetric.of(metricRegistry))
            .server(ServerMetric.of(metricRegistry))
            .build();
    }

    static Double fromCounter(MetricRegistry metricRegistry, String metricName) {
        return Optional.ofNullable(metricRegistry.findCounter(metricName)).map(Counter::count).orElse(null);
    }

    static Double fromGauge(MetricRegistry metricRegistry, String metricName) {
        return Optional.ofNullable(metricRegistry.findGauge(metricName)).map(Gauge::value).orElse(null);
    }

    @SuperBuilder
    @Getter
    public static class ExecutorMetric {
        private final Double killEventCount;
        private final Double workerJobResubmitCount;
        private final Double slaViolationCount;
        private final Double threadCount;
        private final Double executionQueuedCount;

        static ExecutorMetric of(MetricRegistry metricRegistry) {
            return ExecutorMetric.builder()
                .killEventCount(fromCounter(metricRegistry, MetricRegistry.METRIC_EXECUTOR_KILLED_COUNT))
                .workerJobResubmitCount(fromCounter(metricRegistry, MetricRegistry.METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT))
                .slaViolationCount(fromCounter(metricRegistry, MetricRegistry.METRIC_EXECUTOR_SLA_VIOLATION_COUNT))
                .threadCount(fromGauge(metricRegistry, MetricRegistry.METRIC_EXECUTOR_THREAD_COUNT))
                .executionQueuedCount(fromCounter(metricRegistry, MetricRegistry.METRIC_EXECUTOR_EXECUTION_QUEUED_COUNT))
                .build();
        }
    }

    @SuperBuilder
    @Getter
    public static class QueueMetric {
        private final Double bigMessageCount;

        static QueueMetric of(MetricRegistry metricRegistry) {
            return QueueMetric.builder()
                .bigMessageCount(fromCounter(metricRegistry, MetricRegistry.METRIC_QUEUE_MESSAGE_BIG_TOTAL))
                .build();
        }
    }

    @SuperBuilder
    @Getter
    public static class WorkerMetric {
        private final Double threadCount;

        static WorkerMetric of(MetricRegistry metricRegistry) {
            return WorkerMetric.builder()
                .threadCount(fromGauge(metricRegistry, MetricRegistry.METRIC_WORKER_JOB_THREAD_COUNT))
                .build();
        }
    }

    @SuperBuilder
    @Getter
    public static class ServerMetric {
        private final Double maintenanceEnterCount;

        static ServerMetric of(MetricRegistry metricRegistry) {
            return ServerMetric.builder()
                .maintenanceEnterCount(fromCounter(metricRegistry, MetricRegistry.METRIC_MAINTENANCE_ENTER_COUNT))
                .build();
        }
    }
}
