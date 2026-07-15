package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.GenerationResult;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.ArtefactDraft;
import io.kestra.webserver.services.ai.agent.domain.ArtefactKind;

import dev.langchain4j.invocation.InvocationContext;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration check for {@code author-flow}: the tool, the real {@link io.kestra.core.services.FlowService}
 * validation, and draft publishing are exercised end to end. Only the AI provider (which drives an LLM)
 * is mocked, so generated YAML is deterministic while validation runs for real.
 */
@KestraTest(environments = "memory")
class AuthorFlowToolTest {
    private static final String TENANT = MAIN_TENANT;
    private static final String VALID_YAML = "id: hello\nnamespace: company.team\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: hi\n";
    private static final String INVALID_YAML = "id: broken\nnamespace: company.team\n";

    @Inject
    private AuthorFlowTool tool;

    @Inject
    private AiServiceManager aiServiceManager;

    private AiServiceInterface aiService;
    private List<ArtefactDraft> published;
    private InvocationContext params;

    @MockBean(AiServiceManager.class)
    AiServiceManager aiServiceManager() {
        return mock(AiServiceManager.class);
    }

    @BeforeEach
    void setUp() {
        aiService = mock(AiServiceInterface.class);
        published = new ArrayList<>();
        params = AgentCallContext.into(new AgentCallContext.Context(TENANT, null, "provider-1", "thread-1", published::add));
        when(aiServiceManager.getAiService("provider-1")).thenReturn(aiService);
    }

    @Test
    void shouldExposeAuthoringMetadata() {
        assertThat(tool.artefact()).isEqualTo(ArtefactKind.FLOW);
    }

    @Test
    void shouldPublishValidDraftWhenGenerationSucceeds() {
        // Given — the model returns a well-formed flow
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(VALID_YAML));

        // When
        AuthorFlowTool.Result result = tool.authorFlow("log hello", "company.team", null, params);

        // Then — the draft carries the generated YAML and passed real validation
        assertThat(published).hasSize(1);
        ArtefactDraft draft = published.getFirst();
        assertThat(draft.kind()).isEqualTo(ArtefactKind.FLOW);
        assertThat(draft.yaml()).isEqualTo(VALID_YAML);
        assertThat(draft.valid()).isTrue();
        assertThat(draft.constraints()).isNull();
        assertThat(result.draftId()).isEqualTo(draft.draftId());
        assertThat(result.kind()).isEqualTo(ArtefactKind.FLOW);
        assertThat(result.valid()).isTrue();
        assertThat(result.validationError()).isNull();
        assertThat(result.yaml()).isEqualTo(VALID_YAML);
    }

    @Test
    void shouldPassThreadScopedPromptToGenerationPipeline() {
        // Given
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(VALID_YAML));
        ArgumentCaptor<FlowGenerationPrompt> prompt = ArgumentCaptor.forClass(FlowGenerationPrompt.class);

        // When
        tool.authorFlow("add a retry", "company.team", "id: existing\n", params);

        // Then — the one-shot pipeline is reused verbatim with the thread as the conversation id
        verify(aiService).generateFlow(any(), prompt.capture(), eq(TENANT));
        assertThat(prompt.getValue().getConversationId()).isEqualTo("thread-1");
        assertThat(prompt.getValue().getUserPrompt()).isEqualTo("add a retry");
        assertThat(prompt.getValue().getNamespace()).isEqualTo("company.team");
        assertThat(prompt.getValue().getYaml()).isEqualTo("id: existing\n");
    }

    @Test
    void shouldPublishInvalidDraftWithConstraintsWhenValidationFails() {
        // Given — the model returns a flow that fails real validation (no tasks)
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(INVALID_YAML));

        // When
        AuthorFlowTool.Result result = tool.authorFlow("log hello", null, null, params);

        // Then — the draft is still published (the user sees it) but flagged invalid for the model to fix
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().valid()).isFalse();
        assertThat(published.getFirst().constraints()).isNotBlank();
        assertThat(result.valid()).isFalse();
        assertThat(result.validationError()).isNotBlank();
    }

    @Test
    void shouldThrowWithoutDraftWhenGenerationFails() {
        // Given
        when(aiService.generateFlow(any(), any(), anyString())).thenThrow(new AiException("I cannot generate this flow"));

        // When / Then — no draft reaches the user; the tool errors so the orchestrator feeds the
        // message back to the model to react to
        assertThatThrownBy(() -> tool.authorFlow("do something impossible", null, null, params))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Flow generation failed: I cannot generate this flow");
        assertThat(published).isEmpty();
    }

    @Test
    void shouldFailWhenNoAiProviderConfigured() {
        // Given
        when(aiServiceManager.getAiService("provider-1")).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> tool.authorFlow("log hello", null, null, params))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No AI provider");
        assertThat(published).isEmpty();
    }
}
