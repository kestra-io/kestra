package io.kestra.plugin.core.trigger;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.WebhookService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
public class WebhookContext {
    @Getter
    HttpRequest request;

    @Getter
    String path;

    @Getter
    Flow flow;

    @Getter
    AbstractWebhookTrigger trigger;

    @Getter
    WebhookService webhookService;
}
