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
 * Calls a tool the way the agent loop and the MCP server do: through a {@link ToolCatalog}, with the JSON arguments a model sends.
 */
final class ToolCalls {
    private ToolCalls() {
    }

    static Map<String, Object> call(final AiPlatformTool tool, final String name, final String arguments, final String tenant) {
        DocsMcpToolProvider docs = mock(DocsMcpToolProvider.class);
        when(docs.tools()).thenReturn(Map.of());
        ToolCatalog catalog = new ToolCatalog(List.of(tool), List.of(), docs, (permission, t, principal) -> true, new DefaultAgentToolCallScope());
        ToolExecutionRequest request = ToolExecutionRequest.builder().id("call-1").name(name).arguments(arguments).build();

        String text = catalog.dispatch(request, AgentCallContext.Context.ofTenant(tenant)).text();
        try {
            return JacksonMapper.toMap(text);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Tool '%s' did not return a JSON object: %s".formatted(name, text), e);
        }
    }
}
