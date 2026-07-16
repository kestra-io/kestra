package io.kestra.webserver.services.ai.agent.tool;

import java.util.Objects;

import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.UserInfo;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;

/**
 * Authoring sub-agent for dashboards: runs the existing one-shot generation pipeline
 * (plugin finder → restricted schema → YAML builder) through {@link AiServiceInterface#generateDashboard},
 * validates the result, and publishes it as a non-mutating {@link ArtefactDraft}. Nothing is saved —
 * the draft is surfaced to the user for review and apply.
 */
@Singleton
@Requires(bean = AiServiceManager.class)
public class AuthorDashboardTool implements AiAuthoringTool {
    private final AiServiceManager aiServiceManager;
    private final ModelValidator modelValidator;

    @Inject
    public AuthorDashboardTool(final AiServiceManager aiServiceManager, final ModelValidator modelValidator) {
        this.aiServiceManager = aiServiceManager;
        this.modelValidator = modelValidator;
    }

    @Override
    public ArtefactKind artefact() {
        return ArtefactKind.DASHBOARD;
    }

    @Tool(
        name = "author-dashboard",
        value = "Draft a Kestra custom dashboard (YAML) from a natural-language description, or revise an existing dashboard when its current YAML is provided. The draft is validated and shown to the user as an artefact card; nothing is saved. If the draft comes back invalid, call this tool again passing the draft as `currentDashboardYaml` with instructions to fix the reported constraints. Do not paste the full YAML in your reply — refer the user to the draft. "
            + "Returns an object { draftId, kind, valid, validationError, yaml }: `draftId` and `yaml` identify the published draft and `valid` reflects validation (`validationError` holds the constraints when invalid). If generation fails, the tool errors with the reason instead of returning."
    )
    public Result authorDashboard(
        @P(name = "instructions", value = "What the dashboard should display, or how the current dashboard should be changed") String instructions,
        @P(
            name = "currentDashboardYaml", value = "The full current dashboard YAML when revising an existing dashboard; omit when creating a new one", required = false
        ) String currentDashboardYaml,
        final AgentCallContext.Context context) {

        AiServiceInterface aiService = aiServiceManager.getAiService(context.providerId());
        if (aiService == null) {
            throw new IllegalStateException("No AI provider is configured; dashboard authoring is unavailable.");
        }

        DashboardGenerationPrompt prompt = new DashboardGenerationPrompt(
            Objects.requireNonNullElseGet(context.conversationId(), IdUtils::create),
            instructions,
            currentDashboardYaml
        );

        String yaml;
        try {
            yaml = aiService.generateDashboard(new UserInfo(null, "copilot-agent"), prompt).content();
        } catch (AiException e) {
            throw new IllegalStateException("Dashboard generation failed: " + e.getMessage(), e);
        }

        String constraints = validate(yaml);
        ArtefactDraft draft = new ArtefactDraft(IdUtils.create(), ArtefactKind.DASHBOARD, yaml, constraints == null, constraints);

        return Result.drafted(draft);
    }

    /** Parse and validate the generated source, as the dashboard validate endpoint does; null when valid. */
    private String validate(final String yaml) {
        try {
            Dashboard parsed = YamlParser.parse(yaml, Dashboard.class).toBuilder().deleted(false).build();
            modelValidator.validate(parsed);
            return null;
        } catch (ConstraintViolationException e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return "Unable to validate the dashboard: " + e.getMessage();
        }
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
