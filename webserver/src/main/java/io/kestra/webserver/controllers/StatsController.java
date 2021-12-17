package io.kestra.webserver.controllers;

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
import jakarta.inject.Inject;

@Validated
@Controller("/api/v1/stats")
public class StatsController {
    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    /**
     * Return daily statistics for all executions filter optionnaly by a lucene query
     *
     * @param q Lucene string to filter execution
     * @param startDate default to now - 30 days
     * @param endDate default to now
     * @return a list of DailyExecutionStatistics
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily", produces = MediaType.TEXT_JSON)
    public List<DailyExecutionStatistics> dailyStatistics(
        @Nullable String q,
        @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate
    ) {
        // @TODO: seems to be converted back to utc by micronaut
        return executionRepository.dailyStatistics(
            q,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            false
        );
    }

    /**
     * Return daily statistics for all taskRuns filter optionnaly by a lucene query
     *
     * @param q Lucene string to filter execution
     * @param startDate default to now - 30 days
     * @param endDate default to now
     * @return a list of DailyExecutionStatistics
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "taskruns/daily", produces = MediaType.TEXT_JSON)
    public List<DailyExecutionStatistics> taskRunsDailyStatistics(
        @Nullable String q,
        @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate
    ) {
        return executionRepository.dailyStatistics(
            q,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            true
        );
    }

    /**
     * Return daily statistics for all executions filter optionnaly by a lucene query group by namespace &amp; flow
     *
     * @param q Lucene string to filter execution
     * @param startDate default to now - 30 days
     * @param endDate default to now
     * @return map of namespace, containing a Map of flow, DailyExecutionStatistics
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/daily/group-by-flow", produces = MediaType.TEXT_JSON)
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String q,
        @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate,
        @Nullable Boolean namespaceOnly
    ) {

        return executionRepository.dailyGroupByFlowStatistics(
            q,
            startDate != null ? startDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            endDate != null ? endDate.withZoneSameInstant(ZoneId.systemDefault()) : null,
            namespaceOnly != null && namespaceOnly
        );
    }
}
