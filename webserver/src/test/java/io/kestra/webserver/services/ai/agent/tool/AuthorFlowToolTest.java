package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.GenerationResult;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

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
 * Integration check for {@code author-flow}: the tool and the real
 * {@link io.kestra.core.services.FlowService} validation are exercised end to end, and the tool's
 * returned publishable draft is asserted. Only the AI provider (which drives an LLM) is mocked, so
 * generated YAML is deterministic while validation runs for real.
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
    private AgentCallContext.Context params;

    @MockBean(AiServiceManager.class)
    AiServiceManager aiServiceManager() {
        return mock(AiServiceManager.class);
    }

    @BeforeEach
    void setUp() {
        aiService = mock(AiServiceInterface.class);
        params = new AgentCallContext.Context(TENANT, null, "provider-1", "thread-1");
        when(aiServiceManager.getAiService("provider-1")).thenReturn(aiService);
    }

    @Test
    void shouldExposeAuthoringMetadata() {
        assertThat(tool.artefact()).isEqualTo(ArtefactKind.FLOW);
    }

    @Test
    void shouldReturnValidDraftWhenGenerationSucceeds() {
        // Given — the model returns a well-formed flow
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(VALID_YAML));

        // When
        AuthorFlowTool.Result result = tool.authorFlow("log hello", "company.team", null, params);

        // Then — the result carries the generated YAML and passed real validation
        assertThat(result.kind()).isEqualTo(ArtefactKind.FLOW);
        assertThat(result.valid()).isTrue();
        assertThat(result.validationError()).isNull();
        assertThat(result.yaml()).isEqualTo(VALID_YAML);
        // and its publishable artefact mirrors it — what the orchestrator persists and streams
        ArtefactDraft draft = result.artefact();
        assertThat(draft.draftId()).isEqualTo(result.draftId());
        assertThat(draft.kind()).isEqualTo(ArtefactKind.FLOW);
        assertThat(draft.yaml()).isEqualTo(VALID_YAML);
        assertThat(draft.valid()).isTrue();
        assertThat(draft.constraints()).isNull();
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
    void shouldDefaultNamespaceAndReportAssumptionWhenCreatingWithoutNamespace() {
        // Given — the model returns a well-formed flow; the agent authors WITHOUT a namespace
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(VALID_YAML));
        ArgumentCaptor<FlowGenerationPrompt> prompt = ArgumentCaptor.forClass(FlowGenerationPrompt.class);

        // When — no namespace, no current YAML (a fresh create) — the case that used to hard-fail and loop
        AuthorFlowTool.Result result = tool.authorFlow("log hello", null, null, params);

        // Then — the pipeline is handed the default namespace instead of null (which the template rejects)
        verify(aiService).generateFlow(any(), prompt.capture(), eq(TENANT));
        assertThat(prompt.getValue().getNamespace()).isEqualTo(AuthorFlowTool.DEFAULT_NAMESPACE);
        // and the assumption is reported back so the agent can tell the user which namespace was used
        assertThat(result.assumedNamespace()).isEqualTo(AuthorFlowTool.DEFAULT_NAMESPACE);
    }

    @Test
    void shouldDefaultBlankNamespaceButNotReportAssumptionWhenRevising() {
        // Given — a revision (current YAML provided) with a blank namespace: the real namespace lives in
        // the YAML and is preserved, so the default is only a placeholder and must NOT be surfaced.
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(VALID_YAML));
        ArgumentCaptor<FlowGenerationPrompt> prompt = ArgumentCaptor.forClass(FlowGenerationPrompt.class);

        // When
        AuthorFlowTool.Result result = tool.authorFlow("add a retry", "  ", "id: existing\nnamespace: prod.team\n", params);

        // Then — still non-null for the template, but no assumption is reported to the user
        verify(aiService).generateFlow(any(), prompt.capture(), eq(TENANT));
        assertThat(prompt.getValue().getNamespace()).isEqualTo(AuthorFlowTool.DEFAULT_NAMESPACE);
        assertThat(result.assumedNamespace()).isNull();
    }

    @Test
    void shouldNotReportAssumptionWhenNamespaceProvided() {
        // Given
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(VALID_YAML));

        // When — the caller supplies a namespace
        AuthorFlowTool.Result result = tool.authorFlow("log hello", "company.team", null, params);

        // Then — nothing was assumed
        assertThat(result.assumedNamespace()).isNull();
    }

    @Test
    void shouldReturnInvalidDraftWithConstraintsWhenValidationFails() {
        // Given — the model returns a flow that fails real validation (no tasks)
        when(aiService.generateFlow(any(), any(), eq(TENANT))).thenReturn(GenerationResult.of(INVALID_YAML));

        // When
        AuthorFlowTool.Result result = tool.authorFlow("log hello", null, null, params);

        // Then — the draft is still returned (the user sees it) but flagged invalid for the model to fix
        assertThat(result.valid()).isFalse();
        assertThat(result.validationError()).isNotBlank();
        assertThat(result.artefact().valid()).isFalse();
        assertThat(result.artefact().constraints()).isNotBlank();
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
    }

    @Test
    void shouldFailWhenNoAiProviderConfigured() {
        // Given
        when(aiServiceManager.getAiService("provider-1")).thenReturn(null);

        // When / Then
        assertThatThrownBy(() -> tool.authorFlow("log hello", null, null, params))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No AI provider");
    }
}
