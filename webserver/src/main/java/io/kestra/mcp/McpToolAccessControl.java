package io.kestra.mcp;

import java.util.Optional;

import io.kestra.core.models.AccessScope;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;

/**
 * Decides which flows an MCP server exposes to a caller as tools, and which of them that caller may invoke.
 * <p>
 * The caller is resolved once per request and carried on {@link KestraMcpTransportContext} rather than read
 * from the request context, because tool handlers run on a reactor thread where the request's
 * authentication is no longer in scope.
 */
public interface McpToolAccessControl {
    /**
     * @return an identifier for the caller behind this request, empty when it is anonymous
     */
    Optional<String> callerId(HttpRequest<?> request);

    /**
     * @param callerId the caller, or {@code null} when anonymous
     * @param tenantId the tenant the MCP server belongs to
     * @param serverId the MCP server being connected to
     * @return the namespaces whose flows this caller may execute through that server
     */
    AccessScope executableScope(@Nullable String callerId, String tenantId, String serverId);
}
