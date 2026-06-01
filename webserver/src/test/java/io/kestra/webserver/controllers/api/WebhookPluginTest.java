package io.kestra.webserver.controllers.api;

import java.util.Map;

import io.kestra.core.junit.annotations.LoadFlows;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlowsWithTenant;
import io.kestra.core.runners.TestRunnerUtils;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.POST;
import static io.micronaut.http.HttpRequest.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
public class WebhookPluginTest {
    private static final String TESTS_FLOW_NS = "io.kestra.tests";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private TestRunnerUtils runnerUtils;

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows("flows/valids/webhook-plugin.yaml")
    void pluginWorks() {
        var response = client.toBlocking().exchange(
            PUT(
                "/api/v1/%s/executions/webhook/io.kestra.tests/webhook-plugin/case1".formatted(MAIN_TENANT),
                "{\"test\": \"data\"}"
            ),
            String.class
        );

        assertThat((Object) response.getStatus()).isEqualTo(HttpStatus.OK);

        var execution = runnerUtils.awaitFlowExecution(
            e -> e.getTrigger() != null && e.getTrigger().getId().equals("webhook1"),
            MAIN_TENANT, TESTS_FLOW_NS, "webhook-plugin"
        );
        assertThat(((Map<String, String>) execution.getTrigger().getVariables().get("body")).get("test")).isEqualTo("data");
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows("flows/valids/webhook-plugin.yaml")
    void pluginWorks_webhook2() {
        var response = client.toBlocking().exchange(
            PUT(
                "/api/v1/%s/executions/webhook/io.kestra.tests/webhook-plugin/case2".formatted(MAIN_TENANT),
                "{\"test\": \"data\"}"
            ),
            String.class
        );

        assertThat((Object) response.getStatus()).isEqualTo(HttpStatus.OK);

        var execution = runnerUtils.awaitFlowExecution(
            e -> e.getTrigger() != null && e.getTrigger().getId().equals("webhook2"),
            MAIN_TENANT, TESTS_FLOW_NS, "webhook-plugin"
        );
        assertThat(((Map<String, String>) execution.getTrigger().getVariables().get("body")).get("test")).isEqualTo("data");
    }

    @Test
    @LoadFlows("flows/valids/webhook-plugin.yaml")
    void webbookFailedExecution() {
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(
                    "/api/v1/%s/executions/webhook/io.kestra.tests/webhook-plugin/case2/failed".formatted(MAIN_TENANT),
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
