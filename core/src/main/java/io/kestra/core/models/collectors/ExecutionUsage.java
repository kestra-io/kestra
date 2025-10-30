package io.kestra.core.models.collectors;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.ZonedDateTime;
import java.util.List;

@SuperBuilder
@Getter
@Jacksonized
@Introspected
public class ExecutionUsage {
    private final List<DailyExecutionStatistics> dailyExecutionsCount;

    public static ExecutionUsage of(final String tenantId,
                                    final ExecutionRepositoryInterface executionRepository,
                                    final ZonedDateTime from,
                                    final ZonedDateTime to) {

        return ExecutionUsage.builder()
            .dailyExecutionsCount(executionRepository.dailyStatistics(
                null,
                tenantId,
                null,
                null,
                null,
                from,
                to,
                DateUtils.GroupType.DAY,
                null))
            .build();
    }

    public static ExecutionUsage of(final ExecutionRepositoryInterface repository,
                                    final ZonedDateTime from,
                                    final ZonedDateTime to) {
        return ExecutionUsage.builder()
            .dailyExecutionsCount(repository.dailyStatisticsForAllTenants(
                null,
                null,
                null,
                from,
                to,
                DateUtils.GroupType.DAY
            ))
            .build();
    }
}
