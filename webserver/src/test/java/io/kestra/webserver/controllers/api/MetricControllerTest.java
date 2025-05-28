package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class MetricControllerTest {
    private static final String TESTS_FLOW_NS = "io.kestra.tests";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void searchByExecution() {
        Execution result = triggerExecution(TESTS_FLOW_NS, "minimal", null, true);
        assertThat(result).isNotNull();

        PagedResults<MetricEntry> metrics = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/metrics/" + result.getId()),
            Argument.of(PagedResults.class, MetricEntry.class)
        );
        assertThat(metrics.getTotal()).isEqualTo(2L);
    }

    private Execution triggerExecution(String namespace, String flowId, MultipartBody requestBody, Boolean wait) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + namespace + "/" + flowId + (wait ? "?wait=true" : ""), requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
    }
}