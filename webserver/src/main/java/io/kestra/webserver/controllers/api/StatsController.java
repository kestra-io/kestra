package io.kestra.webserver.controllers.api;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionCountStatistics;
import io.kestra.core.models.executions.statistics.LogStatistics;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.stats.SummaryStatistics;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import org.slf4j.event.Level;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Validated
@Controller("/api/v1/stats")
public class StatsController {
    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    protected LogRepositoryInterface logRepositoryInterface;

    @Inject
    protected FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    protected TriggerRepositoryInterface triggerRepositoryInterface;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "summary")
    @Operation(tags = {"Stats"}, summary = "Get summary statistics")
    public SummaryStatistics summary(@Body @Valid SummaryRequest request) {
        return new SummaryStatistics(
            flowRepositoryInterface.countForNamespace(tenantService.resolveTenant(), request.namespace()),
            triggerRepositoryInterface.countForNamespace(tenantService.resolveTenant(), request.namespace())
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily")
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for executions")
    public List<DailyExecutionStatistics> dailyStatistics(@Body @Valid StatisticRequest statisticRequest) {
        // @TODO: seems to be converted back to utc by micronaut
        return executionRepository.dailyStatistics(
            statisticRequest.q(),
            tenantService.resolveTenant(),
            statisticRequest.scope(),
            statisticRequest.namespace(),
            statisticRequest.flowId(),
            statisticRequest.startDate() != null ? statisticRequest.startDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            statisticRequest.endDate() != null ? statisticRequest.endDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            null,
            statisticRequest.state(),
            false);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "taskruns/daily")
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for taskRuns")
    public List<DailyExecutionStatistics> taskRunsDailyStatistics(@Body @Valid StatisticRequest statisticRequest) {
        return executionRepository.dailyStatistics(
            statisticRequest.q(),
            tenantService.resolveTenant(),
            statisticRequest.scope(),
            statisticRequest.namespace(),
            statisticRequest.flowId(),
            statisticRequest.startDate() != null ? statisticRequest.startDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            statisticRequest.endDate() != null ? statisticRequest.endDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            null,
            statisticRequest.state(),
            true);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily/group-by-flow")
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for executions group by namespaces and flows")
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(@Body @Valid ByFlowStatisticRequest statisticRequest) {
        return executionRepository.dailyGroupByFlowStatistics(
            statisticRequest.q(),
            tenantService.resolveTenant(),
            statisticRequest.namespace(),
            statisticRequest.flowId(),
            statisticRequest.flows() != null && statisticRequest.flows().getFirst().getNamespace() != null && statisticRequest.flows().getFirst().getId() != null ? statisticRequest.flows() : null,
            statisticRequest.startDate() != null ? statisticRequest.startDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            statisticRequest.endDate() != null ? statisticRequest.endDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            Boolean.TRUE.equals(statisticRequest.namespaceOnly())
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily/group-by-namespace")
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for executions grouped by namespace")
    public Map<String, ExecutionCountStatistics> dailyStatisticsGroupByNamespace(@Body @Valid ByNamespaceStatisticRequest request) {
        return executionRepository.executionCountsGroupedByNamespace(
            tenantService.resolveTenant(),
            request.namespace(),
            request.startDate() != null ? request.startDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            request.endDate() != null ? request.endDate().withZoneSameInstant(ZoneId.systemDefault()) : null
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/latest/group-by-flow")
    @Operation(tags = {"Stats"}, summary = "Get latest execution by flows")
    public List<Execution> lastExecutions(
        @Parameter(description = "A list of flows filter") @Body @Valid LastExecutionsRequest lastExecutionsRequest
    ) {
        return executionRepository.lastExecutions(
            tenantService.resolveTenant(),
            lastExecutionsRequest.flows() != null && lastExecutionsRequest.flows().getFirst().getNamespace() != null && lastExecutionsRequest.flows().getFirst().getId() != null ? lastExecutionsRequest.flows() : null
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "logs/daily")
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for logs")
    public List<LogStatistics> logsDailyStatistics(@Body @Valid LogStatisticRequest logStatisticRequest) {
        return logRepositoryInterface.statistics(
            logStatisticRequest.q(),
            tenantService.resolveTenant(),
            logStatisticRequest.namespace(),
            logStatisticRequest.flowId(),
            logStatisticRequest.logLevel(),
            logStatisticRequest.startDate() != null ? logStatisticRequest.startDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            logStatisticRequest.endDate() != null ? logStatisticRequest.endDate().withZoneSameInstant(ZoneId.systemDefault()) : null,
            null
        );
    }

    @Introspected
    public record SummaryRequest(
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "The start datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]")ZonedDateTime endDate
    ) {}


    @Introspected
    public record StatisticRequest(
        @Parameter(description = "A string filter") @Nullable String q,
        @Parameter(description = "The scope of the executions to include") @Nullable FlowScope scope,
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "A flow id filter") @Nullable String flowId,
        @Parameter(description = "The start datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]")ZonedDateTime endDate,
        @Parameter(description = "the state of the execution") @Nullable List<State.Type> state
    ) {}

    @Introspected
    public record LogStatisticRequest(
        @Parameter(description = "A string filter") @Nullable String q,
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "A flow id filter") @Nullable String flowId,
        @Parameter(description = "A log level filter") @Nullable Level logLevel,
        @Parameter(description = "The start datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]")ZonedDateTime endDate
    ) {}

    @Introspected
    public record ByNamespaceStatisticRequest(
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "The start datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]")ZonedDateTime endDate
    ) {}

    @Introspected
    public record ByFlowStatisticRequest(
        @Parameter(description = "A string filter") @Nullable String q,
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "A flow id filter") @Nullable String flowId,
        @Parameter(description = "A list of flows filter") @Nullable List<ExecutionRepositoryInterface.FlowFilter> flows,
        @Parameter(description = "The start datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]")ZonedDateTime endDate,
        @Nullable Boolean namespaceOnly
    ) {}

    @Introspected
    public record LastExecutionsRequest(
        @Parameter(description = "A list of flows filter") @Nullable List<ExecutionRepositoryInterface.FlowFilter> flows
    ) {}
}
