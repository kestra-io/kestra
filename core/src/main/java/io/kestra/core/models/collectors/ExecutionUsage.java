package io.kestra.core.models.collectors;

import java.time.ZonedDateTime;
import java.util.List;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.utils.DateUtils;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@SuperBuilder
@Getter
@Jacksonized
public class ExecutionUsage {
    private final List<DailyExecutionStatistics> dailyExecutionsCount;

    public static ExecutionUsage of(final String tenantId,
        final ExecutionStatisticsRepositoryInterface executionStatisticRepository,
        final ZonedDateTime from,
        final ZonedDateTime to) {

        return ExecutionUsage.builder()
            .dailyExecutionsCount(
                executionStatisticRepository.statistics(
                    tenantId,
                    null,
                    null,
                    from.toInstant(),
                    to.toInstant(),
                    DateUtils.GroupType.DAY
                )
            )
            .build();
    }

    public static ExecutionUsage of(final ExecutionStatisticsRepositoryInterface executionStatisticRepository,
        final ZonedDateTime from,
        final ZonedDateTime to) {
        return ExecutionUsage.builder()
            .dailyExecutionsCount(
                executionStatisticRepository.statisticsForAllTenants(
                    from.toInstant(),
                    to.toInstant(),
                    DateUtils.GroupType.DAY
                )
            )
            .build();
    }
}
