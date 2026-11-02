package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.sla.*;
import io.kestra.core.models.flows.sla.types.AvgDurationSLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SLAService {
    private final ExecutionStatisticsRepositoryInterface executionStatisticsRepository;

    @Inject
    public SLAService(ExecutionStatisticsRepositoryInterface executionStatisticsRepository) {
        this.executionStatisticsRepository = executionStatisticsRepository;
    }

    /**
     * Evaluate execution changed SLA of a flow for an execution.
     * Each violated SLA will be logged.
     */
    public List<Violation> evaluateExecutionChangedSLA(RunContext runContext, FlowInterface flow, Execution execution) {
        return ListUtils.emptyOnNull(flow.getSla()).stream()
            .filter(ExecutionChangedSLA.class::isInstance)
            .map(
                sla ->
                {
                    try {
                        return sla.evaluate(runContext, execution, Map.of());
                    } catch (Exception e) {
                        runContext.logger().error("SLA '{}' could not be evaluated and is treated as violated: {}", sla.getId(), e.getMessage(), e);
                        return Optional.of(new Violation(sla.getId(), sla.getBehavior(), sla.getLabels(), "SLA could not be evaluated: " + e.getMessage()));
                    }
                }
            )
            .flatMap(violation -> violation.stream())
            .peek(violation -> runContext.logger().warn("SLA '{}' violated: {}", violation.slaId(), violation.reason()))
            .toList();
    }

    /**
     * Evaluate a single SLA for an execution.
     * Each violated SLA will be logged.
     */
    public Optional<Violation> evaluateExecutionMonitoringSLA(RunContext runContext, Execution execution, SLA sla, Duration duration) {
        try {
            Optional<Violation> maybeViolation = sla.evaluate(runContext, execution, Map.of(SLA.SLA_DURATION_ATTRIBUTE, duration));
            maybeViolation.ifPresent(violation -> runContext.logger().warn("SLA '{}' violated: {}", violation.slaId(), violation.reason()));
            return maybeViolation;
        } catch (Exception e) {
            runContext.logger().error("SLA '{}' could not be evaluated and is treated as violated: {}", sla.getId(), e.getMessage(), e);
            return Optional.of(new Violation(sla.getId(), sla.getBehavior(), sla.getLabels(), "SLA could not be evaluated: " + e.getMessage()));
        }
    }

    public Optional<Duration> computeSLADuration(Execution execution, ExecutionMonitoringSLA sla) {
        return switch (sla) {
            case MaxDurationSLA maxDurationSLA -> Optional.of(maxDurationSLA.getDuration());
            case AvgDurationSLA avgDurationSLA -> {
                var startDate = Instant.now().minus(avgDurationSLA.getLookbackPeriod());
                var statistics = executionStatisticsRepository.statistics(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), avgDurationSLA.getAverageOverStates(), startDate, Instant.now(), DateUtils.GroupType.MONTH);

                var sum = 0L;
                var count = 0L;
                for (var statistic : statistics) {
                    var bucketSum = statistic.getDuration().getSum();
                    if (bucketSum != null) {
                        sum += bucketSum.toMillis();
                        count += statistic.getDuration().getCount();
                    }
                }
                if (count == 0 || count < avgDurationSLA.getMinExecutions()) {
                    yield Optional.empty();
                }

                var avg = sum / count;
                if (avgDurationSLA.getPercentageThreshold() != null) {
                    avg = (long) (avg * (1d + avgDurationSLA.getPercentageThreshold()));
                }
                if (avgDurationSLA.getDurationThreshold() != null) {
                    avg = avg + avgDurationSLA.getDurationThreshold().toMillis();
                }
                yield Optional.of(Duration.ofMillis(avg));
            }
            default -> throw new IllegalArgumentException("Unknown SLA type: " + sla.getClass().getName());
        };
    }
}
