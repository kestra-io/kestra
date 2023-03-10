package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;

import java.util.List;

public interface MetricRepositoryInterface extends SaveRepositoryInterface<MetricEntry> {
    List<MetricEntry> findByExecutionId(String id);

    List<MetricEntry> findByExecutionIdAndTaskId(String executionId, String taskId);

    List<MetricEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId);

    Integer purge(Execution execution);
}
