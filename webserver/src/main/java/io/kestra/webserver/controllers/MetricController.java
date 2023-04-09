package io.kestra.webserver.controllers;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

@Validated
@Controller("/api/v1/metrics")
@Requires(beans = MetricRepositoryInterface.class)
public class MetricController {
    @Inject
    private MetricRepositoryInterface metricsRepository;

    @Inject
    @Named(QueueFactoryInterface.METRIC_QUEUE)
    protected QueueInterface<MetricEntry> metricQueue;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Metrics"}, summary = "Get metrics for a specific execution")
    public PagedResults<MetricEntry> findByExecution(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The task id") @Nullable @QueryValue String taskId
    ) {
        var pageable = PageableUtils.from(page, size, sort, metricsRepository.sortMapping());
        if (taskId != null) {
            return PagedResults.of(metricsRepository.findByExecutionIdAndTaskId(executionId, taskId, pageable));
        } else if (taskRunId != null) {
            return PagedResults.of(metricsRepository.findByExecutionIdAndTaskRunId(executionId, taskRunId, pageable));
        } else {
            return PagedResults.of(metricsRepository.findByExecutionId(executionId, pageable));
        }
    }
}
