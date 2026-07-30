package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Thrown by {@link ToolCatalog#dispatch} when a tool returned more than {@code maxToolResultChars}.
 * The result is deliberately not truncated: a cut-off result would leave the model reasoning on partial
 * data without knowing it. The orchestrator converts this into a failed tool result, so the model can
 * retry with a narrower request and the client can tell the user what happened.
 */
public class ToolResultTooLargeException extends KestraRuntimeException {
    private final String tool;
    private final int size;
    private final int limit;

    public ToolResultTooLargeException(final String tool, final int size, final int limit) {
        super(
            "The result of tool '%s' is too large to send to the model: %d characters, the maximum is %d. Narrow the request (add a filter, a smaller page size or a shorter range) and try again."
                .formatted(tool, size, limit)
        );
        this.tool = tool;
        this.size = size;
        this.limit = limit;
    }

    public String getTool() {
        return tool;
    }

    public int getSize() {
        return size;
    }

    public int getLimit() {
        return limit;
    }
}
