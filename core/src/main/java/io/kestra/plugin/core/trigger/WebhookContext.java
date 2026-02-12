package io.kestra.plugin.core.trigger;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.WebhookService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
public record WebhookContext(
    HttpRequest request,
    String path,
    Flow flow,
    AbstractWebhookTrigger trigger,
    WebhookService webhookService
) {

}
