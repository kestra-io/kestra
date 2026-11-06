package io.kestra.mcp;

import java.util.Optional;

import io.micronaut.http.HttpRequest;

/**
 * Identifies the caller of an MCP server, whatever kind of tools it exposes.
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
}
