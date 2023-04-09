package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.micronaut.data.model.Pageable;

import java.util.function.Function;

public interface MetricRepositoryInterface extends SaveRepositoryInterface<MetricEntry> {
    ArrayListTotal<MetricEntry> findByExecutionId(String id, Pageable pageable);

    ArrayListTotal<MetricEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Pageable pageable);

    ArrayListTotal<MetricEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Pageable pageable);

    Integer purge(Execution execution);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return s -> s;
    }
}
