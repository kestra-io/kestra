package io.kestra.repository.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Singleton;

import jakarta.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryMetricRepository implements MetricRepositoryInterface {
    private final List<MetricEntry> metrics = new ArrayList<>();

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionId(String tenantId, String id, Pageable pageable) {
        var results = metrics.stream()
            .filter(metric -> metric.getExecutionId().equals(id))
            .filter(metric -> (tenantId == null && metric.getTenantId() == null) || (tenantId != null && tenantId.equals(metric.getTenantId())))
            .collect(Collectors.toList());
        return new ArrayListTotal<>(results, results.size());
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionIdAndTaskId(String tenantId, String executionId, String taskId, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ArrayListTotal<MetricEntry> findByExecutionIdAndTaskRunId(String tenantId, String executionId, String taskRunId, Pageable pageable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> flowMetrics(String tenantId, String namespace, String flowId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> taskMetrics(String tenantId, String namespace, String flowId, String taskId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> tasksWithMetrics(String tenantId, String namespace, String flowId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MetricAggregations aggregateByFlowId(String tenantId, String namespace, String flowId, @Nullable String taskId, String metric, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, String aggregation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Integer purge(Execution execution) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MetricEntry save(MetricEntry metricEntry) {
        metrics.add(metricEntry);
        return metricEntry;
    }
}
