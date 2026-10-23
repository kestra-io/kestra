package io.kestra.core.models.flows.sla.types;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.ExecutionMonitoringSLA;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.DateUtils;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@Getter
@NoArgsConstructor
public class AvgDurationSLA extends SLA implements ExecutionMonitoringSLA {
    private Duration durationThreshold;

    private float percentageThreshold;

    @Builder.Default
    @NotNull
    private int minExecutions = 5;

    @NotNull
    private Duration lookbackPeriod;

    @Builder.Default
    @NotNull
    private List<State.Type> averageOverStates = List.of(State.Type.SUCCESS, State.Type.WARNING); // TODO

    @Override
    public Optional<Violation> evaluate(RunContext runContext, Execution execution, Map<String, Object> additionalParameters) throws InternalException {
        Duration executionDuration = Duration.between(execution.getState().getStartDate(), Instant.now());
        Duration slaDuration = (Duration) additionalParameters.get(SLA.SLA_DURATION_ATTRIBUTE);

        if (executionDuration.compareTo(slaDuration) > 0) {
            String reason = "execution duration of " + executionDuration.truncatedTo(ChronoUnit.MILLIS) + " exceed the maximum duration of " + slaDuration + ".";
            return Optional.of(new Violation(this.getId(), this.getBehavior(), this.getLabels(), reason));
        }

        return Optional.empty();
    }

    @Override
    public Optional<Duration> computeDuration(RunContext runContext) {
        var executionStatisticRepository = ((DefaultRunContext) runContext).services().additionalService(ExecutionStatisticsRepositoryInterface.class);
        var flowInfo = runContext.flowInfo();
        var startDate = Instant.now().minus(this.lookbackPeriod);
        var statistics = executionStatisticRepository.statistics(flowInfo.tenantId(), flowInfo.namespace(), flowInfo.id(), startDate, Instant.now(), DateUtils.GroupType.MONTH);

        var statisticsSize = statistics.stream()
            .map(it -> it.getDuration().getCount())
            .reduce(0L, Long::sum);
        if (statisticsSize < minExecutions) {
            return Optional.empty();
        }

        var cumulatedDurationAvg = statistics.stream()
            .map(it -> it.getDuration().getAvg())
            .reduce(Duration.ZERO, (d1, d2) -> d2 != null ? d1.plus(d2) : d1)
            .toMillis();
        return Optional.of(Duration.ofMillis(cumulatedDurationAvg / statistics.size()));
    }
}
