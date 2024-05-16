package io.kestra.core.validations;

import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.core.models.validations.ModelValidator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
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

        assertThat(modelValidator.isValid(webhook).isPresent(), is(true));
        assertThat(modelValidator.isValid(webhook).get().getMessage(), containsString("invalid webhook: conditions of type MultipleCondition are not supported"));
    }
}
