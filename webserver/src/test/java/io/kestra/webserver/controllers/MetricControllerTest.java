package io.kestra.webserver.controllers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.repository.memory.MemoryMetricRepository;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

class MetricControllerTest extends AbstractMemoryRunnerTest {
    private static final String TESTS_FLOW_NS = "io.kestra.tests";

    @Inject
    @Client("/")
    RxHttpClient client;

    @Inject
    MemoryMetricRepository memoryMetricRepository;

    @Test
    void findByExecution() {
        Execution result = triggerExecution(TESTS_FLOW_NS, "minimal", null, true);
        assertThat(result, notNullValue());

        PagedResults<MetricEntry> metrics = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/metrics/" + result.getId()),
            Argument.of(PagedResults.class, MetricEntry.class)
        );
        assertThat(metrics.getTotal(), is(2L));
    }

    private Execution triggerExecution(String namespace, String flowId, MultipartBody requestBody, Boolean wait) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/trigger/" + namespace + "/" + flowId + (wait ? "?wait=true" : ""), requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
    }
}