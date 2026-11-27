package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowService;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Authoring sub-agent revising a saved flow: reads its source, runs the same generation pipeline as
 * {@link AuthorFlowTool} on it, and publishes the result as a non-mutating {@link ArtefactDraft} of that same flow.
 */
@Singleton
@Requires(bean = AiServiceManager.class)
public class ReviseFlowTool implements AiAuthoringTool, McpExposedTool {
    private final AiServiceManager aiServiceManager;
    private final FlowService flowService;
    private final FlowRepositoryInterface flowRepository;

    @Inject
    public ReviseFlowTool(final AiServiceManager aiServiceManager, final FlowService flowService, final FlowRepositoryInterface flowRepository) {
        this.aiServiceManager = aiServiceManager;
        this.flowService = flowService;
        this.flowRepository = flowRepository;
    }

    @Override
    public ArtefactKind artefact() {
        return ArtefactKind.FLOW;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "revise_flow",
        value = "Draft a change to a flow that is already saved — including the flow of an execution being diagnosed — from a natural-language description of the change. The flow's saved source is read for you, and the draft keeps its id and namespace. Use this, never `author_flow`, whenever the flow exists. The draft is validated and returned; nothing is saved, so do not tell the user the flow has been updated or saved. If the draft comes back invalid, call this tool again passing the draft as `currentFlowYaml` with instructions to fix the reported constraints. "
            + "Returns an object { draftId, kind, valid, validationError, yaml, namespace, flowId }: `draftId` and `yaml` identify the draft, `namespace` and `flowId` the flow it revises, and `valid` reflects validation (`validationError` holds the constraints when invalid). If the flow is not found or generation fails, the tool errors with the reason instead of returning; do not fall back to `author_flow`."
    )
    public Result reviseFlow(
        @P(name = "instructions", value = "How the flow should be changed") String instructions,
        @P(name = "namespace", value = "The namespace of the flow to revise") String namespace,
        @P(name = "flowId", value = "The id of the flow to revise") String flowId,
        @P(name = "currentFlowYaml", value = "The YAML to revise instead of the saved source: unsaved editor content of this flow, or a previous draft of it to fix. Omit to revise the saved source", required = false) String currentFlowYaml,
        final AgentCallContext.Context context) {

        FlowWithSource saved = flowRepository.findByIdWithSource(context.tenant(), namespace, flowId, Optional.empty(), false)
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Flow not found: '%s.%s'. Pass the 'namespace' and 'flowId' of a saved flow.".formatted(namespace, flowId)
                )
            );
        String baseYaml = StringUtils.isBlank(currentFlowYaml) ? saved.getSource() : currentFlowYaml;

        ArtefactDraft draft = FlowDrafts.draft(aiServiceManager, flowService, instructions, saved.getNamespace(), baseYaml, context);
        requireSameFlow(saved, draft.yaml());

        return new Result(draft.draftId(), draft.kind(), draft.valid(), draft.constraints(), draft.yaml(), saved.getNamespace(), saved.getId());
    }

    /** The generation pipeline may discard the flow it was given and start from scratch, which is not a revision. */
    private static void requireSameFlow(final FlowWithSource saved, final String yaml) {
        Object draftedId;
        Object draftedNamespace;
        try {
            Map<?, ?> root = YamlParser.parse(yaml, Map.class);
            draftedId = root.get("id");
            draftedNamespace = root.get("namespace");
        } catch (RuntimeException e) {
            // Unparsable output has no identity to compare; validation reports it as an invalid draft.
            return;
        }
        if (!saved.getId().equals(draftedId) || !saved.getNamespace().equals(draftedNamespace)) {
            throw new IllegalStateException(
                "Flow generation failed: the draft was written as flow '%s.%s' instead of revising '%s.%s', so it was discarded. Call this tool again with instructions that only describe the change to make."
                    .formatted(draftedNamespace, draftedId, saved.getNamespace(), saved.getId())
            );
        }
    }

    /**
     * A published draft of a saved flow.
     *
     * @param validationError the validation constraints when invalid, else null
     * @param namespace the namespace of the flow the draft revises
     * @param flowId the id of the flow the draft revises
     */
    public record Result(String draftId, ArtefactKind kind, boolean valid, String validationError, String yaml, String namespace, String flowId)
        implements
            PublishableToolResult {
        @Override
        public ArtefactDraft artefact() {
            return new ArtefactDraft(draftId, kind, yaml, valid, validationError);
        }
    }
}
