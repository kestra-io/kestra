package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Singleton;

@Singleton
public class TestDraftTool implements AiAuthoringTool {
    @Override
    public ArtefactKind artefact() {
        return ArtefactKind.FLOW;
    }

    @Tool(name = "draft-artefact", value = "Test-only authoring tool; returns a publishable draft for its argument.")
    public Result draftArtefact(@P(name = "executionId", value = "opaque test argument") final String executionId,
        final AgentCallContext.Context context) {
        return new Result(new ArtefactDraft("draft-" + executionId, ArtefactKind.FLOW, "id: " + executionId + "\n", true, null));
    }

    /** A publishable draft — dispatch extracts {@link #artefact()} to publish it. */
    public record Result(ArtefactDraft artefact) implements PublishableToolResult {
    }
}
