package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.plugin.core.trigger.WebhookContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@KestraTest
class WebhookServiceTest {

    @Inject
    private WebhookService webhookService;

    @Test
    void shouldCreateExecutionFromWebhookInputs() {
        // ---- Mock Flow ----
        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("test");
        when(flow.getNamespace()).thenReturn("io.kestra.unittest");
        when(flow.getTenantId()).thenReturn(null);
        when(flow.getRevision()).thenReturn(1);
        when(flow.getVariables()).thenReturn(Map.of());

        // ---- Trigger ----
        Webhook trigger = Webhook.builder()
            .id("webhook")
            .type(Webhook.class.getName())
            .inputs(Map.of(
                "test_ciphertext", "my-secret"
            ))
            .build();

        // ---- Context ----
        WebhookContext context = mock(WebhookContext.class);
        when(context.flow()).thenReturn(flow);

        // ---- Execute ----
        Optional<Execution> result =
            webhookService.newExecution(context, flow, trigger, null);

        // ---- Assert ----
        assertThat(result).isPresent();
    }
}