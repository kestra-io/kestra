package io.kestra.webserver.services.ai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private ExpressionContextService expressionContextService;
    private PosthogService posthogService;
    private FlowYamlBuilder flowYamlBuilder;
    private DashboardYamlBuilder dashboardYamlBuilder;

    @BeforeEach
    void setUp() {
        InstanceService instanceService = mock(InstanceService.class);
        when(instanceService.fetch()).thenReturn("instance-id");
        JsonSchemaGenerator jsonSchemaGenerator = mock(JsonSchemaGenerator.class);
        when(jsonSchemaGenerator.schemas(any(), eq(false), anyList(), eq(true))).thenReturn(Map.of());
        expressionContextService = mock(ExpressionContextService.class);
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
        verify(service).beforeGeneration(eq(USER_INFO), eq(CONVERSATION_ID), anyString(), anyMap());
        verify(service).afterGeneration(any(), anyString(), anyMap(), eq(yaml), anyString());
        verify(service, never()).onGenerationFailure(any(), any());
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
        verifyFailure(failure);
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldInvokeFailureCallbackWhenContextIsNull(GenerationType type) {
        AiException failure = new AiException("Generation failed.");
        stubGeneration(type, invocation ->
        {
            throw failure;
        });

        assertThatThrownBy(() -> generate(type, null)).isSameAs(failure).hasNoSuppressedExceptions();

        verify(service).onGenerationFailure(isNull(), same(failure));
        verify(service, never()).afterGeneration(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldPreserveOriginalExceptionWhenCleanupThrows(GenerationType type) {
        MetadataAppenderChatModelListener listener = metadataListener();
        AiException failure = new AiException("Generation failed.");
        IllegalStateException cleanupFailure = new IllegalStateException("Cleanup failed.");
        stubGeneration(type, invocation ->
        {
            throw failure;
        });
        doAnswer(invocation ->
        {
            invocation.callRealMethod();
            throw cleanupFailure;
        }).when(service).onGenerationFailure(any(), same(failure));

        assertThatThrownBy(() -> generate(type, CONVERSATION_ID)).isSameAs(failure);

        assertThat(failure.getSuppressed()).containsExactly(cleanupFailure);
        assertThat(listener.conversationMetadataGetter().get()).isNull();
        verifyFailure(failure);
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldPreserveGenerationErrorWhenFailureCleanupThrowsError(GenerationType type) {
        AssertionError failure = new AssertionError("Generation failed.");
        AssertionError cleanupFailure = new AssertionError("Cleanup failed.");
        stubGeneration(type, invocation ->
        {
            throw failure;
        });
        doAnswer(invocation ->
        {
            invocation.callRealMethod();
            throw cleanupFailure;
        }).when(service).onGenerationFailure(any(), same(failure));

        assertThatThrownBy(() -> generate(type, CONVERSATION_ID)).isSameAs(failure);

        assertThat(failure.getSuppressed()).containsExactly(cleanupFailure);
        assertThat(metadataListener().conversationMetadataGetter().get()).isNull();
        verifyFailure(failure);
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldPreserveOriginalExceptionWhenCleanupRethrowsIt(GenerationType type) {
        AiException failure = new AiException("Generation failed.");
        stubGeneration(type, invocation ->
        {
            throw failure;
        });
        doAnswer(invocation ->
        {
            invocation.callRealMethod();
            throw failure;
        }).when(service).onGenerationFailure(any(), same(failure));

        assertThatThrownBy(() -> generate(type, CONVERSATION_ID)).isSameAs(failure).hasNoSuppressedExceptions();

        assertThat(metadataListener().conversationMetadataGetter().get()).isNull();
        verifyFailure(failure);
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldNotInvokeFailureCallbackWhenBeforeGenerationThrows(GenerationType type) {
        IllegalStateException failure = new IllegalStateException("Generation cannot start.");
        doThrow(failure).when(service).beforeGeneration(any(), any(), any(), any());

        assertThatThrownBy(() -> generate(type, CONVERSATION_ID)).isSameAs(failure);

        verify(service, never()).onGenerationFailure(any(), any());
        verify(service, never()).afterGeneration(any(), any(), any(), any(), any());
        verifyNoInteractions(flowYamlBuilder, dashboardYamlBuilder);
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldCleanUpWhenPreparationFails(GenerationType type) {
        IllegalStateException failure = new IllegalStateException("Cannot create the plugin finder.");
        doThrow(failure).when(service).pluginFinder(any());

        assertThatThrownBy(() -> generate(type, CONVERSATION_ID)).isSameAs(failure);

        assertThat(metadataListener().conversationMetadataGetter().get()).isNull();
        verifyFailure(failure);
    }

    @Test
    void shouldCleanUpWhenFlowExpressionContextFails() {
        IllegalStateException failure = new IllegalStateException("Cannot build the expression context.");
        when(expressionContextService.buildGlobalExpressionContext()).thenThrow(failure);

        assertThatThrownBy(() -> generate(GenerationType.FLOW, CONVERSATION_ID)).isSameAs(failure);

        assertThat(metadataListener().conversationMetadataGetter().get()).isNull();
        verifyFailure(failure);
    }

    @ParameterizedTest
    @EnumSource(GenerationType.class)
    void shouldInvokeFailureCallbackOnceWhenCompletionThrows(GenerationType type) {
        IllegalStateException failure = new IllegalStateException("Cannot capture the generation result.");
        stubGeneration(type, invocation -> "id: generated\n");
        doAnswer(invocation ->
        {
            Map<String, Object> properties = invocation.getArgument(2);
            if (properties.containsKey("$ai_output_state")) {
                throw failure;
            }
            return null;
        }).when(posthogService).capture(anyString(), anyString(), anyMap());

        assertThatThrownBy(() -> generate(type, CONVERSATION_ID)).isSameAs(failure).hasNoSuppressedExceptions();

        assertThat(metadataListener().conversationMetadataGetter().get()).isNull();
        verify(service).afterGeneration(any(), anyString(), anyMap(), anyString(), anyString());
        verify(service).onGenerationFailure(any(), same(failure));
    }

    @ParameterizedTest
    @EnumSource(GenerationOutcome.class)
    void shouldPreserveNewerRequestMetadataWhenOlderRequestCleansUp(GenerationOutcome outcome) {
        MetadataAppenderChatModelListener listener = metadataListener();
        AiService.GenerationContext olderContext = service.beforeGeneration(USER_INFO, CONVERSATION_ID, "Generation", Map.of());
        AiService.GenerationContext newerContext = service.beforeGeneration(USER_INFO, CONVERSATION_ID, "Generation", Map.of());
        AiService.ConversationMetadata newerMetadata = listener.conversationMetadataGetter().get();
        assertThat(olderContext.parentSpanId()).isNotEqualTo(newerContext.parentSpanId());
        assertThat(newerMetadata.parentSpanId()).isEqualTo(newerContext.parentSpanId());

        switch (outcome) {
            case SUCCESS -> service.afterGeneration(olderContext, "GenerationResult", Map.of(), "id: generated\n", "yaml");
            case FAILURE -> service.onGenerationFailure(olderContext, new AiException("Generation failed."));
        }

        assertThat(listener.conversationMetadataGetter().get()).isSameAs(newerMetadata);

        service.afterGeneration(newerContext, "GenerationResult", Map.of(), "id: generated\n", "yaml");

        assertThat(listener.conversationMetadataGetter().get()).isNull();
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

    private void verifyFailure(Throwable failure) {
        verify(service).beforeGeneration(eq(USER_INFO), eq(CONVERSATION_ID), anyString(), anyMap());
        verify(service).onGenerationFailure(argThat(context -> context != null && CONVERSATION_ID.equals(context.conversationId())), same(failure));
        verify(service, never()).afterGeneration(any(), any(), any(), any(), any());
    }

    private enum GenerationType {
        FLOW,
        DASHBOARD
    }

    private enum GenerationOutcome {
        SUCCESS,
        FAILURE
    }
}
