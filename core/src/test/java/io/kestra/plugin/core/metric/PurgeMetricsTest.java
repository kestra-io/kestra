package io.kestra.plugin.core.metric;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.devskiller.friendly_id.FriendlyId;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.MetricRepositoryInterface;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class PurgeMetricsTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private MetricRepositoryInterface metricRepository;

    @Test
    void run_with_full_arguments() throws Exception {
        String namespace = "purge.metric.ns";
        String flowId = "purge-metric-flow";
        Instant t1 = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant t2 = Instant.now().minus(1, ChronoUnit.DAYS);

        metricRepository.save(MetricEntry.builder()
            .tenantId(MAIN_TENANT)
            .namespace(namespace)
            .flowId(flowId)
            .executionId("exec-1")
            .taskId("task-1")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("m1")
            .value(1.0)
            .timestamp(t1)
            .build());

        metricRepository.save(MetricEntry.builder()
            .tenantId(MAIN_TENANT)
            .namespace(namespace)
            .flowId(flowId)
            .executionId("exec-2")
            .taskId("task-1")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("m2")
            .value(2.0)
            .timestamp(t2)
            .build());

        var purge = PurgeMetrics.builder()
            .namespace(Property.ofValue(namespace))
            .flowId(Property.ofValue(flowId))
            .startDate(Property.ofValue(ZonedDateTime.now().minusDays(15).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .endDate(Property.ofValue(ZonedDateTime.now().minusDays(5).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();

        var runContext = runContextFactory.of(flowId, namespace);
        var output = purge.run(runContext);

        assertThat(output.getCount()).isEqualTo(1);
    }

    @Test
    void run_with_no_namespace() throws Exception {
        String namespace = "purge.metric.ns.all";
        String flowId = "flow-all";
        Instant t1 = Instant.now().minus(10, ChronoUnit.DAYS);

        metricRepository.save(MetricEntry.builder()
            .tenantId(MAIN_TENANT)
            .namespace(namespace)
            .flowId(flowId)
            .executionId("exec-all-1")
            .taskId("task-1")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("m1")
            .value(1.0)
            .timestamp(t1)
            .build());

        var purge = PurgeMetrics.builder()
            .endDate(Property.ofValue(ZonedDateTime.now().minusDays(5).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();

        var runContext = runContextFactory.of(flowId, namespace);
        var output = purge.run(runContext);

        assertThat(output.getCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void run_with_flowId_without_namespace_should_throw() {
        var purge = PurgeMetrics.builder()
            .flowId(Property.ofValue("flow-without-ns"))
            .endDate(Property.ofValue(ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();

        var runContext = runContextFactory.of("flow-without-ns", "any.ns");

        assertThatThrownBy(() -> purge.run(runContext))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Property `namespace` is required when `flowId` is set.");
    }

    @Test
    void run_with_no_matching_metrics() throws Exception {
        var purge = PurgeMetrics.builder()
            .namespace(Property.ofValue("non.existent.namespace"))
            .endDate(Property.ofValue(ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();

        var runContext = runContextFactory.of("flow", "non.existent.namespace");
        var output = purge.run(runContext);

        assertThat(output.getCount()).isEqualTo(0);
    }
}
