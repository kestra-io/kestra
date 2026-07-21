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
    private final WebserverMetric webserver;

    public static MetricUsage of(MetricRegistry metricRegistry) {
        return MetricUsage.builder()
            .executor(ExecutorMetric.of(metricRegistry))
            .queue(QueueMetric.of(metricRegistry))
            .worker(WorkerMetric.of(metricRegistry))
            .server(ServerMetric.of(metricRegistry))
            .webserver(WebserverMetric.of(metricRegistry))
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

    @SuperBuilder
    @Getter
    public static class WebserverMetric {
        private final Double restartCount;
        private final Double replayCount;
        private final Double pauseCount;
        private final Double resumeCount;
        private final Double resumeFromBreakpointCount;
        private final Double forceRunCount;
        private final Double killCount;
        private final Double changeStatusCount;
        private final Double updateLabelsCount;
        private final Double unqueueCount;
        private final Double taskRunChangeStateCount;

        static WebserverMetric of(MetricRegistry metricRegistry) {
            return WebserverMetric.builder()
                .restartCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_RESTART_TOTAL))
                .replayCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_REPLAY_TOTAL))
                .pauseCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_PAUSE_TOTAL))
                .resumeCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_RESUME_TOTAL))
                .resumeFromBreakpointCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_RESUME_FROM_BREAKPOINT_TOTAL))
                .forceRunCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_FORCE_RUN_TOTAL))
                .killCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_KILL_TOTAL))
                .changeStatusCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_CHANGE_STATUS_TOTAL))
                .updateLabelsCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_UPDATE_LABELS_TOTAL))
                .unqueueCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_EXECUTION_UNQUEUE_TOTAL))
                .taskRunChangeStateCount(fromCounter(metricRegistry, MetricRegistry.METRIC_WEBSERVER_TASKRUN_CHANGE_STATE_TOTAL))
                .build();
        }
    }
}
