package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Thrown by {@link ToolCatalog#dispatch} when the caller may not use a tool on the effective tenant.
 * The orchestrator converts it into a rejected tool result so the model can adapt instead of the
 * turn failing.
 */
public class ToolPermissionDeniedException extends KestraRuntimeException {
    public ToolPermissionDeniedException(final String tool, final String tenant) {
        super("Permission denied: tool '%s' cannot be used on tenant '%s' by the caller.".formatted(tool, tenant));
    }

    public ToolPermissionDeniedException(final String tool, final String tenant, final String namespace) {
        super("Permission denied: tool '%s' cannot be used on tenant '%s' and namespace '%s' by the caller.".formatted(tool, tenant, namespace));
    }
}
