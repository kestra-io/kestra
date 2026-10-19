package io.kestra.metrics.h2;

import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.ExecutionBacklogMetrics;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.queue.jdbc.client.JdbcQueueBacklogMetrics;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class H2QueryMetricsDisabledTest {
    @Inject
    ApplicationContext applicationContext;
    @Inject
    MetricRegistry metrics;

    @Test
    void shouldNotCreatePollersOrExportBacklogsByDefault() {
        assertThat(applicationContext.containsBean(ExecutionBacklogMetrics.class)).isFalse();
        assertThat(applicationContext.containsBean(JdbcQueueBacklogMetrics.class)).isFalse();
        assertThat(metrics.findGauges(MetricRegistry.METRIC_EXECUTOR_EXECUTION_PENDING)).isEmpty();
        assertThat(metrics.findGauges(MetricRegistry.METRIC_QUEUE_MESSAGE_PENDING)).isEmpty();
    }
}
