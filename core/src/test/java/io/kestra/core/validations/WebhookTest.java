package io.kestra.core.validations;

import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class WebhookTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void webhookValidation()  {
        var webhook = Webhook.builder()
            .id("webhook")
            .type(Webhook.class.getName())
            .key("webhook")
            .conditions(
                List.of(
                    MultipleCondition.builder().id("multiple").type(MultipleCondition.class.getName()).build()
                )
            )
            .build();

        assertThat(modelValidator.isValid(webhook).isPresent()).isTrue();
        assertThat(modelValidator.isValid(webhook).get().getMessage()).contains("invalid webhook: conditions of type MultipleCondition are not supported");
    }

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
