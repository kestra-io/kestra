package io.kestra.webserver.controllers;

import io.micronaut.core.convert.format.Format;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
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


    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for executions")
    public List<DailyExecutionStatistics> dailyStatistics(
        @Parameter(description = "Lucene string filter") String q,
        @Parameter(description = "The start datetime, default to now - 30 days") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate
    ) {
        // @TODO: seems to be converted back to utc by micronaut
        return executionRepository.dailyStatistics(
            q,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            false
        );
    }


    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "taskruns/daily", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for taskRuns")
    public List<DailyExecutionStatistics> taskRunsDailyStatistics(
        @Parameter(description = "Lucene string filter") String q,
        @Parameter(description = "The start datetime, default to now - 30 days") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate
    ) {
        return executionRepository.dailyStatistics(
            q,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            true
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily/group-by-flow", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Stats"}, summary = "Get daily statistics for executions group by namespaces and flows")
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Parameter(description = "Lucene string filter") String q,
        @Parameter(description = "The start datetime, default to now - 30 days") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime, default to now") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate,
        @Parameter(description = "Return only namespace result and skip flows") @Nullable Boolean namespaceOnly
    ) {

        return executionRepository.dailyGroupByFlowStatistics(
            q,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            namespaceOnly != null && namespaceOnly
        );
    }
}
