package io.kestra.webserver.controllers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.tenant.TenantService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.repositories.ExecutionRepositoryInterface;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

@Validated
@Controller("/api/v1/stats")
public class StatsController {
    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily")
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for executions")
    public List<DailyExecutionStatistics> dailyStatistics(
        @Parameter(description = "A string filter") @Nullable String q,
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "A flow id filter") @Nullable String flowId,
        @Parameter(description = "The start datetime, default to now - 30 days") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate
    ) {
        // @TODO: seems to be converted back to utc by micronaut
        return executionRepository.dailyStatistics(
            q,
            tenantService.resolveTenant(),
            namespace,
            flowId,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            null,
            false
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "taskruns/daily")
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for taskRuns")
    public List<DailyExecutionStatistics> taskRunsDailyStatistics(
        @Parameter(description = "A string filter") @Nullable String q,
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "A flow id filter") @Nullable String flowId,
        @Parameter(description = "The start datetime, default to now - 30 days") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate
    ) {
        return executionRepository.dailyStatistics(
            q,
            tenantService.resolveTenant(),
            namespace,
            flowId,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            null,
            true
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily/group-by-flow")
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for executions group by namespaces and flows")
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Parameter(description = "A string filter") @Nullable String q,
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "A flow id filter") @Nullable String flowId,
        @Parameter(description = "A list of flows filter") @Nullable List<ExecutionRepositoryInterface.FlowFilter> flows,
        @Parameter(description = "The start datetime, default to now - 30 days") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate,
        @Parameter(description = "Return only namespace result and skip flows") @Nullable Boolean namespaceOnly
    ) {
        return executionRepository.dailyGroupByFlowStatistics(
            q,
            tenantService.resolveTenant(),
            namespace,
            flowId,
            flows != null && flows.get(0).getNamespace() != null && flows.get(0).getId() != null ? flows : null,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            namespaceOnly != null && namespaceOnly
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/latest/group-by-flow")
    @Operation(tags = {"Stats"}, summary = "Get latest execution by flows")
    public List<Execution> lastExecutions(
        @Parameter(description = "A list of flows filter") @Nullable List<ExecutionRepositoryInterface.FlowFilter> flows
    ) {
        return executionRepository.lastExecutions(
            tenantService.resolveTenant(),
            flows != null && flows.get(0).getNamespace() != null && flows.get(0).getId() != null ? flows : null
        );
    }
}
