package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.domain.ToolFamily;
import io.kestra.webserver.services.ai.agent.domain.WritePolicy;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Singleton;

@Singleton
public class TestMutateTool implements AiPlatformTool {
    @Override
    public ToolFamily family() {
        return ToolFamily.MUTATE;
    }

    @Override
    public WritePolicy writePolicy() {
        return WritePolicy.CONFIRM;
    }

    @Override
    public String permission() {
        return "test:mutate";
    }

    @Tool(name = "update-artefact", value = "Test-only mutate tool; echoes its argument.")
    public String updateArtefact(@P(name = "executionId", value = "opaque test argument") final String executionId) {
        return "updated: " + executionId;
    }
}
