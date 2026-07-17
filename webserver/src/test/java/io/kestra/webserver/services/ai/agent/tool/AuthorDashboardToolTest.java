package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration check for {@code author-dashboard}: the tool, the real dashboard YAML parsing and
 * {@link io.kestra.core.models.validations.ModelValidator} validation are exercised end to end, and
 * the tool's returned publishable draft is asserted. Only the AI provider (which drives an LLM) is
 * mocked, so generated YAML is deterministic while validation runs for real.
 */
@KestraTest(environments = "memory")
class AuthorDashboardToolTest {
    private static final String TENANT = MAIN_TENANT;
    private static final String VALID_YAML = """
        id: full
        title: Some Dashboard
        description: Default overview dashboard
        timeWindow:
          default: P30D
          max: P365D
        charts:
          - id: logs_timeseries
            type: io.kestra.plugin.core.dashboard.chart.TimeSeries
            chartOptions:
              displayName: Error Logs
              description: Count of ERROR logs per date
              legend:
                enabled: true
              column: date
              colorByColumn: level
            data:
              type: io.kestra.plugin.core.dashboard.data.Logs
              columns:
                date:
                  field: DATE
                  displayName: Execution Date
                level:
                  field: LEVEL
                total:
                  displayName: Total Error Logs
                  agg: COUNT
                  graphStyle: BARS
              where:
                - field: LEVEL
                  type: IN
                  values:
                    - ERROR
        """;

    @Inject
    private AuthorDashboardTool tool;

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
        assertThat(tool.artefact()).isEqualTo(ArtefactKind.DASHBOARD);
    }

    @Test
    void shouldReturnValidDraftWhenGenerationSucceeds() {
        // Given — the model returns a well-formed dashboard
        when(aiService.generateDashboard(any(), any())).thenReturn(GenerationResult.of(VALID_YAML));

        // When
        AuthorDashboardTool.Result result = tool.authorDashboard("show executions per namespace", null, params);

        // Then — the result carries the generated YAML and passed real validation
        assertThat(result.kind()).isEqualTo(ArtefactKind.DASHBOARD);
        assertThat(result.valid()).isTrue();
        assertThat(result.validationError()).isNull();
        assertThat(result.yaml()).isEqualTo(VALID_YAML);
        // and its publishable artefact mirrors it — what the orchestrator persists and streams
        ArtefactDraft draft = result.artefact();
        assertThat(draft.draftId()).isEqualTo(result.draftId());
        assertThat(draft.kind()).isEqualTo(ArtefactKind.DASHBOARD);
        assertThat(draft.yaml()).isEqualTo(VALID_YAML);
        assertThat(draft.valid()).isTrue();
        assertThat(draft.constraints()).isNull();
    }

    @Test
    void shouldPassThreadScopedPromptToGenerationPipeline() {
        // Given
        when(aiService.generateDashboard(any(), any())).thenReturn(GenerationResult.of(VALID_YAML));
        ArgumentCaptor<DashboardGenerationPrompt> prompt = ArgumentCaptor.forClass(DashboardGenerationPrompt.class);

        // When
        tool.authorDashboard("add a failure chart", "title: Existing\n", params);

        // Then — the one-shot pipeline is reused verbatim with the thread as the conversation id
        verify(aiService).generateDashboard(any(), prompt.capture());
        assertThat(prompt.getValue().getConversationId()).isEqualTo("thread-1");
        assertThat(prompt.getValue().getUserPrompt()).isEqualTo("add a failure chart");
        assertThat(prompt.getValue().getYaml()).isEqualTo("title: Existing\n");
    }

    @Test
    void shouldReturnInvalidDraftWithConstraintsWhenValidationFails() {
        // Given — a parseable dashboard that fails real validation (missing the mandatory id)
        when(aiService.generateDashboard(any(), any())).thenReturn(GenerationResult.of("title: Only a title\n"));

        // When
        AuthorDashboardTool.Result result = tool.authorDashboard("show executions", null, params);

        // Then — the draft is still returned (the user sees it) but flagged invalid for the model to fix
        assertThat(result.valid()).isFalse();
        assertThat(result.validationError()).isNotBlank();
        assertThat(result.artefact().valid()).isFalse();
        assertThat(result.artefact().constraints()).isNotBlank();
    }

    @Test
    void shouldReturnInvalidDraftWhenGeneratedYamlDoesNotParse() {
        // Given — the generated source is not a parseable dashboard
        when(aiService.generateDashboard(any(), any())).thenReturn(GenerationResult.of("title: [unclosed\n"));

        // When
        AuthorDashboardTool.Result result = tool.authorDashboard("show executions", null, params);

        // Then — the parse error surfaces as a constraint violation on the draft
        assertThat(result.valid()).isFalse();
        assertThat(result.validationError()).isNotBlank();
        assertThat(result.artefact().valid()).isFalse();
        assertThat(result.artefact().constraints()).isNotBlank();
    }

    @Test
    void shouldThrowWithoutDraftWhenGenerationFails() {
        // Given
        when(aiService.generateDashboard(any(), any())).thenThrow(new AiException("I cannot generate this dashboard"));

        // When / Then — no draft reaches the user; the tool errors so the orchestrator feeds the
        // message back to the model to react to
        assertThatThrownBy(() -> tool.authorDashboard("do something impossible", null, params))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Dashboard generation failed: I cannot generate this dashboard");
    }
}
