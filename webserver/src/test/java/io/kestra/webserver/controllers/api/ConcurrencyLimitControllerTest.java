package io.kestra.webserver.controllers.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.assertions.Problems;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.webserver.errors.ProblemDetail;
import io.kestra.webserver.errors.ProblemTypes;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
class ConcurrencyLimitControllerTest {

    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

    @Inject
    private EmbeddedServer embeddedServer;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @Test
    void shouldReturnBadRequestWhenUpdatingWithInvalidConcurrencyLimit() {
        // Given - a ConcurrencyLimit with all required fields null
        ConcurrencyLimit invalid = ConcurrencyLimit.builder().build();

        // When
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                PUT("/api/v1/main/concurrency-limit/namespace/flowId", invalid)
            )
        );

        // Then - Micronaut returns 422 for @Body @Valid bean validation failures
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void shouldReturnNotFoundWhenBodyArrivesAfterHeaders() throws Exception {
        byte[] body = "{\"namespace\":\"missing.namespace\",\"flowId\":\"missing\",\"running\":1}".getBytes(StandardCharsets.UTF_8);
        String credentials = Base64.getEncoder()
            .encodeToString((basicAuthConfiguration.getUsername() + ":" + basicAuthConfiguration.getPassword()).getBytes(StandardCharsets.UTF_8));
        String headers = "PUT /api/v1/main/concurrency-limit HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "Authorization: Basic " + credentials + "\r\n"
            + "Content-Type: application/json\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Connection: close\r\n\r\n";

        try (Socket socket = new Socket(embeddedServer.getHost(), embeddedServer.getPort())) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            out.flush();
            // a raw 404 returned while the body is still pending sent Micronaut's status route into an endless loop (kestra-ee#12895)
            Thread.sleep(300);
            out.write(body);
            out.flush();

            String statusLine = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
            assertThat(statusLine).startsWith("HTTP/1.1 404");
        }
    }

    @Test
    @ExecuteFlow("flows/valids/flow-concurrency-queue.yml")
    @SuppressWarnings("unchecked")
    void test(Execution execution) throws Exception {
        assertThat(execution).isNotNull();

        // we should have at least one concurrency limit inside the database
        PagedResults<ConcurrencyLimit> retrieved = client.toBlocking().retrieve(
            GET("/api/v1/main/concurrency-limit/search"), Argument.of(PagedResults.class, ConcurrencyLimit.class)
        );
        assertThat(retrieved.getResults()).hasSize(1);
        ConcurrencyLimit concurrencyLimit = retrieved.getResults().getFirst();
        assertThat(concurrencyLimit.getNamespace()).isEqualTo(execution.getNamespace());
        assertThat(concurrencyLimit.getFlowId()).isEqualTo(execution.getFlowId());

        // update the concurrency limit
        ConcurrencyLimit updated = client.toBlocking().retrieve(
            PUT("/api/v1/main/concurrency-limit/" + concurrencyLimit.getNamespace() + "/" + concurrencyLimit.getFlowId(), concurrencyLimit.withRunning(99)),
            ConcurrencyLimit.class
        );
        assertThat(updated).isNotNull();
        assertThat(updated.getRunning()).isEqualTo(99);

        // the flow-scoped GET returns the same record
        ConcurrencyLimit scoped = client.toBlocking().retrieve(
            GET("/api/v1/main/concurrency-limit/" + concurrencyLimit.getNamespace() + "/" + concurrencyLimit.getFlowId()),
            ConcurrencyLimit.class
        );
        assertThat(scoped.getNamespace()).isEqualTo(concurrencyLimit.getNamespace());
        assertThat(scoped.getFlowId()).isEqualTo(concurrencyLimit.getFlowId());
    }

    @Test
    @ExecuteFlow("flows/valids/flow-concurrency-queue.yml")
    void shouldRejectNegativeRunningCount(Execution execution) {
        ConcurrencyLimit existing = client.toBlocking().retrieve(
            GET("/api/v1/main/concurrency-limit/" + execution.getNamespace() + "/" + execution.getFlowId()),
            ConcurrencyLimit.class
        );

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                PUT("/api/v1/main/concurrency-limit/" + existing.getNamespace() + "/" + existing.getFlowId(), existing.withRunning(-5))
            )
        );

        ProblemDetail problem = Problems.assertProblem(e, ProblemTypes.VALIDATION_FAILED);
        assertThat(problem.detail()).isEqualTo("running: must be greater than or equal to 0");
        Problems.assertErrors(e).singleElement().satisfies(error -> assertThat(error.path()).isEqualTo("running"));
    }

    @Test
    @ExecuteFlow("flows/valids/flow-concurrency-queue.yml")
    void shouldIdentifyConcurrencyLimitFromPathNotBody(Execution execution) {
        // A body naming a different flow must not retarget it: the path identifies the resource.
        ConcurrencyLimit spoofed = ConcurrencyLimit.builder()
            .tenantId("main")
            .namespace("evil.namespace")
            .flowId("evil-flow")
            .running(42)
            .build();

        ConcurrencyLimit updated = client.toBlocking().retrieve(
            PUT("/api/v1/main/concurrency-limit/" + execution.getNamespace() + "/" + execution.getFlowId(), spoofed),
            ConcurrencyLimit.class
        );

        assertThat(updated.getNamespace()).isEqualTo(execution.getNamespace());
        assertThat(updated.getFlowId()).isEqualTo(execution.getFlowId());
        assertThat(updated.getRunning()).isEqualTo(42);

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                GET("/api/v1/main/concurrency-limit/evil.namespace/evil-flow")
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldResetNamespaceAndTenantCountersFromBody() {
        ConcurrencyLimit namespaceRow = concurrencyLimitRepository.update(ConcurrencyLimit.builder().tenantId("main").namespace("io.kestra.reset").flowId("").running(3).build());
        ConcurrencyLimit tenantRow = concurrencyLimitRepository.update(ConcurrencyLimit.builder().tenantId("main").namespace("").flowId("").running(5).build());
        try {
            assertResetsNamespaceAndTenantCounters();
        } finally {
            concurrencyLimitRepository.delete(namespaceRow);
            concurrencyLimitRepository.delete(tenantRow);
        }
    }

    private void assertResetsNamespaceAndTenantCounters() {
        ConcurrencyLimit namespaceCounter = client.toBlocking().retrieve(
            PUT("/api/v1/main/concurrency-limit", Map.of("namespace", "io.kestra.reset", "flowId", "", "running", 0)),
            ConcurrencyLimit.class
        );
        ConcurrencyLimit tenantCounter = client.toBlocking().retrieve(
            PUT("/api/v1/main/concurrency-limit", Map.of("running", 1)),
            ConcurrencyLimit.class
        );

        assertThat(namespaceCounter.getRunning()).isZero();
        assertThat(concurrencyLimitRepository.findById("main", "io.kestra.reset", "").orElseThrow().getRunning()).isZero();
        assertThat(tenantCounter.getRunning()).isEqualTo(1);
        assertThat(concurrencyLimitRepository.findById("main", "", "").orElseThrow().getRunning()).isEqualTo(1);
    }

    @Test
    void shouldReturnNotFoundWhenResettingUnknownCounter() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                PUT("/api/v1/main/concurrency-limit", Map.of("namespace", "unknown.namespace", "flowId", "", "running", 0))
            )
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldReturnNotFoundWhenGettingConcurrencyLimitForUnknownFlow() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                GET("/api/v1/main/concurrency-limit/unknown.namespace/unknown-flow")
            )
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }
}