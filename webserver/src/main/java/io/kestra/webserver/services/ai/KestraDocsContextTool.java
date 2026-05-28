package io.kestra.webserver.services.ai;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

/**
 * A LangChain4j tool that provides the AI Copilot with access to Kestra documentation
 * and flow blueprints from the public kestra.io MCP server.
 */
@Slf4j
@Singleton
@Requires(property = "kestra.ai.enabled", value = "true", defaultValue = "true")
public class KestraDocsContextTool implements AutoCloseable {

    private static final Duration MCP_TIMEOUT = Duration.ofSeconds(15);

    private final McpClient mcpClient;

    @Inject
    public KestraDocsContextTool(
        @Value("${micronaut.http.services.api.url:https://api.kestra.io}") String apiUrl,
        VersionProvider versionProvider
    ) {
        McpClient client = null;
        try {
            client = new DefaultMcpClient.Builder()
                .transport(StreamableHttpMcpTransport.builder()
                    .url(apiUrl + "/v1/mcp")
                    .timeout(MCP_TIMEOUT)
                    .build())
                .clientName("Kestra/" + versionProvider.getVersion())
                .toolExecutionTimeout(MCP_TIMEOUT)
                .build();
        } catch (Exception e) {
            log.warn("Failed to initialize Kestra docs MCP client, documentation context will be unavailable: {}", e.getMessage());
        }
        this.mcpClient = client;
    }

    /**
     * Searches Kestra documentation for the given query.
     *
     * @param query the search query
     * @return matching documentation entries, or null if an error occurred
     */
    @Tool("Search the Kestra documentation for a given query. Use this to find information about Kestra features, tasks, triggers, and configuration options.")
    public String searchDocs(@P("The search query to find relevant Kestra documentation") String query) {
        return callMcpTool("search_docs", Map.of("query", query));
    }

    /**
     * Retrieves the content of a specific Kestra documentation page.
     *
     * @param parsedUrl the parsed URL of the documentation page
     * @return the documentation page content, or null if an error occurred
     */
    @Tool("Retrieve the content of a specific Kestra documentation page by its URL.")
    public String getDoc(@P("The parsed URL of the documentation page to retrieve") String parsedUrl) {
        return callMcpTool("get_doc", Map.of("parsedUrl", parsedUrl));
    }

    /**
     * Searches for Kestra flow blueprints matching the given query.
     *
     * @param query the search query
     * @return matching blueprints, or null if an error occurred
     */
    @Tool("Search for Kestra flow blueprints that match a given query. Use this to find example flows and templates.")
    public String searchBlueprints(@P("The search query to find relevant Kestra flow blueprints") String query) {
        return callMcpTool("blueprints", Map.of("query", query));
    }

    /**
     * Retrieves the YAML definition of a specific Kestra blueprint flow.
     *
     * @param blueprintId the ID of the blueprint to retrieve
     * @return the blueprint flow YAML, or null if an error occurred
     */
    @Tool("Retrieve the YAML flow definition of a specific Kestra blueprint by its ID.")
    public String getBlueprintFlow(@P("The numeric ID of the blueprint to retrieve") int blueprintId) {
        return callMcpTool("get_blueprint_flow", Map.of("blueprintId", blueprintId));
    }

    private String callMcpTool(String toolName, Map<String, Object> arguments) {
        if (mcpClient == null) {
            return null;
        }
        try {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(JacksonMapper.ofJson().writeValueAsString(arguments))
                .build();
            ToolExecutionResult result = mcpClient.executeTool(request);
            return result.resultText();
        } catch (Exception e) {
            log.warn("Failed to call MCP tool '{}': {}", toolName, e.getMessage());
            return null;
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (mcpClient == null) {
            return;
        }
        try {
            mcpClient.close();
        } catch (Exception e) {
            log.warn("Failed to close MCP client: {}", e.getMessage());
        }
    }
}
