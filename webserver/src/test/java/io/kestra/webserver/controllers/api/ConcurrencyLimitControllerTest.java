package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.PUT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class ConcurrencyLimitControllerTest {

    @Inject
    @Client("/")
    private ReactorHttpClient client;

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
    }
}