package io.kestra.webserver.controllers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.jdbc.repository.AbstractJdbcMetricRepository;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class MetricControllerTest extends JdbcH2ControllerTest {
    private static final String TESTS_FLOW_NS = "io.kestra.tests";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    AbstractJdbcMetricRepository jdbcMetricRepository;

    @SuppressWarnings("unchecked")
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
                .POST("/api/v1/executions/" + namespace + "/" + flowId + (wait ? "?wait=true" : ""), requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
    }
}