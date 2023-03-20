package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.micronaut.data.model.Pageable;

import java.util.List;

public interface MetricRepositoryInterface extends SaveRepositoryInterface<MetricEntry> {
    ArrayListTotal<MetricEntry> findByExecutionId(String id, Pageable pageable);

    ArrayListTotal<MetricEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Pageable pageable);

    ArrayListTotal<MetricEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Pageable pageable);

    Integer purge(Execution execution);
}
