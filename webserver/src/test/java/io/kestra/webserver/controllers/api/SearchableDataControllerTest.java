package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.searchable.SearchableData;
import io.kestra.core.searchable.SearchableDataRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class SearchableDataControllerTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private SearchableDataRepositoryInterface searchableDataRepository;

    @Test
    void listSearchableData() {
        // Given
        String tenantId = TenantService.MAIN_TENANT;
        var execution = saveExecution(tenantId);
        searchableDataRepository.save(new SearchableData(tenantId, execution.getId(), "orderIds", "ORD-8841"));
        searchableDataRepository.save(new SearchableData(tenantId, execution.getId(), "customerId", "CUST-12"));
        searchableDataRepository.save(new SearchableData(tenantId, execution.getId(), "region", "eu"));
        String uri = "/api/v1/" + tenantId + "/searchable_data/" + execution.getId();

        // When
        PagedResults<Map<String, Object>> first = client.toBlocking().retrieve(
            HttpRequest.GET(uri + "?page=1&size=2"), Argument.of(PagedResults.class, Argument.mapOf(String.class, Object.class))
        );
        PagedResults<Map<String, Object>> second = client.toBlocking().retrieve(
            HttpRequest.GET(uri + "?page=2&size=2"), Argument.of(PagedResults.class, Argument.mapOf(String.class, Object.class))
        );

        // Then
        assertThat(first.getTotal()).isEqualTo(3);
        assertThat(first.getResults()).containsExactly(
            Map.of("dataId", "customerId", "value", "CUST-12"),
            Map.of("dataId", "orderIds", "value", "ORD-8841")
        );
        assertThat(second.getResults()).containsExactly(Map.of("dataId", "region", "value", "eu"));
    }

    @Test
    void listSearchableDataShouldReturnEmptyWhenTheExecutionHasNone() {
        String tenantId = TenantService.MAIN_TENANT;
        var execution = saveExecution(tenantId);

        PagedResults<?> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/searchable_data/" + execution.getId()), PagedResults.class
        );

        assertThat(response.getTotal()).isZero();
        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void listSearchableDataShouldThrowNotFoundWhenExecutionNotFound() {
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/" + TenantService.MAIN_TENANT + "/searchable_data/not-found"), PagedResults.class
            )
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void listSearchableDataShouldThrowNotFoundWhenExecutionBelongsToAnotherTenant() {
        // Given
        var execution = saveExecution("another-tenant");
        searchableDataRepository.save(new SearchableData("another-tenant", execution.getId(), "orderIds", "ORD-8841"));

        // When / Then - the request is resolved on the MAIN tenant, so it must not see another tenant's outputs
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/" + TenantService.MAIN_TENANT + "/searchable_data/" + execution.getId()), PagedResults.class
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void listSearchableDataShouldListEachItemOfAnArray() {
        // Given
        String tenantId = TenantService.MAIN_TENANT;
        var execution = saveExecution(tenantId);
        searchableDataRepository.saveBatch(List.of(
            new SearchableData(tenantId, execution.getId(), "orderIds", 0, "ORD-1"),
            new SearchableData(tenantId, execution.getId(), "orderIds", 1, "ORD-2"),
            new SearchableData(tenantId, execution.getId(), "orderIds", 2, "ORD-3")
        ));

        // When
        PagedResults<Map<String, Object>> response = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/" + tenantId + "/searchable_data/" + execution.getId() + "?page=1&size=2"),
            Argument.of(PagedResults.class, Argument.mapOf(String.class, Object.class))
        );

        // Then
        assertThat(response.getTotal()).isEqualTo(3);
        assertThat(response.getResults()).containsExactly(
            Map.of("dataId", "orderIds", "value", "ORD-1"),
            Map.of("dataId", "orderIds", "value", "ORD-2")
        );
    }

    private Execution saveExecution(String tenantId) {
        return executionRepository.save(
            Execution.builder()
                .tenantId(tenantId)
                .id(IdUtils.create())
                .namespace("namespace")
                .flowId("flowId")
                .state(new State(State.Type.SUCCESS))
                .build()
        );
    }
}
