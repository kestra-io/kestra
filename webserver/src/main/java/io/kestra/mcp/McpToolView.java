package io.kestra.mcp;

import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * The tools of one MCP server as seen by a group of callers, resolved by a {@link McpToolSource}.
 * <p>
 * A view is kept alongside the server built from it and asked again for its tools whenever they change, from a
 * thread that carries no caller, so it must hold everything it needs to list them.
 */
public interface McpToolView {
    /**
     * Identifies every caller that sees the same tools on this server, so they share one built MCP server
     * instead of one being built per user.
     */
    String cacheKey();

    /**
     * @return the tools these callers currently see
     */
    List<McpServerFeatures.AsyncToolSpecification> tools();
}
