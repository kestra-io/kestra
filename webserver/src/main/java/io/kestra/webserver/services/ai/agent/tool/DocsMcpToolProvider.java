package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;
import java.util.stream.Collectors;

import io.kestra.webserver.services.ai.agent.AgentConfiguration;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolExecutor;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DocsMcpToolProvider {
    private final String url;

    private volatile Map<ToolSpecification, ToolExecutor> tools;
    private volatile McpClient client;

    public DocsMcpToolProvider(final AgentConfiguration configuration) {
        this.url = configuration.docsMcpUrl();
    }

    /**
     * Lazily connect and list the remote docs tools, mapping each to an executor that calls back to
     * the same MCP client. Best-effort: on failure logs a warning and returns an empty map so the
     * loop degrades to no docs grounding rather than failing the turn.
     *
     * @return the remote tool specs mapped to their executors (empty if the server is unreachable)
     */
    public synchronized Map<ToolSpecification, ToolExecutor> tools() {
        if (tools != null) {
            return tools;
        }
        try {
            McpTransport transport = new StreamableHttpMcpTransport.Builder()
                .url(url)
                .logRequests(false)
                .logResponses(false)
                .build();
            this.client = new DefaultMcpClient.Builder()
                .transport(transport)
                .build();
            this.tools = client.listTools().stream()
                .collect(Collectors.toMap(spec -> spec, spec -> new McpToolExecutor(client)));
            log.info("Connected to Kestra docs MCP server at {} ({} tools)", url, tools.size());
        } catch (Exception e) {
            log.warn("Could not connect to Kestra docs MCP server at {}; Ask-mode docs grounding disabled. Reason: {}", url, e.getMessage());
            this.tools = Map.of();
        }
        return tools;
    }
}
