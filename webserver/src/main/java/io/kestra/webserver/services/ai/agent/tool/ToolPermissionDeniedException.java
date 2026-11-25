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

    private ToolPermissionDeniedException(final String message) {
        super(message);
    }

    /** The caller lacks {@code permission} on {@code tenant}, whatever the arguments of the call. */
    public static ToolPermissionDeniedException missingPermission(final String permission, final String tenant) {
        return new ToolPermissionDeniedException(
            "Permission denied: the caller lacks the '%s' permission on tenant '%s', so the tool cannot be used.".formatted(permission, tenant)
        );
    }

    /** The caller lacks {@code permission} on the {@code namespace} the call's arguments target. */
    public static ToolPermissionDeniedException missingPermission(final String permission, final String tenant, final String namespace) {
        return new ToolPermissionDeniedException(
            "Permission denied: the caller lacks the '%s' permission on namespace '%s' of tenant '%s', so the tool cannot be used with these arguments."
                .formatted(permission, namespace, tenant)
        );
    }
}
