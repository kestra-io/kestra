package io.kestra.core.models.collectors;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;
import java.util.List;

@SuperBuilder
@Getter
@Jacksonized
@Introspected
public class ExecutionMetrics {
    private final List<DailyExecutionStatistics> dailyExecutionsCount;
    private final List<DailyExecutionStatistics> dailyTaskRunsCount;

    public static ExecutionMetrics of(ExecutionRepositoryInterface executionRepository) {
        return ExecutionMetrics.builder()
            .dailyExecutionsCount(executionRepository.dailyStatistics(
                "*",
                LocalDate.now().minusDays(1),
                LocalDate.now(),
                false
            ))
            .dailyTaskRunsCount(executionRepository.dailyStatistics(
                "*",
                LocalDate.now().minusDays(1),
                LocalDate.now(),
                true
            ))
            .build();
    }
}
