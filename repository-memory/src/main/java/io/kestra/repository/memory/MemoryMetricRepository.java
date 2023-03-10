package io.kestra.repository.memory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.repositories.MetricRepositoryInterface;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryMetricRepository implements MetricRepositoryInterface {
    private final List<MetricEntry> metrics = new ArrayList<>();

    @Override
    public List<MetricEntry> findByExecutionId(String id) {
        return metrics.stream().filter(metrics -> metrics.getExecutionId().equals(id)).collect(Collectors.toList());
    }

    @Override
    public List<MetricEntry> findByExecutionIdAndTaskId(String executionId, String taskId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<MetricEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId) {
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
