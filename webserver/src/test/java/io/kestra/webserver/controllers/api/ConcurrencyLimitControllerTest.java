package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
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

    @Test
    void shouldReturnBadRequestWhenUpdatingWithInvalidConcurrencyLimit() {
        // Given - a request without the required running count
        var invalid = new ConcurrencyLimitController.ConcurrencyLimitUpdateRequest("namespace", "flowId", null);

        // When
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                PUT("/api/v1/main/concurrency-limit", invalid)
            )
        );

        // Then - Micronaut returns 422 for @Body @Valid bean validation failures
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
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
            PUT("/api/v1/main/concurrency-limit", new ConcurrencyLimitController.ConcurrencyLimitUpdateRequest(concurrencyLimit.getNamespace(), concurrencyLimit.getFlowId(), 99)),
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
                PUT("/api/v1/main/concurrency-limit", new ConcurrencyLimitController.ConcurrencyLimitUpdateRequest(existing.getNamespace(), existing.getFlowId(), -5))
            )
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void shouldUpdateNamespaceConcurrencyLimitWhenFlowIsEmpty() {
        // Given - a namespace-scoped counter, stored with an empty flow id
        concurrencyLimitRepository.update(ConcurrencyLimit.builder().tenantId("main").namespace("scoped.namespace").flowId("").running(3).build());

        // When
        ConcurrencyLimit updated = client.toBlocking().retrieve(
            PUT("/api/v1/main/concurrency-limit", new ConcurrencyLimitController.ConcurrencyLimitUpdateRequest("scoped.namespace", null, 0)),
            ConcurrencyLimit.class
        );

        // Then
        assertThat(updated.getNamespace()).isEqualTo("scoped.namespace");
        assertThat(updated.getFlowId()).isEmpty();
        assertThat(updated.getRunning()).isZero();
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingUnknownConcurrencyLimitByBody() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                PUT("/api/v1/main/concurrency-limit", new ConcurrencyLimitController.ConcurrencyLimitUpdateRequest("unknown.namespace", "unknown-flow", 0))
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