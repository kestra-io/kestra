package io.kestra.core.metrics;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.tenant.TenantService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExecutionBacklogMetricsTest {
    @Test
    void shouldCacheCountsByTenantAndResetDrainedBacklogs() throws IOException {
        var repository = mock(ExecutionRepositoryInterface.class);
        var tenants = mock(TenantService.class);
        when(tenants.listTenants()).thenReturn(List.of("main", "other"));
        when(repository.fetchData(eq("main"), any(), isNull(), isNull(), any()))
            .thenReturn(rows(Map.of("state", "CREATED", "count", 2L), Map.of("state", "QUEUED", "count", 3L)))
            .thenReturn(rows());
        when(repository.fetchData(eq("other"), any(), isNull(), isNull(), any()))
            .thenReturn(rows(Map.of("state", "CREATED", "count", 4L)))
            .thenReturn(rows(Map.of("state", "QUEUED", "count", 1L)));
        var metrics = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig());
        var poller = new ExecutionBacklogMetrics(repository, tenants, metrics);

        poller.refresh();
        clearInvocations(repository);

        assertThat(value(metrics, "main", "CREATED")).isEqualTo(2);
        assertThat(value(metrics, "main", "QUEUED")).isEqualTo(3);
        assertThat(value(metrics, "other", "CREATED")).isEqualTo(4);
        assertThat(value(metrics, "other", "QUEUED")).isZero();
        verifyNoInteractions(repository);

        poller.refresh();

        assertThat(value(metrics, "main", "CREATED")).isZero();
        assertThat(value(metrics, "main", "QUEUED")).isZero();
        assertThat(value(metrics, "other", "CREATED")).isZero();
        assertThat(value(metrics, "other", "QUEUED")).isEqualTo(1);

        when(tenants.listTenants()).thenReturn(List.of("main"));
        poller.refresh();
        assertThat(value(metrics, "other", "QUEUED")).isZero();
    }

    @Test
    void shouldPreserveTheLastSampleWhenOneTenantQueryFails() throws IOException {
        var repository = mock(ExecutionRepositoryInterface.class);
        var tenants = mock(TenantService.class);
        when(tenants.listTenants()).thenReturn(List.of("main", "other"));
        when(repository.fetchData(eq("main"), any(), isNull(), isNull(), any()))
            .thenReturn(rows(Map.of("state", "CREATED", "count", 2L)))
            .thenThrow(new IOException("Repository is unavailable."));
        when(repository.fetchData(eq("other"), any(), isNull(), isNull(), any()))
            .thenReturn(rows(Map.of("state", "QUEUED", "count", 3L)))
            .thenReturn(rows());
        var metrics = new MetricRegistry(new SimpleMeterRegistry(), new MetricConfig());
        var poller = new ExecutionBacklogMetrics(repository, tenants, metrics);

        poller.refresh();
        poller.refresh();

        assertThat(value(metrics, "main", "CREATED")).isEqualTo(2);
        assertThat(value(metrics, "other", "QUEUED")).isZero();
    }

    @SafeVarargs
    private static ArrayListTotal<Map<String, Object>> rows(Map<String, Object>... rows) {
        return new ArrayListTotal<>(List.of(rows), rows.length);
    }

    private double value(MetricRegistry metrics, String tenantId, String state) {
        return metrics.findGauges(MetricRegistry.METRIC_EXECUTOR_EXECUTION_PENDING).stream()
            .filter(
                gauge -> tenantId.equals(gauge.getId().getTag(MetricRegistry.TAG_TENANT_ID))
                    && state.equals(gauge.getId().getTag(MetricRegistry.TAG_STATE))
            )
            .findFirst().orElseThrow().value();
    }
}
