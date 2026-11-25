package io.kestra.webserver.services.ai.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Only the AI provider (which drives an LLM) is mocked; the flow repository and validation are the real beans.
 */
@KestraTest(environments = "memory")
class ReviseFlowToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai.revise";

    @Inject
    private ReviseFlowTool tool;

    @Inject
    private AiServiceManager aiServiceManager;

    @Inject
    private FlowRepositoryInterface flowRepository;

    private AiServiceInterface aiService;
    private AgentCallContext.Context params;

    @MockBean(AiServiceManager.class)
    AiServiceManager aiServiceManager() {
        return mock(AiServiceManager.class);
    }

    @BeforeEach
    void setUp() {
        aiService = mock(AiServiceInterface.class);
        params = AgentCallContext.Context.builder().tenant(MAIN_TENANT).providerId("provider-1").conversationId("thread-1").build();
        when(aiServiceManager.getAiService("provider-1")).thenReturn(aiService);
    }

    @Test
    void shouldReviseSavedSourceWhenNoYamlIsProvided() {
        String flowId = IdUtils.create();
        String saved = flowYaml(flowId, "hi");
        String revision = flowYaml(flowId, "hello");
        flowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, saved));
        when(aiService.generateFlow(any(), any(), eq(MAIN_TENANT))).thenReturn(GenerationResult.of(revision));
        ArgumentCaptor<FlowGenerationPrompt> prompt = ArgumentCaptor.forClass(FlowGenerationPrompt.class);

        ReviseFlowTool.Result result = tool.reviseFlow("say hello", NAMESPACE, flowId, null, params);

        verify(aiService).generateFlow(any(), prompt.capture(), eq(MAIN_TENANT));
        assertThat(prompt.getValue().getYaml()).isEqualTo(saved);
        assertThat(prompt.getValue().getNamespace()).isEqualTo(NAMESPACE);
        assertThat(result.kind()).isEqualTo(ArtefactKind.FLOW);
        assertThat(result.valid()).isTrue();
        assertThat(result.yaml()).isEqualTo(revision);
        assertThat(result.namespace()).isEqualTo(NAMESPACE);
        assertThat(result.flowId()).isEqualTo(flowId);
        assertThat(result.artefact().yaml()).isEqualTo(revision);
    }

    @Test
    void shouldReviseProvidedYamlWhenYamlIsProvided() {
        String flowId = IdUtils.create();
        String unsaved = flowYaml(flowId, "unsaved");
        flowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, flowYaml(flowId, "hi")));
        when(aiService.generateFlow(any(), any(), eq(MAIN_TENANT))).thenReturn(GenerationResult.of(flowYaml(flowId, "hello")));
        ArgumentCaptor<FlowGenerationPrompt> prompt = ArgumentCaptor.forClass(FlowGenerationPrompt.class);

        tool.reviseFlow("say hello", NAMESPACE, flowId, unsaved, params);

        verify(aiService).generateFlow(any(), prompt.capture(), eq(MAIN_TENANT));
        assertThat(prompt.getValue().getYaml()).isEqualTo(unsaved);
    }

    @Test
    void shouldDiscardDraftWhenItIsWrittenAsAnotherFlow() {
        String flowId = IdUtils.create();
        flowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, flowYaml(flowId, "hi")));
        when(aiService.generateFlow(any(), any(), eq(MAIN_TENANT))).thenReturn(GenerationResult.of(flowYaml("another_flow", "hi")));

        assertThatThrownBy(() -> tool.reviseFlow("fix the expression", NAMESPACE, flowId, null, params))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("instead of revising '%s.%s'".formatted(NAMESPACE, flowId));
    }

    @Test
    void shouldFailWithoutGeneratingWhenFlowIsNotFound() {
        assertThatThrownBy(() -> tool.reviseFlow("fix the expression", NAMESPACE, "missing_flow", null, params))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Flow not found: '%s.missing_flow'".formatted(NAMESPACE));
        verify(aiService, never()).generateFlow(any(), any(), any());
    }

    private static String flowYaml(final String id, final String message) {
        return "id: %s\nnamespace: %s\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: %s\n".formatted(id, NAMESPACE, message);
    }
}
