package io.kestra.webserver.services.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import io.kestra.webserver.services.posthog.PosthogService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosthogChatModelListenerTest {

    @Mock
    private PosthogService posthogService;

    @InjectMocks
    private PosthogChatModelListener listener;

    @Test
    void shouldNotThrowAndSkipCaptureWhenParentIdMissing() {
        // Given - attributes with no PARENT_ID (simulates onRequest failure)
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequestParameters params = mock(ChatRequestParameters.class);
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.modelName()).thenReturn("gemini-2.0-flash");
        when(chatRequest.parameters()).thenReturn(params);
        when(chatRequest.messages()).thenReturn(List.of());

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.hasToolExecutionRequests()).thenReturn(false);
        when(aiMessage.text()).thenReturn("some response");

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.id()).thenReturn("resp-id");

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatResponse.metadata()).thenReturn(metadata);
        when(chatResponse.tokenUsage()).thenReturn(null);

        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        when(responseContext.chatRequest()).thenReturn(chatRequest);
        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(responseContext.attributes()).thenReturn(attributes);

        // When/Then
        assertThatCode(() -> listener.onResponse(responseContext)).doesNotThrowAnyException();
        verify(posthogService, never()).capture(any(), any(), any());
    }

    @Test
    void shouldCaptureWhenAllAttributesPresent() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        attributes.put(MetadataAppenderChatModelListener.PARENT_ID, "parent-id");
        attributes.put(MetadataAppenderChatModelListener.SPAN_NAME, "FlowYamlBuilder");
        attributes.put(MetadataAppenderChatModelListener.CONVERSATION_ID, "conv-id");
        attributes.put(MetadataAppenderChatModelListener.USER_UID, "user-uid");

        ChatRequestParameters params = mock(ChatRequestParameters.class);
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.modelName()).thenReturn("gemini-2.0-flash");
        when(chatRequest.parameters()).thenReturn(params);
        when(chatRequest.messages()).thenReturn(List.of());

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.hasToolExecutionRequests()).thenReturn(false);
        when(aiMessage.text()).thenReturn("generated yaml");

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.id()).thenReturn("resp-id");

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatResponse.metadata()).thenReturn(metadata);
        when(chatResponse.tokenUsage()).thenReturn(null);

        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        when(responseContext.chatRequest()).thenReturn(chatRequest);
        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(responseContext.attributes()).thenReturn(attributes);

        // When
        listener.onResponse(responseContext);

        // Then
        verify(posthogService, times(1)).capture(eq("user-uid"), eq("$ai_generation"), any());
    }

    @Test
    void shouldNotThrowAndSkipCaptureOnErrorWhenParentIdMissing() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();

        ChatRequestParameters params = mock(ChatRequestParameters.class);
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.modelName()).thenReturn("gemini-2.0-flash");
        when(chatRequest.parameters()).thenReturn(params);

        ChatModelErrorContext errorContext = mock(ChatModelErrorContext.class);
        when(errorContext.chatRequest()).thenReturn(chatRequest);
        when(errorContext.error()).thenReturn(new RuntimeException("LLM error"));
        when(errorContext.attributes()).thenReturn(attributes);

        // When/Then
        assertThatCode(() -> listener.onError(errorContext)).doesNotThrowAnyException();
        verify(posthogService, never()).capture(any(), any(), any());
    }

    @Test
    void shouldNotIncludeIpPropertyWhenIpIsBlank() {
        // Given - blank IP attribute (set by MetadataAppenderChatModelListener when IP is null)
        Map<Object, Object> attributes = new HashMap<>();
        attributes.put(MetadataAppenderChatModelListener.PARENT_ID, "parent-id");
        attributes.put(MetadataAppenderChatModelListener.SPAN_NAME, "FlowYamlBuilder");
        attributes.put(MetadataAppenderChatModelListener.CONVERSATION_ID, "conv-id");
        attributes.put(MetadataAppenderChatModelListener.USER_UID, "user-uid");
        attributes.put(MetadataAppenderChatModelListener.IP, "");

        ChatRequestParameters params = mock(ChatRequestParameters.class);
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.modelName()).thenReturn("gemini-2.0-flash");
        when(chatRequest.parameters()).thenReturn(params);
        when(chatRequest.messages()).thenReturn(List.of());

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.hasToolExecutionRequests()).thenReturn(false);
        when(aiMessage.text()).thenReturn("generated yaml");

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.id()).thenReturn("resp-id");

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatResponse.metadata()).thenReturn(metadata);
        when(chatResponse.tokenUsage()).thenReturn(null);

        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        when(responseContext.chatRequest()).thenReturn(chatRequest);
        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(responseContext.attributes()).thenReturn(attributes);

        // When
        listener.onResponse(responseContext);

        // Then - $ip must not be present (blank IP causes PostHog 4xx)
        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.captor();
        verify(posthogService, times(1)).capture(eq("user-uid"), eq("$ai_generation"), propsCaptor.capture());
        assertThat(propsCaptor.getValue()).doesNotContainKey("$ip");
    }

    @Test
    void shouldIncludeIpPropertyWhenIpIsNonBlank() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        attributes.put(MetadataAppenderChatModelListener.PARENT_ID, "parent-id");
        attributes.put(MetadataAppenderChatModelListener.SPAN_NAME, "FlowYamlBuilder");
        attributes.put(MetadataAppenderChatModelListener.CONVERSATION_ID, "conv-id");
        attributes.put(MetadataAppenderChatModelListener.USER_UID, "user-uid");
        attributes.put(MetadataAppenderChatModelListener.IP, "1.2.3.4");

        ChatRequestParameters params = mock(ChatRequestParameters.class);
        ChatRequest chatRequest = mock(ChatRequest.class);
        when(chatRequest.modelName()).thenReturn("gemini-2.0-flash");
        when(chatRequest.parameters()).thenReturn(params);
        when(chatRequest.messages()).thenReturn(List.of());

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.hasToolExecutionRequests()).thenReturn(false);
        when(aiMessage.text()).thenReturn("generated yaml");

        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.id()).thenReturn("resp-id");

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(chatResponse.metadata()).thenReturn(metadata);
        when(chatResponse.tokenUsage()).thenReturn(null);

        ChatModelResponseContext responseContext = mock(ChatModelResponseContext.class);
        when(responseContext.chatRequest()).thenReturn(chatRequest);
        when(responseContext.chatResponse()).thenReturn(chatResponse);
        when(responseContext.attributes()).thenReturn(attributes);

        // When
        listener.onResponse(responseContext);

        // Then - $ip must be present and equal to the provided IP
        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.captor();
        verify(posthogService, times(1)).capture(eq("user-uid"), eq("$ai_generation"), propsCaptor.capture());
        assertThat(propsCaptor.getValue()).containsEntry("$ip", "1.2.3.4");
    }
}
