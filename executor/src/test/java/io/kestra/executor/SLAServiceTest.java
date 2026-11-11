package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.Violation;
import io.kestra.core.models.flows.sla.types.AvgDurationSLA;
import io.kestra.core.models.flows.sla.types.ExecutionAssertionSLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MicronautTest
class SLAServiceTest {
    @Inject
    private SLAService slaService;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldReturnViolationWithDeclaredBehaviorWhenAssertionCannotBeEvaluated() {
        SLA sla = ExecutionAssertionSLA.builder()
            .id("broken")
            .type(SLA.Type.EXECUTION_ASSERTION)
            .behavior(SLA.Behavior.FAIL)
            ._assert("{{ nope.missing.thing }}")
            .build();
        Flow flow = Flow.builder()
            .tenantId("tenant")
            .namespace("io.kestra.unit-test")
            .id(IdUtils.create())
            .tasks(List.of(Log.builder().id("t").type(Log.class.getName()).message("hi").build()))
            .sla(List.of(sla))
            .build();
        Execution execution = Execution.builder()
            .tenantId("tenant")
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(1)
            .state(new State())
            .build();
        RunContext runContext = runContextFactory.of(flow, execution);

        List<Violation> violations = slaService.evaluateExecutionChangedSLA(runContext, flow, execution);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().behavior()).isEqualTo(SLA.Behavior.FAIL);
        assertThat(violations.getFirst().reason()).contains("could not be evaluated");
    }

    @Test
    void shouldComputeMaxDurationSlaDurationFromItsConfiguration() {
        var sla = MaxDurationSLA.builder().duration(Duration.ofMinutes(5)).build();

        Optional<Duration> duration = new SLAService(mock(ExecutionStatisticsRepositoryInterface.class)).computeSLADuration(execution(), sla);

        assertThat(duration).contains(Duration.ofMinutes(5));
    }

    @Test
    void shouldWeightBucketAveragesByExecutionCount() {
        var sla = avgSla().minExecutions(5).build();

        Optional<Duration> duration = computeDuration(sla, List.of(
            bucket(Duration.ofSeconds(10), 1),
            bucket(Duration.ofSeconds(20), 9)
        ));

        assertThat(duration).contains(Duration.ofSeconds(19));
    }

    @Test
    void shouldIgnoreBucketsWithoutAverage() {
        var sla = avgSla().minExecutions(5).build();

        Optional<Duration> duration = computeDuration(sla, List.of(
            bucket(Duration.ofSeconds(10), 5),
            bucket(null, 5)
        ));

        assertThat(duration).contains(Duration.ofSeconds(10));
    }

    @Test
    void shouldNotCountBucketsWithoutAverageTowardsMinExecutions() {
        var sla = avgSla().minExecutions(5).build();

        Optional<Duration> duration = computeDuration(sla, List.of(
            bucket(Duration.ofSeconds(10), 2),
            bucket(null, 5)
        ));

        assertThat(duration).isEmpty();
    }

    @Test
    void shouldNotComputeAverageDurationWhenNoStatistics() {
        Optional<Duration> duration = computeDuration(avgSla().build(), List.of());

        assertThat(duration).isEmpty();
    }

    @Test
    void shouldNotComputeAverageDurationWhenNotEnoughExecutions() {
        var sla = avgSla().minExecutions(5).build();

        Optional<Duration> duration = computeDuration(sla, List.of(bucket(Duration.ofSeconds(10), 4)));

        assertThat(duration).isEmpty();
    }

    @Test
    void shouldComputeAverageDurationWhenExactlyMinExecutions() {
        var sla = avgSla().minExecutions(5).build();

        Optional<Duration> duration = computeDuration(sla, List.of(bucket(Duration.ofSeconds(10), 5)));

        assertThat(duration).contains(Duration.ofSeconds(10));
    }

    @Test
    void shouldAddPercentageThresholdToAverage() {
        var sla = avgSla().percentageThreshold(0.5f).build();

        Optional<Duration> duration = computeDuration(sla, List.of(bucket(Duration.ofSeconds(10), 5)));

        assertThat(duration).contains(Duration.ofSeconds(15));
    }

    @Test
    void shouldAddDurationThresholdToAverage() {
        var sla = avgSla().durationThreshold(Duration.ofSeconds(5)).build();

        Optional<Duration> duration = computeDuration(sla, List.of(bucket(Duration.ofSeconds(10), 5)));

        assertThat(duration).contains(Duration.ofSeconds(15));
    }

    @Test
    void shouldAddDurationThresholdOnceAcrossMultipleBuckets() {
        var sla = avgSla().durationThreshold(Duration.ofSeconds(10)).build();

        Optional<Duration> duration = computeDuration(sla, List.of(
            bucket(Duration.ofSeconds(10), 3),
            bucket(Duration.ofSeconds(20), 3)
        ));

        assertThat(duration).contains(Duration.ofSeconds(25));
    }

    @Test
    void shouldAverageOverSuccessAndWarningByDefault() {
        var repository = mock(ExecutionStatisticsRepositoryInterface.class);

        new SLAService(repository).computeSLADuration(execution(), avgSla().build());

        verify(repository).statistics(eq("tenant"), eq("namespace"), eq("flow"), eq(List.of(State.Type.SUCCESS, State.Type.WARNING)), any(), any(), any());
    }

    @Test
    void shouldAverageOverConfiguredStates() {
        var sla = avgSla().averageOverStates(List.of(State.Type.FAILED)).build();
        var repository = mock(ExecutionStatisticsRepositoryInterface.class);
        when(repository.statistics(any(), any(), any(), eq(List.of(State.Type.FAILED)), any(), any(), any()))
            .thenReturn(List.of(bucket(Duration.ofSeconds(40), 5)));
        when(repository.statistics(any(), any(), any(), eq(List.of(State.Type.SUCCESS)), any(), any(), any()))
            .thenReturn(List.of(bucket(Duration.ofSeconds(10), 5)));

        Optional<Duration> duration = new SLAService(repository).computeSLADuration(execution(), sla);

        assertThat(duration).contains(Duration.ofSeconds(40));
    }

    private static AvgDurationSLA.AvgDurationSLABuilder<?, ?> avgSla() {
        return AvgDurationSLA.builder().lookbackPeriod(Duration.ofDays(7));
    }

    private static Execution execution() {
        return Execution.builder().tenantId("tenant").namespace("namespace").flowId("flow").id(IdUtils.create()).build();
    }

    private static Optional<Duration> computeDuration(AvgDurationSLA sla, List<DailyExecutionStatistics> statistics) {
        var repository = mock(ExecutionStatisticsRepositoryInterface.class);
        when(repository.statistics(any(), any(), any(), any(), any(), any(), any())).thenReturn(statistics);
        return new SLAService(repository).computeSLADuration(execution(), sla);
    }

    private static DailyExecutionStatistics bucket(Duration avg, long count) {
        return DailyExecutionStatistics.builder()
            .startDate(Instant.now())
            .duration(DailyExecutionStatistics.Duration.builder().avg(avg).sum(avg == null ? null : avg.multipliedBy(count)).count(count).build())
            .build();
    }
}
