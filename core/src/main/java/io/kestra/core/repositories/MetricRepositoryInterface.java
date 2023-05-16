package io.kestra.core.repositories;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;

public interface MetricRepositoryInterface extends SaveRepositoryInterface<MetricEntry> {
    ArrayListTotal<MetricEntry> findByExecutionId(String id, Pageable pageable);

    ArrayListTotal<MetricEntry> findByExecutionIdAndTaskId(String executionId, String taskId, Pageable pageable);

    ArrayListTotal<MetricEntry> findByExecutionIdAndTaskRunId(String executionId, String taskRunId, Pageable pageable);

    List<String> flowMetrics(String namespace, String flowId);

    List<String> taskMetrics(String namespace, String flowId, String taskId);

    List<String> tasksWithMetrics(String namespace, String flowId);

    MetricAggregations aggregateByFlowId(String namespace, String flowId, @Nullable String taskId, String metric, ZonedDateTime startDate, ZonedDateTime endDate, String aggregation);

    Integer purge(Execution execution);

    default Function<String, String> sortMapping() throws IllegalArgumentException {
        return s -> s;
    }
}
