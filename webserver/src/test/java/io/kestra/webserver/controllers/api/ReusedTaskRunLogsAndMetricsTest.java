package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A replay reuses the task runs before the one it restarts from, so those tasks never run again: their logs and
 * metrics must still be served for the new execution, read from the execution that actually ran them.
 */
@KestraTest(startRunner = true)
class ReusedTaskRunLogsAndMetricsTest {
    private static final String NAMESPACE = "io.kestra.tests";
    private static final String FLOW_ID = "reused-taskrun";

    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/reused-taskrun.yaml" })
    void shouldReturnTheLogsAndMetricsOfAReusedTaskRunWhenReplayingFromALaterTask() throws Exception {
        // Given: an execution of task1 then task2, and the logs and metrics task1 recorded
        Execution execution = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/%s/%s?wait=true".formatted(NAMESPACE, FLOW_ID), null)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
        // Logs and metrics are indexed asynchronously, so wait for the ones task1 recorded before replaying.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        {
            assertThat(logsOf(execution.getId())).anySatisfy(log -> assertThat(log.getTaskId()).isEqualTo("task1"));
            assertThat(metricsOf(execution.getId()).getResults()).anySatisfy(metric -> assertThat(metric.getTaskId()).isEqualTo("task1"));
        });
        LogEntry task1Log = logsOf(execution.getId()).stream()
            .filter(log -> "task1".equals(log.getTaskId()))
            .findFirst()
            .orElseThrow();
        MetricEntry task1Metric = metricsOf(execution.getId()).getResults().stream()
            .filter(metric -> "task1".equals(metric.getTaskId()))
            .findFirst()
            .orElseThrow();

        // When: replaying from task2, which reuses task1 instead of running it again
        String originalTask2RunId = execution.findTaskRunsByTaskId("task2").getFirst().getId();
        Optional<Flow> flow = flowRepository.findById(TenantService.MAIN_TENANT, NAMESPACE, FLOW_ID);
        client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/main/executions/%s/actions/replay?taskRunId=%s".formatted(execution.getId(), originalTask2RunId),
                Map.of()
            ),
            Execution.class
        );
        Execution replay = runnerUtils.awaitChildExecution(flow.orElseThrow(), execution, Duration.ofSeconds(30));

        // Then: the reused task run points at the run that produced the rows, and both endpoints serve them
        TaskRun reusedTask1 = replay.findTaskRunsByTaskId("task1").getFirst();
        assertThat(reusedTask1.getSourceExecutionId()).isEqualTo(execution.getId());
        assertThat(reusedTask1.getSourceTaskRunId()).isEqualTo(execution.findTaskRunsByTaskId("task1").getFirst().getId());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        {
            assertThat(logsOf(replay.getId()))
                .as("task1 did not run again, so its log is the one it recorded, with its original timestamp")
                .anySatisfy(log -> assertThat(log)
                    .extracting(LogEntry::getMessage, LogEntry::getTimestamp)
                    .containsExactly(task1Log.getMessage(), task1Log.getTimestamp())
                )
                .anySatisfy(log -> assertThat(log.getTaskId()).isEqualTo("task2"))
                .as("task2 ran again, so only its new log is served, not the one of the run it replaced")
                .noneSatisfy(log -> assertThat(log.getTaskRunId()).isEqualTo(originalTask2RunId));

            assertThat(metricsOf(replay.getId()).getResults())
                .as("same for its metrics")
                .anySatisfy(metric -> assertThat(metric)
                    .extracting(MetricEntry::getName, MetricEntry::getTimestamp)
                    .containsExactly(task1Metric.getName(), task1Metric.getTimestamp())
                )
                .anySatisfy(metric -> assertThat(metric.getTaskId()).isEqualTo("task2"));
        });

        // And: asking for the reused task run alone still reaches the rows recorded under the source run
        assertThat(logsOfTaskRun(replay.getId(), reusedTask1.getId()))
            .isNotEmpty()
            .allSatisfy(log -> assertThat(log.getTaskId()).isEqualTo("task1"));
        assertThat(metricsOfTaskRun(replay.getId(), reusedTask1.getId()).getResults())
            .isNotEmpty()
            .allSatisfy(metric -> assertThat(metric.getTaskId()).isEqualTo("task1"));
    }

    private List<LogEntry> logsOf(String executionId) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/logs/" + executionId),
            Argument.listOf(LogEntry.class)
        );
    }

    private List<LogEntry> logsOfTaskRun(String executionId, String taskRunId) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/logs/%s?filters[taskRunId][EQUALS]=%s".formatted(executionId, taskRunId)),
            Argument.listOf(LogEntry.class)
        );
    }

    @SuppressWarnings("unchecked")
    private PagedResults<MetricEntry> metricsOf(String executionId) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/metrics/%s?size=100".formatted(executionId)),
            Argument.of(PagedResults.class, MetricEntry.class)
        );
    }

    @SuppressWarnings("unchecked")
    private PagedResults<MetricEntry> metricsOfTaskRun(String executionId, String taskRunId) {
        return client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/metrics/%s?taskRunId=%s".formatted(executionId, taskRunId)),
            Argument.of(PagedResults.class, MetricEntry.class)
        );
    }
}
