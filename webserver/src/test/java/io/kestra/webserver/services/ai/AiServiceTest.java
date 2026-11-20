package io.kestra.webserver.services.ai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.stubbing.Answer;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContext;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.libs.copilot.services.ai.DashboardYamlBuilder;
import io.kestra.libs.copilot.services.ai.FlowYamlBuilder;
import io.kestra.libs.copilot.services.ai.PluginFinder;
import io.kestra.webserver.services.posthog.PosthogService;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiServiceTest {
    private static final String CONVERSATION_ID = "conversation-id";
    private static final UserInfo USER_INFO = new UserInfo("127.0.0.1", "user-id");

    private AiService<AiConfiguration> service;
    private PosthogService posthogService;
    private FlowYamlBuilder flowYamlBuilder;
    private DashboardYamlBuilder dashboardYamlBuilder;

    @BeforeEach
    void setUp() {
        InstanceService instanceService = mock(InstanceService.class);
        when(instanceService.fetch()).thenReturn("instance-id");
        JsonSchemaGenerator jsonSchemaGenerator = mock(JsonSchemaGenerator.class);
        when(jsonSchemaGenerator.schemas(any(), eq(false), anyList(), eq(true))).thenReturn(Map.of());
        ExpressionContextService expressionContextService = mock(ExpressionContextService.class);
        when(expressionContextService.buildGlobalExpressionContext()).thenReturn(new ExpressionContext(Map.of()));
        posthogService = mock(PosthogService.class);
        service = spy(
            new AiService<>(
                mock(PluginRegistry.class), jsonSchemaGenerator, mock(VersionProvider.class), instanceService,
                posthogService, null, "test", "Test", List.of(), mock(AiConfiguration.class),
                expressionContextService, mock(FlowParsingService.class)
            ) {
                @Override
                public ChatModel chatModel(List<ChatModelListener> listeners) {
                    throw new AssertionError("Unit tests must not create a provider chat model.");
                }
            }
        );

        PluginFinder pluginFinder = mock(PluginFinder.class);
        when(pluginFinder.findPlugins(anyString(), anyString())).thenReturn(List.of("io.kestra.plugin.core.log.Log"));
        doReturn(pluginFinder).when(service).pluginFinder(any());
        doReturn(List.of()).when(service).allPluginsMetadata();
        flowYamlBuilder = mock(FlowYamlBuilder.class);
        dashboardYamlBuilder = mock(DashboardYamlBuilder.class);
        doReturn(flowYamlBuilder).when(service).flowYamlBuilder(any());
        doReturn(dashboardYamlBuilder).when(service).dashboardYamlBuilder(any());
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldCompleteAndCleanUpWhenGenerationSucceeds(GenerationType type) {
        MetadataAppenderChatModelListener listener = metadataListener();
        String yaml = "id: generated\n";
        stubGeneration(type, invocation ->
        {
            assertThat(listener.conversationMetadataGetter().get()).isNotNull();
            return yaml;
        });

        assertThat(generate(type, CONVERSATION_ID)).isEqualTo(GenerationResult.of(yaml));

        assertThat(listener.conversationMetadataGetter().get()).isNull();
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldCleanUpAndPreserveExceptionWhenGenerationFails(GenerationType type) {
        MetadataAppenderChatModelListener listener = metadataListener();
        AiException failure = new AiException("Generation failed.");
        stubGeneration(type, invocation ->
        {
            assertThat(listener.conversationMetadataGetter().get()).isNotNull();
            throw failure;
        });

        assertThatThrownBy(() -> generate(type, CONVERSATION_ID)).isSameAs(failure);

        assertThat(listener.conversationMetadataGetter().get()).isNull();
        String spanName = type == GenerationType.FLOW ? "FlowGenerationError" : "DashboardGenerationError";
        String outputKey = type == GenerationType.FLOW ? "generatedFlow" : "generatedDashboard";
        verify(posthogService).capture(
            eq(USER_INFO.uid()), eq("$ai_span"),
            argThat(properties -> spanName.equals(properties.get("$ai_span_name")) && Map.of(outputKey, "").equals(properties.get("$ai_output_state")))
        );
    }

    private GenerationResult generate(GenerationType type, String conversationId) {
        return switch (type) {
            case FLOW -> service.generateFlow(USER_INFO, new FlowGenerationPrompt(conversationId, "Generate a flow", null, null), "tenant-id");
            case DASHBOARD -> service.generateDashboard(USER_INFO, new DashboardGenerationPrompt(conversationId, "Generate a dashboard", null));
        };
    }

    private void stubGeneration(GenerationType type, Answer<String> answer) {
        switch (type) {
            case FLOW -> when(flowYamlBuilder.buildFlow(anyString(), anyString(), anyString(), isNull(), anyString(), anyString(), anyString())).thenAnswer(answer);
            case DASHBOARD -> when(dashboardYamlBuilder.buildDashboard(anyString(), anyString(), anyString())).thenAnswer(answer);
        }
    }

    private MetadataAppenderChatModelListener metadataListener() {
        return (MetadataAppenderChatModelListener) service.listeners("Test", CONVERSATION_ID).getLast();
    }

    private enum GenerationType {
        FLOW,
        DASHBOARD
    }
}
