package io.kestra.core.validations;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.plugin.core.trigger.Webhook;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class WebhookTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void webhookValidation() {
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
}
