package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import static io.micronaut.http.HttpStatus.REQUEST_ENTITY_TOO_LARGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Enable the queue message-size guard at its production 1MB limit; raise the HTTP limits so an oversized payload
// reaches the guard rather than being rejected by the server first.
@KestraTest
@Property(name = "kestra.queue.message-protection.enabled", value = "true")
@Property(name = "kestra.queue.message-protection.limit", value = "1048576")
@Property(name = "micronaut.server.max-request-size", value = "10485760")
@Property(name = "micronaut.server.multipart.max-file-size", value = "10485760")
class ExecutionControllerMessageProtectionTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    private static final String OVERSIZED_VALUE = "x".repeat(2_000_000);

    @Test
    @LoadFlows("flows/valids/message-protection-input.yaml")
    void largeInputReturnsRequestEntityTooLarge() {
        MultipartBody body = MultipartBody.builder()
            .addPart("v", OVERSIZED_VALUE)
            .build();

        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest
                    .POST("/api/v1/main/executions/io.kestra.tests/message-protection-input", body)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
            )
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(REQUEST_ENTITY_TOO_LARGE.getCode());
    }

    @Test
    @LoadFlows("flows/valids/webhook.yaml")
    void largeWebhookBodyReturnsRequestEntityTooLarge() {
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest
                    .POST("/api/v1/main/executions/webhook/io.kestra.tests/webhook/a-secret-key", OVERSIZED_VALUE)
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
            )
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(REQUEST_ENTITY_TOO_LARGE.getCode());
    }
}
