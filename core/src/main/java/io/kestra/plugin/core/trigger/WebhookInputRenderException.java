package io.kestra.plugin.core.trigger;

import java.io.Serial;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Thrown when a webhook trigger's inputs cannot be rendered or processed.
 *
 * <p>This is intentionally distinct from a webhook whose conditions are not met: the latter is a
 * normal outcome (no execution is created and the caller receives a {@code 204}), whereas a failure
 * to render the inputs is a genuine error that must not be silently swallowed into a {@code 204}.
 */
public class WebhookInputRenderException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public WebhookInputRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
