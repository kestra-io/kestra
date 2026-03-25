package io.kestra.webserver.controllers.api;

import java.nio.charset.StandardCharsets;

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

    @Test
    void getTaskOutput() {
        String taskRunId = "taskRunId";
        String tenantId = TenantService.MAIN_TENANT;
        byte[] value = "value".getBytes(StandardCharsets.UTF_8);

        TaskOutput taskOutput = new TaskOutput(taskRunId, tenantId, "executionId", value, null);
        taskOutputRepository.save(taskOutput);

        TaskOutput response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/outputs/executionId/" + taskRunId),
            TaskOutput.class
        );

        assertThat(response.taskRunId()).isEqualTo(taskRunId);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.value()).isEqualTo(value);
    }

    @Test
    void getNotFound() {
        String taskRunId = "notFound";
        String tenantId = TenantService.MAIN_TENANT;

        assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/" + tenantId + "/outputs/executionId/" + taskRunId),
                TaskOutput.class
            )
        );
    }
}