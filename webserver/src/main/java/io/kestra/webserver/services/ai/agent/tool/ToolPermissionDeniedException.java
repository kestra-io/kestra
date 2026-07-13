package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Thrown by {@link ToolCatalog#dispatch} when the caller may not use a tool on the effective tenant.
 * The orchestrator converts it into a rejected tool result so the model can adapt instead of the
 * turn failing.
 */
public class ToolPermissionDeniedException extends KestraRuntimeException {
    public ToolPermissionDeniedException(final String tool, final String tenant) {
        super("Permission denied: tool '" + tool + "' cannot be used on tenant '" + tenant + "' by the caller.");
    }

    public ToolPermissionDeniedException(final String tool, final String tenant, final String namespace) {
        super("Permission denied: tool '" + tool + "' cannot be used on tenant '" + tenant + "' and namespace '" + namespace + "' by the caller.");
    }
}
