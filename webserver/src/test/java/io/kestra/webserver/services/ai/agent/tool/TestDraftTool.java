package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.ArtefactDraft;
import io.kestra.webserver.services.ai.agent.domain.ArtefactKind;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationContext;
import jakarta.inject.Singleton;

@Singleton
public class TestDraftTool implements AiAuthoringTool {
    @Override
    public ArtefactKind artefact() {
        return ArtefactKind.FLOW;
    }

    @Tool(name = "draft-artefact", value = "Test-only authoring tool; publishes a draft for its argument.")
    public String draftArtefact(@P(name = "executionId", value = "opaque test argument") final String executionId,
        final InvocationContext invocationContext) {
        AgentCallContext.from(invocationContext).publishDraft(
            new ArtefactDraft(
                "draft-" + executionId, ArtefactKind.FLOW, "id: " + executionId + "\n", true, null
            )
        );
        return "drafted: " + executionId;
    }
}
