package io.kestra.plugin.core.trigger;

import java.net.URI;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.WebhookService;

import lombok.Builder;

/**
 * The context of a webhook call.
 *
 * @param request        the incoming request
 * @param path           the optional additional path segments of the webhook URL
 * @param flow           the flow the webhook trigger belongs to
 * @param trigger        the webhook trigger the call matched
 * @param webhookService the service the trigger uses to create and start the execution
 * @param executionId    the identifier the execution created from this call will be given, minted before the
 *                       request is read so that files stored for the call live under the execution that will
 *                       carry them; {@code null} to let the execution mint its own
 * @param storedBodyUri  the URI the body of the request was stored under, for a trigger fetching it as
 *                       {@link AbstractWebhookTrigger.FetchType#STORE}; {@code null} for any other fetch type,
 *                       and for a request without a body
 */
@Builder
public record WebhookContext(
    HttpRequest request,
    String path,
    Flow flow,
    AbstractWebhookTrigger trigger,
    WebhookService webhookService,
    String executionId,
    URI storedBodyUri) {

}
