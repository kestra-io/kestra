package io.kestra.core.reporter.reports;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.plugin.core.http.Trigger;
import io.kestra.plugin.core.log.Log;
import io.kestra.plugin.core.trigger.Schedule;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class PluginMetricReportTest {
    
    @Inject
    MetricRegistry metricRegistry;
    
    @Inject
    PluginMetricReport pluginMetricReport;
    
    @Test
    void shouldGetReport() {
        // Given
        metricRegistry.timer(MetricRegistry.METRIC_WORKER_ENDED_DURATION, MetricRegistry.METRIC_WORKER_ENDED_DURATION_DESCRIPTION, MetricRegistry.TAG_TASK_TYPE, Log.class.getName())
            .record(() -> Duration.ofSeconds(1));
        metricRegistry.timer(MetricRegistry.METRIC_WORKER_TRIGGER_DURATION, MetricRegistry.METRIC_WORKER_TRIGGER_DURATION_DESCRIPTION, MetricRegistry.TAG_TRIGGER_TYPE, Trigger.class.getName())
            .record(() -> Duration.ofSeconds(1));
        metricRegistry.timer(MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION, MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION_DESCRIPTION, MetricRegistry.TAG_TRIGGER_TYPE, Schedule.class.getName())
            .record(() -> Duration.ofSeconds(1));
        
        // When
        PluginMetricReport.PluginMetricEvent event = pluginMetricReport.report(Instant.now());
        
        // Then
        assertThat(event.pluginMetrics()).hasSize(3);
    }
}