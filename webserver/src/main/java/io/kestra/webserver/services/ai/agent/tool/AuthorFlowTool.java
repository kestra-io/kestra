package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.core.models.flows.FlowSource;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.UserInfo;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Authoring sub-agent for flows: runs the existing one-shot generation pipeline
 * (plugin finder → restricted schema → YAML builder) through {@link AiServiceInterface#generateFlow},
 * validates the result, and publishes it as a non-mutating {@link ArtefactDraft}. Nothing is saved —
 * the draft is surfaced to the user for review and apply.
 */
@Singleton
@Requires(bean = AiServiceManager.class)
public class AuthorFlowTool implements AiAuthoringTool {
    private final AiServiceManager aiServiceManager;
    private final FlowService flowService;

    @Inject
    public AuthorFlowTool(final AiServiceManager aiServiceManager, final FlowService flowService) {
        this.aiServiceManager = aiServiceManager;
        this.flowService = flowService;
    }

    @Override
    public ArtefactKind artefact() {
        return ArtefactKind.FLOW;
    }

    @Tool(
        name = "author-flow",
        value = "Draft a Kestra flow (YAML) from a natural-language description, or revise an existing flow when its current YAML is provided. The draft is validated and shown to the user as an artefact card; nothing is saved. If the draft comes back invalid, call this tool again passing the draft as `currentFlowYaml` with instructions to fix the reported constraints. Do not paste the full YAML in your reply — refer the user to the draft. "
            + "Returns an object { draftId, kind, valid, validationError, yaml }: `draftId` and `yaml` identify the published draft and `valid` reflects validation (`validationError` holds the constraints when invalid). If generation fails, the tool errors with the reason instead of returning."
    )
    public Result authorFlow(
        @P(name = "instructions", value = "What the flow should do, or how the current flow should be changed") String instructions,
        @P(name = "namespace", value = "The namespace the flow belongs to; omit if unknown", required = false) String namespace,
        @P(name = "currentFlowYaml", value = "The full current flow YAML when revising an existing flow; omit when creating a new one", required = false) String currentFlowYaml,
        final AgentCallContext.Context context) {

        AiServiceInterface aiService = aiServiceManager.getAiService(context.providerId());
        if (aiService == null) {
            throw new IllegalStateException("No AI provider is configured; flow authoring is unavailable.");
        }

        FlowGenerationPrompt prompt = new FlowGenerationPrompt(
            Objects.requireNonNullElseGet(context.conversationId(), IdUtils::create),
            instructions,
            currentFlowYaml,
            namespace
        );

        String yaml;
        try {
            yaml = aiService.generateFlow(new UserInfo(null, "copilot-agent"), prompt, context.tenant()).content();
        } catch (AiException e) {
            throw new IllegalStateException("Flow generation failed: " + e.getMessage(), e);
        }

        String constraints = validate(context.tenant(), yaml);
        ArtefactDraft draft = new ArtefactDraft(IdUtils.create(), ArtefactKind.FLOW, yaml, constraints == null, constraints);

        return Result.drafted(draft);
    }

    /** Run platform validation on the generated source; returns the violations, or null when valid. */
    private String validate(final String tenant, final String yaml) {
        List<ValidateConstraintViolation> violations = flowService.validate(tenant, List.of(new FlowSource(null, yaml)));
        String constraints = violations.stream()
            .map(ValidateConstraintViolation::getConstraints)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("; "));
        return constraints.isEmpty() ? null : constraints;
    }

    /**
     * A published artefact draft.
     *
     * @param draftId the id of the published draft
     * @param kind the kind of artefact that was drafted
     * @param valid whether the generated YAML passed validation
     * @param validationError the validation constraints when invalid, else null
     * @param yaml the generated YAML
     */
    public record Result(String draftId, ArtefactKind kind, boolean valid, String validationError, String yaml)
        implements
            PublishableToolResult {
        static Result drafted(final ArtefactDraft draft) {
            return new Result(draft.draftId(), draft.kind(), draft.valid(), draft.constraints(), draft.yaml());
        }

        @Override
        public ArtefactDraft artefact() {
            return new ArtefactDraft(draftId, kind, yaml, valid, validationError);
        }
    }
}
