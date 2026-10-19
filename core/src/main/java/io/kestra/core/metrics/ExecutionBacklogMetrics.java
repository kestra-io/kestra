package io.kestra.core.metrics;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.core.dashboard.data.Executions;

import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.model.Pageable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@RequiresMetrics
@Requires(property = "kestra.metrics.query.enabled", value = "true")
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|WEBSERVER|STANDALONE)", defaultValue = "STANDALONE")
@Slf4j
public class ExecutionBacklogMetrics {
    private static final List<State.Type> STATES = List.of(State.Type.CREATED, State.Type.QUEUED);
    private static final Executions<ColumnDescriptor<Executions.Fields>> FILTER = Executions.<ColumnDescriptor<Executions.Fields>> builder()
        .columns(
            Map.of(
                "state", ColumnDescriptor.<Executions.Fields> builder().field(Executions.Fields.STATE).build(),
                "count", ColumnDescriptor.<Executions.Fields> builder().field(Executions.Fields.ID).agg(AggregationType.COUNT).build()
            )
        )
        .where(
            List.of(
                In.<Executions.Fields> builder()
                    .field(Executions.Fields.STATE)
                    .values(List.of(State.Type.CREATED.name(), State.Type.QUEUED.name()))
                    .build()
            )
        )
        .build();

    private final ExecutionRepositoryInterface executionRepository;
    private final TenantService tenantService;
    private final MetricRegistry metricRegistry;
    private final ConcurrentMap<ExecutionState, AtomicLong> counts = new ConcurrentHashMap<>();

    public ExecutionBacklogMetrics(ExecutionRepositoryInterface executionRepository, TenantService tenantService, MetricRegistry metricRegistry) {
        this.executionRepository = executionRepository;
        this.tenantService = tenantService;
        this.metricRegistry = metricRegistry;
    }

    @Scheduled(initialDelay = "${kestra.metrics.query.interval:1m}", fixedDelay = "${kestra.metrics.query.interval:1m}")
    public void refresh() {
        Set<String> tenants = Set.copyOf(tenantService.listTenants());
        counts.forEach((key, count) ->
        {
            if (!tenants.contains(key.tenantId())) {
                count.set(0);
            }
        });
        for (String tenantId : tenants) {
            try {
                var rows = executionRepository.fetchData(tenantId, FILTER, null, null, Pageable.unpaged());
                for (State.Type state : STATES) {
                    long total = rows.stream()
                        .filter(row -> state.name().equals(row.get("state")))
                        .mapToLong(row -> ((Number) row.get("count")).longValue())
                        .sum();
                    counts.computeIfAbsent(
                        new ExecutionState(tenantId, state), key -> metricRegistry.gauge(
                            MetricRegistry.METRIC_EXECUTOR_EXECUTION_PENDING,
                            MetricRegistry.METRIC_EXECUTOR_EXECUTION_PENDING_DESCRIPTION,
                            new AtomicLong(),
                            MetricRegistry.TAG_TENANT_ID, tenantId,
                            MetricRegistry.TAG_STATE, state.name()
                        )
                    ).set(total);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Unable to refresh execution backlog metrics for tenant '{}'.", tenantId, e);
            }
        }
    }

    private record ExecutionState(String tenantId, State.Type state) {
    }
}
