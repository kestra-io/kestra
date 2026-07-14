package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.services.FlowService;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.GenerationResult;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.ArtefactDraft;
import io.kestra.webserver.services.ai.agent.domain.ArtefactKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorFlowToolTest {
    private static final String TENANT = "unit";
    private static final String YAML = "id: hello\nnamespace: company.team\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: hi\n";

    private AiServiceManager aiServiceManager;
    private AiServiceInterface aiService;
    private FlowService flowService;
    private AuthorFlowTool tool;
    private List<ArtefactDraft> published;

    @BeforeEach
    void setUp() {
        aiServiceManager = mock(AiServiceManager.class);
        aiService = mock(AiServiceInterface.class);
        flowService = mock(FlowService.class);
        tool = new AuthorFlowTool(aiServiceManager, flowService);
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
        assertThat(tool.artefact()).isEqualTo(ArtefactKind.FLOW);
    }

    @Test
    void shouldPublishValidDraftWhenGenerationSucceeds() {
        // Given
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(YAML));
        when(flowService.validate(eq(TENANT), any())).thenReturn(List.of(ValidateConstraintViolation.builder().index(0).build()));

        // When
        AuthorFlowTool.Result result = tool.authorFlow("log hello", "company.team", null);

        // Then — the draft carries the generated YAML and passed validation
        assertThat(published).hasSize(1);
        ArtefactDraft draft = published.getFirst();
        assertThat(draft.kind()).isEqualTo(ArtefactKind.FLOW);
        assertThat(draft.yaml()).isEqualTo(YAML);
        assertThat(draft.valid()).isTrue();
        assertThat(draft.constraints()).isNull();
        assertThat(result.draftId()).isEqualTo(draft.draftId());
        assertThat(result.kind()).isEqualTo(ArtefactKind.FLOW);
        assertThat(result.valid()).isTrue();
        assertThat(result.validationError()).isNull();
        assertThat(result.yaml()).isEqualTo(YAML);
    }

    @Test
    void shouldPassThreadScopedPromptToGenerationPipeline() {
        // Given
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(YAML));
        when(flowService.validate(eq(TENANT), any())).thenReturn(List.of(ValidateConstraintViolation.builder().index(0).build()));
        ArgumentCaptor<FlowGenerationPrompt> prompt = ArgumentCaptor.forClass(FlowGenerationPrompt.class);

        // When
        tool.authorFlow("add a retry", "company.team", "id: existing\n");

        // Then — the one-shot pipeline is reused verbatim with the thread as the conversation id
        org.mockito.Mockito.verify(aiService).generateFlow(any(), prompt.capture(), eq(TENANT));
        assertThat(prompt.getValue().getConversationId()).isEqualTo("thread-1");
        assertThat(prompt.getValue().getUserPrompt()).isEqualTo("add a retry");
        assertThat(prompt.getValue().getNamespace()).isEqualTo("company.team");
        assertThat(prompt.getValue().getYaml()).isEqualTo("id: existing\n");
    }

    @Test
    void shouldPublishInvalidDraftWithConstraintsWhenValidationFails() {
        // Given
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(YAML));
        when(flowService.validate(eq(TENANT), any())).thenReturn(
            List.of(
                ValidateConstraintViolation.builder().index(0).constraints("tasks: must not be empty").build()
            )
        );

        // When
        AuthorFlowTool.Result result = tool.authorFlow("log hello", null, null);

        // Then — the draft is still published (the user sees it) but flagged invalid for the model to fix
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().valid()).isFalse();
        assertThat(published.getFirst().constraints()).isEqualTo("tasks: must not be empty");
        assertThat(result.valid()).isFalse();
        assertThat(result.validationError()).isEqualTo("tasks: must not be empty");
    }

    @Test
    void shouldThrowWithoutDraftWhenGenerationFails() {
        // Given
        when(aiService.generateFlow(any(), any(), anyString())).thenThrow(new AiException("I cannot generate this flow"));

        // When / Then — no draft reaches the user; the tool errors so the orchestrator feeds the
        // message back to the model to react to
        assertThatThrownBy(() -> tool.authorFlow("do something impossible", null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Flow generation failed: I cannot generate this flow");
        assertThat(published).isEmpty();
    }

    @Test
    void shouldFailWhenNoAiProviderConfigured() {
        // Given
        when(aiServiceManager.getAiService("provider-1")).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> tool.authorFlow("log hello", null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No AI provider");
        assertThat(published).isEmpty();
    }
}
