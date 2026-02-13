package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
public class WebhookRoutingTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    private static final String TESTS_FLOW_NS = "io.kestra.tests";

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookExactPathMatch() {
        // Test that exact path matches work with POST
        var response = client.toBlocking().exchange(
            POST(
                "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey",
                "{\"test\": \"data\"}"
            ),
            String.class
        );

        assertThat((Object)response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookExactPathMatchWithGet() {
        // Test that exact path matches work with GET
        var response = client.toBlocking().exchange(
            GET("/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey"),
            String.class
        );

        assertThat((Object)response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookExactPathMatchWithPut() {
        // Test that exact path matches work with PUT
        var response = client.toBlocking().exchange(
            PUT(
                "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey",
                "{\"test\": \"data\"}"
            ),
            String.class
        );

        assertThat((Object)response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookWildcardPathReturns404() {
        // Test that wildcard paths return 404 for base Webhook implementation
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey/extra/path",
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookWildcardPathReturns404WithGet() {
        // Test that wildcard paths return 404 for base Webhook with GET
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                GET("/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey/extra"),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookWildcardPathReturns404WithPut() {
        // Test that wildcard paths return 404 for base Webhook with PUT
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                PUT(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey/extra/path/segments",
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookWithTrailingSlashReturns404() {
        // Test that paths with trailing slash (but no additional segments) also return 404
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey/",
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookWithQueryParameters() {
        // Test that exact path with query parameters still works
        var response = client.toBlocking().exchange(
            POST(
                "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey?param1=value1&param2=value2",
                "{\"test\": \"data\"}"
            ),
            String.class
        );

        assertThat((Object)response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookInvalidKey() {
        // Test that wrong key returns 404
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/wrongkey",
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).contains("Webhook not found");
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookInvalidFlow() {
        // Test that wrong flow returns 404
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/nonexistent-flow/testkey",
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).contains("Flow not found");
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookInvalidNamespace() {
        // Test that wrong namespace returns 404
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(
                    "/api/v1/main/executions/webhook/invalid.namespace/webhook-routing-test/testkey",
                    "{\"test\": \"data\"}"
                ),
                String.class
            )
        );

        assertThat((Object) exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).contains("Flow not found");
    }

    @Test
    @LoadFlows(value = {"flows/valids/webhook-routing-test.yaml"})
    void webhookWithDifferentBodyFormats() {
        // Test JSON body
        var jsonResponse = client.toBlocking().exchange(
            POST(
                "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey",
                "{\"key\": \"value\"}"
            ),
            String.class
        );
        assertThat((Object) jsonResponse.getStatus()).isEqualTo(HttpStatus.OK);

        // Test plain text body
        var textResponse = client.toBlocking().exchange(
            POST(
                "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey",
                "plain text body"
            ),
            String.class
        );
        assertThat((Object)textResponse.getStatus()).isEqualTo(HttpStatus.OK);

        // Test array body
        var arrayResponse = client.toBlocking().exchange(
            POST(
                "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-routing-test/testkey",
                "[1, 2, 3]"
            ),
            String.class
        );
        assertThat((Object) arrayResponse.getStatus()).isEqualTo(HttpStatus.OK);
    }
}
