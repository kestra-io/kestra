package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.DELETE;
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
        // Given - a ConcurrencyLimit with all required fields null
        ConcurrencyLimit invalid = ConcurrencyLimit.builder().build();

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
    void shouldReturnNotFoundWhenUpdatingAMissingConcurrencyLimit() {
        // Given - a ConcurrencyLimit that does not exist in the database
        ConcurrencyLimit missing = new ConcurrencyLimit("main", "some.namespace", "unknown-flow", 1);

        // When
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(PUT("/api/v1/main/concurrency-limit", missing))
        );

        // Then
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingAMissingConcurrencyLimit() {
        // When
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(DELETE("/api/v1/main/concurrency-limit?namespace=some.namespace&flowId=unknown-flow"))
        );

        // Then
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldUpdateAndDeleteNamespaceAndTenantScopedConcurrencyLimits() {
        // Given - namespace and tenant scoped counter rows, stored with empty flowId / namespace
        ConcurrencyLimit namespaceScoped = concurrencyLimitRepository.update(new ConcurrencyLimit("main", "scoped.namespace", "", 2));
        ConcurrencyLimit tenantScoped = concurrencyLimitRepository.update(new ConcurrencyLimit("main", "", "", 3));

        try {
            // When - updating both scoped rows
            ConcurrencyLimit updatedNamespaceScoped = client.toBlocking().retrieve(
                PUT("/api/v1/main/concurrency-limit", namespaceScoped.withRunning(0)),
                ConcurrencyLimit.class
            );
            ConcurrencyLimit updatedTenantScoped = client.toBlocking().retrieve(
                PUT("/api/v1/main/concurrency-limit", tenantScoped.withRunning(0)),
                ConcurrencyLimit.class
            );

            // Then
            assertThat(updatedNamespaceScoped.getRunning()).isZero();
            assertThat(updatedTenantScoped.getRunning()).isZero();

            // When - deleting both scoped rows
            HttpResponse<Void> namespaceScopedDeletion = client.toBlocking().exchange(
                DELETE("/api/v1/main/concurrency-limit?namespace=scoped.namespace")
            );
            HttpResponse<Void> tenantScopedDeletion = client.toBlocking().exchange(
                DELETE("/api/v1/main/concurrency-limit")
            );

            // Then
            assertThat(namespaceScopedDeletion.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
            assertThat(tenantScopedDeletion.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
            assertThat(concurrencyLimitRepository.findById("main", "scoped.namespace", "")).isEmpty();
            assertThat(concurrencyLimitRepository.findById("main", "", "")).isEmpty();
        } finally {
            concurrencyLimitRepository.delete(namespaceScoped);
            concurrencyLimitRepository.delete(tenantScoped);
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
            PUT("/api/v1/main/concurrency-limit", concurrencyLimit.withRunning(99)),
            ConcurrencyLimit.class
        );
        assertThat(updated).isNotNull();
        assertThat(updated.getRunning()).isEqualTo(99);

        // delete the concurrency limit
        HttpResponse<Void> deletion = client.toBlocking().exchange(
            DELETE("/api/v1/main/concurrency-limit?namespace=" + concurrencyLimit.getNamespace() + "&flowId=" + concurrencyLimit.getFlowId())
        );
        assertThat(deletion.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
        assertThat(concurrencyLimitRepository.findById("main", concurrencyLimit.getNamespace(), concurrencyLimit.getFlowId())).isEmpty();
    }
}
