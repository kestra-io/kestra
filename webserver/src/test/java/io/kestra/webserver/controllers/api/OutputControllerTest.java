package io.kestra.webserver.controllers.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.services.ExecutionOutputService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Inject
    private ExecutionOutputService executionOutputService;

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
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/tasks/executionId/" + taskRunId),
            String.class
        );

        assertThat(response).isEqualTo(value);
    }

    @Test
    void getTaskOutputShouldThrowNotFoundWhenTaskRunNotFound() {
        String taskRunId = "notFound";
        String tenantId = TenantService.MAIN_TENANT;

        assertThatThrownBy(() -> client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/tasks/executionId/" + taskRunId),
            TaskOutput.class
        ))
            .isInstanceOf(HttpClientResponseException.class)
            .extracting(e -> ((HttpClientResponseException) e).getStatus())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTaskOutputInformationShouldSkipOrphanedTaskOutput() {
        String tenantId = TenantService.MAIN_TENANT;
        String executionId = IdUtils.create();
        String taskRunId = "taskRunId";
        String orphanTaskRunId = "orphanTaskRunId";
        // A LoopUntil iteration prunes the previous iteration's task run but not its stored output,
        // so the execution can carry an output row whose task run id no longer exists (#18231).
        var execution = Execution.builder()
            .tenantId(tenantId)
            .id(executionId)
            .namespace("namespace")
            .flowId("flowId")
            .taskRunList(List.of(TaskRun.builder().tenantId(tenantId).id(taskRunId).build()))
            .state(new State())
            .build();
        String value = """
            {"some":"output"}""";
        executionRepository.save(execution);

        taskOutputRepository.save(new TaskOutput(taskRunId, tenantId, executionId, value.getBytes(StandardCharsets.UTF_8), null));
        taskOutputRepository.save(new TaskOutput(orphanTaskRunId, tenantId, executionId, value.getBytes(StandardCharsets.UTF_8), null));

        List<OutputController.TaskOutputInformation> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/tasks/" + executionId),
            Argument.listOf(OutputController.TaskOutputInformation.class)
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().taskRunId()).isEqualTo(taskRunId);
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
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/tasks/executionId"),
            Argument.listOf(OutputController.TaskOutputInformation.class)
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().taskRunId()).isEqualTo(taskRunId);
        assertThat(response.getFirst().inline()).isTrue();
    }

    @Test
    void getTaskOutputInformationShouldThrowNotFoundWhenTaskRunNotFound() {
        String tenantId = TenantService.MAIN_TENANT;

        assertThatThrownBy(() -> client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/tasks/not-found"),
            Argument.listOf(OutputController.TaskOutputInformation.class)
        ))
            .isInstanceOf(HttpClientResponseException.class)
            .extracting(e -> ((HttpClientResponseException) e).getStatus())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getExecutionOutputs() throws InternalException {
        // Given
        String tenantId = TenantService.MAIN_TENANT;
        String executionId = IdUtils.create();
        var execution = Execution.builder()
            .tenantId(tenantId)
            .id(executionId)
            .namespace("namespace")
            .flowId("flowId")
            .state(new State(State.Type.SUCCESS))
            .build();
        executionRepository.save(execution);
        executionOutputService.saveOutputs(execution, Map.of("some", "output"));

        // When - every route now has a literal second segment ('executions' or 'tasks'), so there is no ambiguity
        Map<String, Object> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/executions/" + executionId),
            Argument.mapOf(String.class, Object.class)
        );

        // Then
        assertThat(response).containsExactlyInAnyOrderEntriesOf(Map.of("some", "output"));
    }

    @Test
    void getExecutionOutputsShouldThrowNotFoundWhenExecutionBelongsToAnotherTenant() throws InternalException {
        // Given
        String executionId = IdUtils.create();
        var execution = Execution.builder()
            .tenantId("another-tenant")
            .id(executionId)
            .namespace("namespace")
            .flowId("flowId")
            .state(new State(State.Type.SUCCESS))
            .build();
        executionRepository.save(execution);
        executionOutputService.saveOutputs(execution, Map.of("some", "output"));

        // When / Then - the request is resolved on the MAIN tenant, so it must not see another tenant's outputs
        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/" + TenantService.MAIN_TENANT + "/outputs/executions/" + executionId),
                Argument.mapOf(String.class, Object.class)
            )
        );
    }

    @Test
    void getExecutionOutputsShouldThrowNotFoundWhenExecutionNotFound() {
        String tenantId = TenantService.MAIN_TENANT;

        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/" + tenantId + "/outputs/executions/not-found"),
                Argument.mapOf(String.class, Object.class)
            )
        );
    }
}