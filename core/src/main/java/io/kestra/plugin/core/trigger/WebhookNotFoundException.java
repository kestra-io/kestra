package io.kestra.plugin.core.trigger;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.http.HttpResponse;

/**
 * Carries a webhook's custom 404 response so the error handler can return its
 * status, headers, and body instead of the generic not-found problem response.
 */
public class WebhookNotFoundException extends NotFoundException {
    private final HttpResponse<?> response;

    public WebhookNotFoundException(HttpResponse<?> response) {
        super("Webhook returned a custom not-found exception");
        this.response = response;
    }

    public HttpResponse<?> getResponse() {
        return response;
    }
}
