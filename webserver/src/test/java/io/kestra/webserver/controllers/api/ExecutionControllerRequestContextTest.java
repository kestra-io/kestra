package io.kestra.webserver.controllers.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class ExecutionControllerRequestContextTest {
    private static final String BOUNDARY = "kestra-boundary";

    @Inject
    EmbeddedServer server;

    @Inject
    BasicAuthService.BasicAuthConfiguration basicAuth;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    // Mirrors the EE tenant service with no fallback tenant: the tenant is only known while a request is bound.
    @MockBean(TenantService.class)
    TenantService requestScopedTenantService() {
        return new TenantService() {
            @Override
            public String resolveTenant() {
                return ServerRequestContext.currentRequest().isPresent() ? MAIN_TENANT : null;
            }
        };
    }

    @Test
    @LoadFlows("flows/valids/inputs-small-files.yaml")
    void shouldReturnCreatedExecutionWhenMultipartBodyArrivesAfterInputsAreRead() throws Exception {
        String content = "x".repeat(32 * 1024);
        String head = "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"f\"; filename=\"in.xlsx\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n"
            + content;
        String tail = content + "\r\n--" + BOUNDARY + "--\r\n";
        // Stalling mid-body makes the Netty event loop, not the request thread, complete the inputs.
        Flux<ByteBuffer> body = Flux.concat(
            Mono.just(ByteBuffer.wrap(head.getBytes(StandardCharsets.UTF_8))),
            Mono.delay(Duration.ofMillis(500)).thenReturn(ByteBuffer.wrap(tail.getBytes(StandardCharsets.UTF_8)))
        );
        String credentials = basicAuth.getUsername() + ":" + basicAuth.getPassword();

        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(
                HttpRequest.newBuilder(URI.create(server.getURL() + "/api/v1/main/executions/io.kestra.tests/inputs-small-files"))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.fromPublisher(JdkFlowAdapter.publisherToFlowPublisher(body)))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
        }

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Execution execution = JacksonMapper.ofJson().readValue(response.body(), Execution.class);
        assertThat(executionRepository.findById(TenantService.MAIN_TENANT, execution.getId())).isPresent();
    }
}
