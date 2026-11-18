package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.mcp.models.McpToolAnnotations;

/**
 * A platform tool that may also be exposed to external MCP clients on the platform MCP server. Only tools
 * implementing this interface are exposed there, so nothing reaches those clients without opting in.
 */
public interface McpPlatformTool extends AiPlatformTool {
    /**
     * @return the hints MCP clients use to decide whether a call needs the user's approval
     */
    McpToolAnnotations annotations();
}
