package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.models.validations.ModelValidator;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.GenerationResult;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.ArtefactDraft;
import io.kestra.webserver.services.ai.agent.domain.ArtefactKind;

import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorDashboardToolTest {
    private static final String TENANT = "unit";
    private static final String YAML = "title: Executions overview\n";

    private AiServiceManager aiServiceManager;
    private AiServiceInterface aiService;
    private ModelValidator modelValidator;
    private AuthorDashboardTool tool;
    private List<ArtefactDraft> published;

    @BeforeEach
    void setUp() {
        aiServiceManager = mock(AiServiceManager.class);
        aiService = mock(AiServiceInterface.class);
        modelValidator = mock(ModelValidator.class);
        tool = new AuthorDashboardTool(aiServiceManager, modelValidator);
        published = new ArrayList<>();
        AgentCallContext.set(new AgentCallContext.Context(TENANT, null, "provider-1", "thread-1", published::add));
        when(aiServiceManager.getAiService("provider-1")).thenReturn(aiService);
    }

    @AfterEach
    void tearDown() {
        AgentCallContext.clear();
    }

    @Test
    void shouldExposeAuthoringMetadata() {
        // When / Then — an authoring tool: drafts only, no family, no write policy
        assertThat(tool.artefact()).isEqualTo(ArtefactKind.DASHBOARD);
    }

    @Test
    void shouldPublishValidDraftWhenGenerationSucceeds() {
        // Given
        when(aiService.generateDashboard(any(), any())).thenReturn(GenerationResult.of(YAML));

        // When
        AuthorDashboardTool.Result result = tool.authorDashboard("show executions per namespace", null);

        // Then — the draft carries the generated YAML and passed validation
        assertThat(published).hasSize(1);
        ArtefactDraft draft = published.getFirst();
        assertThat(draft.kind()).isEqualTo(ArtefactKind.DASHBOARD);
        assertThat(draft.yaml()).isEqualTo(YAML);
        assertThat(draft.valid()).isTrue();
        assertThat(draft.constraints()).isNull();
        assertThat(result.draftId()).isEqualTo(draft.draftId());
        assertThat(result.kind()).isEqualTo(ArtefactKind.DASHBOARD);
        assertThat(result.valid()).isTrue();
        assertThat(result.validationError()).isNull();
        assertThat(result.yaml()).isEqualTo(YAML);
    }

    @Test
    void shouldPassThreadScopedPromptToGenerationPipeline() {
        // Given
        when(aiService.generateDashboard(any(), any())).thenReturn(GenerationResult.of(YAML));
        ArgumentCaptor<DashboardGenerationPrompt> prompt = ArgumentCaptor.forClass(DashboardGenerationPrompt.class);

        // When
        tool.authorDashboard("add a failure chart", "title: Existing\n");

        // Then — the one-shot pipeline is reused verbatim with the thread as the conversation id
        verify(aiService).generateDashboard(any(), prompt.capture());
        assertThat(prompt.getValue().getConversationId()).isEqualTo("thread-1");
        assertThat(prompt.getValue().getUserPrompt()).isEqualTo("add a failure chart");
        assertThat(prompt.getValue().getYaml()).isEqualTo("title: Existing\n");
    }

    @Test
    void shouldPublishInvalidDraftWithConstraintsWhenValidationFails() {
        // Given
        when(aiService.generateDashboard(any(), any())).thenReturn(GenerationResult.of(YAML));
        doThrow(new ConstraintViolationException("title: must not be blank", Set.of())).when(modelValidator).validate(any());

        // When
        AuthorDashboardTool.Result result = tool.authorDashboard("show executions", null);

        // Then — the draft is still published (the user sees it) but flagged invalid for the model to fix
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().valid()).isFalse();
        assertThat(published.getFirst().constraints()).isEqualTo("title: must not be blank");
        assertThat(result.valid()).isFalse();
        assertThat(result.validationError()).isEqualTo("title: must not be blank");
    }

    @Test
    void shouldPublishInvalidDraftWhenGeneratedYamlDoesNotParse() {
        // Given — the generated source is not a parseable dashboard
        when(aiService.generateDashboard(any(), any())).thenReturn(GenerationResult.of("title: [unclosed\n"));

        // When
        AuthorDashboardTool.Result result = tool.authorDashboard("show executions", null);

        // Then — the parse error surfaces as a constraint violation on the draft
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().valid()).isFalse();
        assertThat(published.getFirst().constraints()).contains("Illegal dashboard source");
        assertThat(result.valid()).isFalse();
        assertThat(result.validationError()).contains("Illegal dashboard source");
    }

    @Test
    void shouldThrowWithoutDraftWhenGenerationFails() {
        // Given
        when(aiService.generateDashboard(any(), any())).thenThrow(new AiException("I cannot generate this dashboard"));

        // When / Then — no draft reaches the user; the tool errors so the orchestrator feeds the
        // message back to the model to react to
        assertThatThrownBy(() -> tool.authorDashboard("do something impossible", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Dashboard generation failed: I cannot generate this dashboard");
        assertThat(published).isEmpty();
    }
}
