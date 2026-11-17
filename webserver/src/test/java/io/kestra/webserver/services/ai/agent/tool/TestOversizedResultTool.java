package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.webserver.services.ai.agent.internals.ToolResultLimit;

import dev.langchain4j.agent.tool.Tool;

/** Test-only tool returning a result far larger than {@link ToolResultLimit#MAX_CHARS}. */
public class TestOversizedResultTool implements AiPlatformTool {
    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Tool(name = "oversized-result", value = "Test-only tool; returns a result larger than the tool result limit.")
    public String oversized() {
        return "x".repeat(ToolResultLimit.MAX_CHARS * 3);
    }
}
