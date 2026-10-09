package io.kestra.webserver.controllers.api;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.WebhookService;
import io.kestra.plugin.core.trigger.Webhook;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@KestraTest
class WebhookSignatureTest {
    private static final String URL = "/api/v1/main/executions/webhook/io.kestra.tests/webhook-signature/testkey";
    private static final String BODY = "{ \"count\": 1.00, \"message\": \"café\", \"ok\": true }\n";
    private static final String SIGNATURE = "sha256=0d541f9ce82c02ab31853d0d99f7989d4dd1e178e1fa0643c4221648013cb22c";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    WebhookService webhookService;

    @MockBean(WebhookService.class)
    WebhookService webhookService(RunContextFactory factory) {
        WebhookService service = mock(WebhookService.class);
        when(service.runContext(any(Flow.class), any(AbstractTrigger.class)))
            .thenAnswer(invocation -> factory.of(invocation.getArgument(0, Flow.class), invocation.getArgument(1, AbstractTrigger.class)));
        when(service.newExecution(any(), any(), any(), any())).thenReturn(Optional.empty());
        return service;
    }

    @BeforeEach
    void clearCalls() {
        clearInvocations(webhookService);
    }

    @Test
    @LoadFlows("flows/valids/webhook-signature.yaml")
    void shouldVerifyRawBytesBeforeDeserializingJson() {
        var response = client.toBlocking().exchange(POST(URL, BODY).header("x-hub-signature-256", SIGNATURE), String.class);
        assertThat((Object) response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        ArgumentCaptor<Webhook.Output> output = ArgumentCaptor.forClass(Webhook.Output.class);
        verify(webhookService).newExecution(any(), any(), any(), output.capture());
        assertThat(output.getValue().getBody()).isInstanceOf(java.util.Map.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "sha256=bad", "sha256=0000000000000000000000000000000000000000000000000000000000000000" })
    @LoadFlows("flows/valids/webhook-signature.yaml")
    void shouldReturnUnauthorizedWithoutCreatingExecution(String signature) {
        var request = POST(URL, BODY);
        if (signature != null) {
            request.header("X-Hub-Signature-256", signature);
        }
        assertThatThrownBy(() -> client.toBlocking().exchange(request, String.class))
            .isInstanceOfSatisfying(
                HttpClientResponseException.class,
                e -> assertThat((Object) e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        verify(webhookService, never()).newExecution(any(), any(), any(), any());
        verify(webhookService, never()).startExecution(any());
    }

    @Test
    @LoadFlows("flows/valids/webhook-signature.yaml")
    void shouldRejectMultipartWithoutCreatingExecution() {
        var request = POST(URL, io.micronaut.http.client.multipart.MultipartBody.builder().addPart("field", "value").build())
            .contentType("multipart/form-data")
            .header("X-Hub-Signature-256", SIGNATURE);
        assertThatThrownBy(() -> client.toBlocking().exchange(request, String.class))
            .isInstanceOfSatisfying(
                HttpClientResponseException.class,
                e -> assertThat((Object) e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        verify(webhookService, never()).newExecution(any(), any(), any(), any());
    }

    @Test
    @LoadFlows("flows/valids/webhook-signature.yaml")
    void shouldRejectTamperedJsonEvenWhenItHasTheSameValues() {
        String normalized = "{\"count\":1.0,\"message\":\"café\",\"ok\":true}";
        assertThatThrownBy(() -> client.toBlocking().exchange(POST(URL, normalized).header("X-Hub-Signature-256", SIGNATURE), String.class))
            .isInstanceOfSatisfying(
                HttpClientResponseException.class,
                e -> assertThat((Object) e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        verify(webhookService, never()).newExecution(any(), any(), any(), any());
    }
}
