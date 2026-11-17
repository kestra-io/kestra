package io.kestra.webserver.services.ai.agent.tool;

/**
 * A platform tool that is also exposed to external MCP clients on the platform MCP server.
 */
public interface McpPlatformTool extends AiPlatformTool, McpExposedTool {
}
