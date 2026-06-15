package io.kestra.plugin.core.trigger;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.AsyncOperationWaiter;
import io.kestra.core.services.WebhookService;

import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
public class WebhookBuilderTest {
    @Inject
    WebhookService webhookService;

    @MockBean(AsyncOperationWaiter.class)
    AsyncOperationWaiter asyncOperationWaiter() {
        AsyncOperationWaiter mock = mock(AsyncOperationWaiter.class);
        when(mock.submit(any(), any(), any()))
            .thenReturn(Mono.just(new AsyncOperationProcessedEvent(
                "op-id", null, "item-id", AsyncOperationProcessedEvent.Outcome.SUCCEEDED, null, Instant.now()
            )));
        return mock;
    }

    @Test
    void testWebhookBuilder() {
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

        var webhookContext = new WebhookContext(request, null, flow, webhook, webhookService);
        var evaluate = webhook.evaluate(webhookContext);

        assertThat(evaluate).isNotNull();
        assertThat(Objects.requireNonNull(evaluate.block()).getStatus().getCode()).isEqualTo(200);
    }
}
