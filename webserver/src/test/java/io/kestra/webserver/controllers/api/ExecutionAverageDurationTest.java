package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the average-duration endpoint backing the execution progress bar. It reads the
 * pre-aggregated execution-statistics table, so the fixtures are statistic rows rather than
 * executions.
 */
@KestraTest
class ExecutionAverageDurationTest {
    private static final String NAMESPACE = "io.kestra.tests.progress";

    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private ExecutionStatisticsRepositoryInterface executionStatisticsRepository;

    @Test
    void shouldReturnNullAverageWhenFlowHasNoStatistics() {
        // Given a flow that never ran
        String flowId = IdUtils.create();

        // When
        ExecutionController.FlowAverageDuration result = averageDuration(flowId);

        // Then
        assertThat(result.avgDurationMs()).isNull();
        assertThat(result.count()).isZero();
    }

    @Test
    void shouldAverageAllTerminatedExecutionsOfTheFlow() {
        // Given three terminated executions of the same flow
        String flowId = IdUtils.create();
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        executionStatisticsRepository.saveBatch(
            List.of(
                statistic(NAMESPACE, flowId, bucket, State.Type.SUCCESS, 1_000),
                statistic(NAMESPACE, flowId, bucket, State.Type.SUCCESS, 2_000),
                statistic(NAMESPACE, flowId, bucket, State.Type.FAILED, 3_000)
            )
        );

        // When
        ExecutionController.FlowAverageDuration result = averageDuration(flowId);

        // Then
        assertThat(result.avgDurationMs()).isEqualTo(2_000L);
        assertThat(result.count()).isEqualTo(3L);
    }

    @Test
    void shouldWeightTheAverageByExecutionCountWhenBucketsSpanSeveralDays() {
        // Given one execution today and three (much shorter) ones a week ago: averaging the two
        // daily averages would give 3_000ms, the count-weighted average is 1_500ms
        String flowId = IdUtils.create();
        Instant today = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant lastWeek = today.minus(7, ChronoUnit.DAYS);
        executionStatisticsRepository.saveBatch(
            List.of(
                statistic(NAMESPACE, flowId, today, State.Type.SUCCESS, 5_000),
                statistic(NAMESPACE, flowId, lastWeek, State.Type.SUCCESS, 1_000),
                statistic(NAMESPACE, flowId, lastWeek, State.Type.SUCCESS, 500),
                statistic(NAMESPACE, flowId, lastWeek, State.Type.SUCCESS, 500)
            )
        );

        // When
        ExecutionController.FlowAverageDuration result = averageDuration(flowId);

        // Then
        assertThat(result.avgDurationMs()).isEqualTo(1_750L);
        assertThat(result.count()).isEqualTo(4L);
    }

    @Test
    void shouldIgnoreExecutionsOlderThanTheLookbackWindow() {
        // Given the only executions of the flow ran more than 30 days ago
        String flowId = IdUtils.create();
        Instant longAgo = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(60, ChronoUnit.DAYS);
        executionStatisticsRepository.saveBatch(
            List.of(statistic(NAMESPACE, flowId, longAgo, State.Type.SUCCESS, 1_000))
        );

        // When
        ExecutionController.FlowAverageDuration result = averageDuration(flowId);

        // Then
        assertThat(result.avgDurationMs()).isNull();
        assertThat(result.count()).isZero();
    }

    @Test
    void shouldIgnoreExecutionsOfOtherFlowsAndNamespaces() {
        // Given executions of the same flow id in another namespace, and of another flow id in the
        // same namespace
        String flowId = IdUtils.create();
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        executionStatisticsRepository.saveBatch(
            List.of(
                statistic(NAMESPACE, flowId, bucket, State.Type.SUCCESS, 1_000),
                statistic(NAMESPACE, IdUtils.create(), bucket, State.Type.SUCCESS, 9_000),
                statistic("io.kestra.tests.other", flowId, bucket, State.Type.SUCCESS, 9_000)
            )
        );

        // When
        ExecutionController.FlowAverageDuration result = averageDuration(flowId);

        // Then
        assertThat(result.avgDurationMs()).isEqualTo(1_000L);
        assertThat(result.count()).isEqualTo(1L);
    }

    private ExecutionController.FlowAverageDuration averageDuration(String flowId) {
        return client.toBlocking().retrieve(
            GET("/api/v1/main/executions/namespaces/" + NAMESPACE + "/flows/" + flowId + "/average-duration"),
            ExecutionController.FlowAverageDuration.class
        );
    }

    private ExecutionStatistic statistic(String namespace, String flowId, Instant bucket, State.Type state, long durationMs) {
        return new ExecutionStatistic(
            MAIN_TENANT,
            namespace,
            flowId,
            bucket,
            state,
            1,
            durationMs,
            durationMs,
            durationMs,
            1,
            durationMs,
            durationMs,
            durationMs,
            IdUtils.create()
        );
    }
}
