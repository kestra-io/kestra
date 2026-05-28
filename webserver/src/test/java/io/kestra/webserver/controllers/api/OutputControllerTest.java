package io.kestra.webserver.controllers.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.tenant.TenantService;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class OutputControllerTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private TaskOutputRepositoryInterface taskOutputRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void getTaskOutput() {
        String tenantId = TenantService.MAIN_TENANT;
        String taskRunId = "taskRunId";
        var execution = Execution.builder()
            .tenantId(tenantId)
            .id("executionId")
            .namespace("namespace")
            .flowId("flowId")
            .taskRunList(List.of(TaskRun.builder().tenantId(tenantId).id(taskRunId).build()))
            .state(new State())
            .build();
        String value = """
        {"some":"output"}""";
        executionRepository.save(execution);

        TaskOutput taskOutput = new TaskOutput(taskRunId, tenantId, "executionId", value.getBytes(StandardCharsets.UTF_8), null);
        taskOutputRepository.save(taskOutput);

        String response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/executionId/" + taskRunId),
            String.class
        );

        assertThat(response).isEqualTo(value);
    }

    @Test
    void getTaskOutputShouldThrowNotFoundWhenTaskRunNotFound() {
        String taskRunId = "notFound";
        String tenantId = TenantService.MAIN_TENANT;

        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/" + tenantId + "/outputs/executionId/" + taskRunId),
                TaskOutput.class
            )
        );
    }

    @Test
    void getTaskOutputInformation() {
        String tenantId = TenantService.MAIN_TENANT;
        String taskRunId = "taskRunId";
        var execution = Execution.builder()
            .tenantId(tenantId)
            .id("executionId")
            .namespace("namespace")
            .flowId("flowId")
            .taskRunList(List.of(TaskRun.builder().tenantId(tenantId).id(taskRunId).build()))
            .state(new State())
            .build();
        String value = """
        {"some":"output"}""";
        executionRepository.save(execution);

        TaskOutput taskOutput = new TaskOutput(taskRunId, tenantId, "executionId", value.getBytes(StandardCharsets.UTF_8), null);
        taskOutputRepository.save(taskOutput);

        List<OutputController.TaskOutputInformation> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/executionId"),
            Argument.listOf(OutputController.TaskOutputInformation.class)
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().taskRunId()).isEqualTo(taskRunId);
        assertThat(response.getFirst().inline()).isTrue();
    }

    @Test
    void getTaskOutputInformationShouldThrowNotFoundWhenTaskRunNotFound() {
        String tenantId = TenantService.MAIN_TENANT;

        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/" + tenantId + "/outputs/not-found"),
                Argument.listOf(OutputController.TaskOutputInformation.class)
            )
        );
    }
}