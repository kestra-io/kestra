package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Thrown by {@link ToolCatalog#dispatch} when a tool returned more characters than the turn's
 * {@link ToolResultBudget} allows.
 */
public class ToolResultTooLargeException extends KestraRuntimeException {
    public ToolResultTooLargeException(final String tool, final int size, final int limit) {
        super(
            "The result of tool '%s' is too large to send to the model: %d characters, the maximum is %d. Narrow the request (add a filter, a smaller page size or a shorter range) and try again."
                .formatted(tool, size, limit)
        );
    }
}
