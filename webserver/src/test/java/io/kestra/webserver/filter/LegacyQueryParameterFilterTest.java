package io.kestra.webserver.filter;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.jdbc.JdbcTestUtils;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class LegacyQueryParameterFilterTest {
    private static final String NAMESPACE = "io.kestra.tests.legacyparams";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    FlowRepositoryInterface flowRepository;

    @BeforeEach
    void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
        createFlow("keep-a");
        createFlow("keep-b");
    }

    @Test
    void shouldRejectFlatNamespaceOnDeleteByQueryAndDeleteNothing() {
        assertThatThrownBy(() -> client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/flows/delete/by-query?namespace=" + NAMESPACE)))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e ->
            {
                assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
                assertThat(e.getResponse().getBody(String.class).orElseThrow())
                    .contains("invalid-query-filters")
                    .contains("'namespace'")
                    .contains("filters[namespace][EQUALS]");
            });

        assertThat(flowRepository.findByNamespace("main", NAMESPACE)).hasSize(2);
    }

    @Test
    void shouldRejectEveryFlatFilterOnAMutatingExecutionEndpoint() {
        assertThatThrownBy(() -> client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/executions/kill/by-query?flowId=x&namespace=y")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e ->
            {
                assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
                assertThat(e.getResponse().getBody(String.class).orElseThrow()).contains("'flowId'").contains("'namespace'");
            });
    }

    @Test
    void shouldStillAcceptTheFiltersFormAndTheEndpointOwnParameters() {
        HttpResponse<?> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/triggers/set-disabled/by-query?disabled=true&filters[namespace][EQUALS]=" + NAMESPACE, Map.of())
        );

        assertThat(response.getStatus().getCode()).isLessThan(300);
    }

    @Test
    void shouldLeaveReadOnlyExportsAlone() {
        HttpResponse<byte[]> response = client.toBlocking().exchange(HttpRequest.GET("/api/v1/main/flows/export/by-query?namespace=" + NAMESPACE), byte[].class);

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldNotCollideWithAnyByQueryEndpointOwnQueryValue() {
        // Query values the by-query endpoints bind themselves. If a QueryFilter.Field ever takes one of these
        // names, the filter would reject legitimate calls, so this must stay empty.
        Set<String> endpointParameters = Set.of(
            "includeNonTerminated", "deleteLogs", "deleteMetrics", "deleteStorage", "latestRevision",
            "newStatus", "newState", "disabled", "recoverMissedSchedules"
        );

        assertThat(LegacyQueryParameterFilter.LEGACY_PARAMETERS).doesNotContainAnyElementsOf(endpointParameters);
    }

    private void createFlow(String id) {
        String source = """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(id, NAMESPACE);
        client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/flows", source).contentType(MediaType.APPLICATION_YAML), Flow.class);
    }
}
