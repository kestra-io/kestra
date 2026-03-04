package io.kestra.plugin.core.trigger;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.WebhookService;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public class WebhookBuilderTest {
    @Inject
    WebhookService webhookService;

    @Test
    void testWebhookBuilder() {
        // Test that the Webhook class can be built with the new hierarchy
        Webhook webhook = Webhook.builder()
            .id("test-webhook")
            .type(Webhook.class.getName())
            .key("test-key")
            .build();

        assertThat(webhook).isNotNull();
        assertThat(webhook.getKey()).isEqualTo("test-key");
        assertThat(webhook.getId()).isEqualTo("test-webhook");
    }

    @Test
    void testWebhookEvaluate() throws Exception {
        Webhook webhook = Webhook.builder()
            .id("test-webhook")
            .type(Webhook.class.getName())
            .key("testkey")
            .build();

        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("io.kestra.tests")
            .build();

        HttpRequest request = HttpRequest.of(URI.create("/api/v1/main/executions/webhook/io.kestra.tests/test-flow/testkey"));
        String path = null;

        var webhookContext = new WebhookContext(
            request,
            path,
            flow,
            webhook,
            webhookService
        );

        var evaluate = webhook.evaluate(webhookContext);

        assertThat(evaluate).isNotNull();
        assertThat(Objects.requireNonNull(evaluate.block()).getStatus().getCode()).isEqualTo(200);
    }
}
