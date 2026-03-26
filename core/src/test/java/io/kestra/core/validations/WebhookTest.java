package io.kestra.core.validations;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.plugin.core.trigger.Webhook;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class WebhookTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void webhookResponseContentTypeValidation() {
        // Invalid content type
        var invalidWebhook = Webhook.builder()
            .id("webhook")
            .type(Webhook.class.getName())
            .key("webhook")
            .responseContentType("text/html")
            .build();

        assertThat(modelValidator.isValid(invalidWebhook).isPresent()).isTrue();
        assertThat(modelValidator.isValid(invalidWebhook).get().getMessage()).contains("invalid webhook: responseContentType must be either 'application/json' or 'text/plain'");

        // Valid content types
        var validPlainText = Webhook.builder()
            .id("webhook")
            .type(Webhook.class.getName())
            .key("webhook")
            .responseContentType("text/plain")
            .build();

        assertThat(modelValidator.isValid(validPlainText).isPresent()).isFalse();

        var validJson = Webhook.builder()
            .id("webhook")
            .type(Webhook.class.getName())
            .key("webhook")
            .responseContentType("application/json")
            .build();

        assertThat(modelValidator.isValid(validJson).isPresent()).isFalse();

        // Null content type (default, should be valid)
        var nullContentType = Webhook.builder()
            .id("webhook")
            .type(Webhook.class.getName())
            .key("webhook")
            .build();

        assertThat(modelValidator.isValid(nullContentType).isPresent()).isFalse();
    }
}
