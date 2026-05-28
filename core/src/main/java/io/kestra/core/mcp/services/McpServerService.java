package io.kestra.core.mcp.services;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Requires(bean = McpServerRepositoryInterface.class)
public class McpServerService {

    private final McpServerRepositoryInterface mcpServerRepository;

    @Inject
    public McpServerService(McpServerRepositoryInterface mcpServerRepository) {
        this.mcpServerRepository = mcpServerRepository;
    }

    public void createDefaultMcpServerIfNotExist(final String tenantId) {
        if (mcpServerRepository.exists(tenantId, McpServer.DEFAULT_ID)) {
            return;
        }

        McpServer defaultServer = new McpServer(
            tenantId,
            McpServer.DEFAULT_ID,
            "Default MCP server for this tenant. Exposes all MCP Tool triggers as tools.",
            "Expose Kestra flows as tools. Invoke a tool only when the user's request clearly " +
            "maps to executing one of the available flows, using the flow's inputs as the tool " +
            "parameters. Do not invent tools or capabilities beyond the provided flows. If no " +
            "suitable flow exists, state that the request cannot be fulfilled. Do not provide " +
            "explanations about Kestra unless explicitly asked.",
            McpServer.ServerType.PRIVATE,
            McpServer.AuthType.BASIC,
            false,
            false, false, null, null
        );

        mcpServerRepository.save(null, defaultServer);
    }
}
