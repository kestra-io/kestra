package io.kestra.mcp;

import java.util.Optional;
import java.util.Set;

import io.kestra.core.mcp.models.McpServer;

import io.micronaut.core.annotation.Nullable;

/**
 * Defines MCP servers from configuration rather than the repository, so users can neither list, edit nor
 * delete them.
 * <p>
 * {@link McpServerCache} asks providers before stored servers, so every lookup of a server, authentication
 * included, sees a provided server and a stored one cannot take over its id. An implementation must not depend
 * on {@link McpServerCache}.
 */
public interface McpServerProvider {
    /**
     * @return the kind of tool source that serves the servers this provider defines
     */
    McpToolSource.Kind toolSource();

    Optional<McpServer> get(@Nullable String tenantId, String serverId);

    /**
     * @return the authentication methods a caller may use on a server this provider defines
     */
    default Set<McpServer.AuthType> authTypes(McpServer server) {
        return Set.of(server.authType());
    }
}
