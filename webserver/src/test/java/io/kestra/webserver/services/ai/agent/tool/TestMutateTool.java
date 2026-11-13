package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.events.Actor;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Singleton;

@Singleton
public class TestMutateTool implements AiPlatformTool {
    private volatile Actor lastActor;

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.MUTATE;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.CONFIRM;
    }

    @Tool(name = "update-artefact", value = "Test-only mutate tool; echoes its argument.")
    public String updateArtefact(
        @P(name = "executionId", value = "opaque test argument") final String executionId,
        final AgentCallContext.Context context) {
        lastActor = context.actor();
        return "updated: " + executionId;
    }

    public Actor lastActor() {
        return lastActor;
    }
}
