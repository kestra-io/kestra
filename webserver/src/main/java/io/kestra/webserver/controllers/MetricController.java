package io.kestra.webserver.controllers;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
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
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Validated
@Controller("/api/v1/metrics")
@Requires(beans = MetricRepositoryInterface.class)
public class MetricController {
    @Inject
    private MetricRepositoryInterface metricsRepository;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKMETRIC_NAMED)
    protected QueueInterface<MetricEntry> metricQueue;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Metrics"}, summary = "Get metrics for a specific execution")
    public List<MetricEntry> findByExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The task id") @Nullable @QueryValue String taskId
    ) {
        if (taskId != null) {
            return metricsRepository.findByExecutionIdAndTaskId(executionId, taskId);
        } else if (taskRunId != null) {
            return metricsRepository.findByExecutionIdAndTaskRunId(executionId, taskRunId);
        } else {
            return metricsRepository.findByExecutionId(executionId);
        }
    }
}
