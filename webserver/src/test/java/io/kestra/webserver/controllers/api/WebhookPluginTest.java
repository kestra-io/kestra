package io.kestra.webserver.controllers.api;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.POST;
import static io.micronaut.http.HttpRequest.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
public class WebhookPluginTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private DispatchQueueInterface<Execution> executionQueue;

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows(value = { "flows/valids/webhook-plugin.yaml" })
    void pluginWorks() throws InterruptedException {
        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> executionReference = new AtomicReference<>();
        executionQueue.addListener(execution ->
        {
            if (execution.getFlowId().equals("webhook-plugin") && execution.getTrigger() != null && execution.getTrigger().getId().equals("webhook1")) {
                queueCount.countDown();
                executionReference.set(execution);
            }
        });

        var response = client.toBlocking().exchange(
            PUT(
                "/api/v1/main/executions/webhook/io.kestra.tests/webhook-plugin/case1",
                "{\"test\": \"data\"}"
            ),
            String.class
        );

        assertThat((Object) response.getStatus()).isEqualTo(HttpStatus.OK);

        assertTrue(queueCount.await(10, TimeUnit.SECONDS));
        assertThat(((Map<String, String>) Objects.requireNonNull(executionReference.get()).getTrigger().getVariables().get("body")).get("test")).isEqualTo("data");
    }

    @Test
    @LoadFlows(value = { "flows/valids/webhook-plugin.yaml" })
    void webbookFailedExecution() throws InterruptedException {
        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> executionReference = new AtomicReference<>();
        executionQueue.addListener(execution ->
        {
            if (execution.getFlowId().equals("webhook-plugin") && execution.getTrigger() != null && execution.getTrigger().getId().equals("webhook2")) {
                queueCount.countDown();
                executionReference.set(execution);
            }
        });

        // Test that wrong namespace returns 404
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(
                    "/api/v1/main/executions/webhook/io.kestra.tests/webhook-plugin/case2/failed",
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        assertTrue(queueCount.await(10, TimeUnit.SECONDS));
        assertThat(Objects.requireNonNull(executionReference.get()).getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

}
