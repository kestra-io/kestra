package io.kestra.mcp;

import java.util.Optional;

import io.kestra.core.mcp.models.McpServer;

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

    Optional<McpServer> get(String tenantId, String serverId);
}
