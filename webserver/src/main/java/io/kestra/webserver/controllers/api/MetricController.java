package io.kestra.webserver.controllers.api;

import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.Format;
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

import java.time.ZonedDateTime;
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

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}")
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
            return PagedResults.of(metricsRepository.findByExecutionIdAndTaskId(tenantService.resolveTenant(), executionId, taskId, pageable));
        } else if (taskRunId != null) {
            return PagedResults.of(metricsRepository.findByExecutionIdAndTaskRunId(tenantService.resolveTenant(), executionId, taskRunId, pageable));
        } else {
            return PagedResults.of(metricsRepository.findByExecutionId(tenantService.resolveTenant(), executionId, pageable));
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/names/{namespace}/{flowId}")
    @Operation(tags = {"Metrics"}, summary = "Get metrics names for a specific flow")
    public List<String> flowMetrics(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow Id") @PathVariable String flowId
    ) {
        return metricsRepository.flowMetrics(tenantService.resolveTenant(), namespace, flowId);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/names/{namespace}/{flowId}/{taskId}")
    @Operation(tags = {"Metrics"}, summary = "Get metrics names for a specific task in a flow")
    public List<String> taskMetrics(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow Id") @PathVariable String flowId,
        @Parameter(description = "The flow Id") @PathVariable String taskId
    ) {
        return metricsRepository.taskMetrics(tenantService.resolveTenant(), namespace, flowId, taskId);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/tasks/{namespace}/{flowId}")
    @Operation(tags = {"Metrics"}, summary = "Get tasks id that have metrics for a specific flow, include deleted or renamed tasks")
    public List<String> tasks(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow Id") @PathVariable String flowId
    ) {
        return metricsRepository.tasksWithMetrics(tenantService.resolveTenant(), namespace, flowId);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/aggregates/{namespace}/{flowId}/{metric}")
    @Operation(tags = {"Metrics"}, summary = "Get metrics aggregations for a specific flow")
    public MetricAggregations aggregateByFlowId(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow Id") @PathVariable String flowId,
        @Parameter(description = "The metric name") @PathVariable String metric,
        @Parameter(description = "The start datetime, default to now - 30 days") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate,
        @Parameter(description = "The type of aggregation: avg, sum, min or max") @QueryValue(defaultValue = "sum") String aggregation
    ) {
        return metricsRepository.aggregateByFlowId(
            tenantService.resolveTenant(),
            namespace,
            flowId,
            null,
            metric,
            startDate == null ? ZonedDateTime.now().minusDays(30) : startDate,
            endDate == null ? ZonedDateTime.now() : endDate,
            aggregation
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/aggregates/{namespace}/{flowId}/{taskId}/{metric}")
    @Operation(tags = {"Metrics"}, summary = "Get metrics aggregations for a specific flow")
    public MetricAggregations aggregateByFlowIdAndTaskId(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow Id") @PathVariable String flowId,
        @Parameter(description = "The task Id") @PathVariable String taskId,
        @Parameter(description = "The metric name") @PathVariable String metric,
        @Parameter(description = "The start datetime, default to now - 30 days") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate,
        @Parameter(description = "The type of aggregation: avg, sum, min or max") @QueryValue(defaultValue = "sum") String aggregation
    ) {
        return metricsRepository.aggregateByFlowId(
            tenantService.resolveTenant(),
            namespace,
            flowId,
            taskId,
            metric,
            startDate == null ? ZonedDateTime.now().minusDays(30) : startDate,
            endDate == null ? ZonedDateTime.now() : endDate,
            aggregation
        );
    }
}
