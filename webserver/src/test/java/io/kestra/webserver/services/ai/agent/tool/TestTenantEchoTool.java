package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import dev.langchain4j.agent.tool.Tool;

/**
 * Test-only tool (constructed manually, not a bean) that echoes the conversation's tenant — read from
 * the {@link AgentCallContext.Context}, never a tool argument — used to assert the catalog binds the
 * call context and the tool reads the tenant from it.
 */
public class TestTenantEchoTool implements AiPlatformTool {
    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Tool(name = "tenant-echo", value = "Test-only tool; echoes the tenant it runs against.")
    public String tenantEcho(final AgentCallContext.Context context) {
        return context.tenant();
    }
}
