package io.kestra.core.models.collectors;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@SuperBuilder
@Getter
@Jacksonized
@Introspected
public class ExecutionUsage {
    private final List<DailyExecutionStatistics> dailyExecutionsCount;
    private final List<DailyExecutionStatistics> dailyTaskRunsCount;

    public static ExecutionUsage of(String tenantId, ExecutionRepositoryInterface executionRepository) {
        ZonedDateTime startDate = ZonedDateTime.now()
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .minusDays(1);

        List<DailyExecutionStatistics> dailyTaskRunsCount = null;

        try {
            dailyTaskRunsCount = executionRepository.dailyStatistics(
                null,
                tenantId,
                null,
                null,
                startDate,
                ZonedDateTime.now(),
                DateUtils.GroupType.DAY,
                true
            );
        } catch (UnsupportedOperationException ignored) {

        }

        return ExecutionUsage.builder()
            .dailyExecutionsCount(executionRepository.dailyStatistics(
                null,
                tenantId,
                null,
                null,
                startDate,
                ZonedDateTime.now(),
                DateUtils.GroupType.DAY,
                false
            ))
            .dailyTaskRunsCount(dailyTaskRunsCount)
            .build();
    }

    public static ExecutionUsage of(ExecutionRepositoryInterface executionRepository) {
        ZonedDateTime startDate = ZonedDateTime.now()
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .minusDays(1);

        List<DailyExecutionStatistics> dailyTaskRunsCount = null;

        try {
            dailyTaskRunsCount = executionRepository.dailyStatisticsForAllTenants(
                null,
                null,
                null,
                startDate,
                ZonedDateTime.now(),
                DateUtils.GroupType.DAY,
                true
            );
        } catch (UnsupportedOperationException ignored) {

        }

        return ExecutionUsage.builder()
            .dailyExecutionsCount(executionRepository.dailyStatisticsForAllTenants(
                null,
                null,
                null,
                startDate,
                ZonedDateTime.now(),
                DateUtils.GroupType.DAY,
                false
            ))
            .dailyTaskRunsCount(dailyTaskRunsCount)
            .build();
    }
}
