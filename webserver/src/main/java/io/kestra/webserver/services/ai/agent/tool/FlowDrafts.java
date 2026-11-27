package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.core.models.flows.FlowSource;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.models.validations.ValidationError;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.UserInfo;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

/**
 * Drafts flows for the flow authoring tools: runs the one-shot generation pipeline and validates its output.
 */
final class FlowDrafts {
    private FlowDrafts() {
    }

    /**
     * @param namespace never null: the generation prompt template rejects a null namespace
     * @param baseYaml the flow to revise, or null to draft a new one
     */
    static ArtefactDraft draft(final AiServiceManager aiServiceManager, final FlowService flowService, final String instructions,
        final String namespace, final String baseYaml, final AgentCallContext.Context context) {
        AiServiceInterface aiService = aiServiceManager.getAiService(context.providerId());
        if (aiService == null) {
            throw new IllegalStateException("No AI provider is configured; flow authoring is unavailable.");
        }

        FlowGenerationPrompt prompt = new FlowGenerationPrompt(
            Objects.requireNonNullElseGet(context.conversationId(), IdUtils::create),
            instructions,
            baseYaml,
            namespace
        );

        String yaml;
        try {
            yaml = aiService.generateFlow(new UserInfo(null, "copilot-agent"), prompt, context.tenant()).content();
        } catch (AiException e) {
            throw new IllegalStateException("Flow generation failed: " + e.getMessage(), e);
        }

        String constraints = validate(flowService, context.tenant(), yaml);
        return new ArtefactDraft(IdUtils.create(), ArtefactKind.FLOW, yaml, constraints == null, constraints);
    }

    private static String validate(final FlowService flowService, final String tenant, final String yaml) {
        List<ValidateConstraintViolation> violations = flowService.validate(tenant, List.of(new FlowSource(null, yaml)));
        String constraints = violations.stream()
            .flatMap(violation -> violation.getErrors().stream())
            .map(ValidationError::toLine)
            .collect(Collectors.joining("; "));
        return constraints.isEmpty() ? null : constraints;
    }
}
