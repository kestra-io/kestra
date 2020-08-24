package org.kestra.webserver.controllers;

import io.micronaut.core.convert.format.Format;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.validation.Validated;
import org.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import org.kestra.core.repositories.ExecutionRepositoryInterface;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;

@Validated
@Controller("/api/v1/stats")

public class StatsController {
    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    /**
     * Return daily statistics for all executions filter optionnaly by a lucene query group by namespace & flow
     *
     * @param q Lucene string to filter execution
     * @param startDate default to now - 30 days
     * @param endDate default to now
     * @return map of namespace, containing a Map of flow, AbstractDailyExecutionStatistics
     */
    @Post(uri = "executions/daily/group-by-flow", produces = MediaType.TEXT_JSON)
    public Map<String, Map<String, List<DailyExecutionStatistics>>> dailyGroupByFlowStatistics(
        @Nullable String q,
        @Nullable @Format("yyyy-MM-dd") LocalDate startDate,
        @Nullable @Format("yyyy-MM-dd") LocalDate endDate
    ) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }

        final String execQuery = "state.startDate:[" +
            startDate.toString() +
            " TO " +
            (endDate != null ? endDate.toString() : "*") +
            "] AND " + (q != null ? q : "*");

        return executionRepository.dailyGroupByFlowStatistics(execQuery);
    }
}
