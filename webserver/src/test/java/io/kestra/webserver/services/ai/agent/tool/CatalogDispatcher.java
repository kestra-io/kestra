package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import io.kestra.core.serializers.JacksonMapper;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Calls a tool the way the agent loop and the MCP server do: through a {@link ToolCatalog}, with the model's JSON arguments.
 */
final class CatalogDispatcher {
    private CatalogDispatcher() {
    }

    static Map<String, Object> dispatch(final AiPlatformTool tool, final String name, final String arguments, final String tenant) {
        DocsMcpToolProvider docsMcpToolProvider = mock(DocsMcpToolProvider.class);
        when(docsMcpToolProvider.tools()).thenReturn(Map.of());
        ToolCatalog catalog = new ToolCatalog(List.of(tool), List.of(), docsMcpToolProvider, (permission, tenantId, principal) -> true, new DefaultAgentToolCallScope());
        ToolExecutionRequest request = ToolExecutionRequest.builder().id("call-1").name(name).arguments(arguments).build();
        String result = catalog.dispatch(request, AgentCallContext.Context.ofTenant(tenant)).text();
        try {
            return JacksonMapper.toMap(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The tool result is not a JSON object: " + result, e);
        }
    }
}
