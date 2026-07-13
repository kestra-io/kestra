package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Test-only tool (constructed manually, not a bean) following the multi-tenant pattern: a
 * {@link TenantId} parameter resolved via {@link AgentCallContext#resolveTenant} — used to assert the
 * catalog hides the parameter from the spec and binds the effective tenant.
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
    public String tenantEcho(
        @TenantId @P(name = "tenantId", value = "The tenant to run against; omit to use your current tenant", required = false) String tenantId) {
        return AgentCallContext.resolveTenant(tenantId);
    }
}
