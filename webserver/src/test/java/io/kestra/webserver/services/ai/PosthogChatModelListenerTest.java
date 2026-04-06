package io.kestra.webserver.services.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import io.kestra.webserver.services.posthog.PosthogService;
import io.micrometer.core.instrument.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosthogChatModelListenerTest {

    @Mock
    private PosthogService posthogService;

    @InjectMocks
    private PosthogChatModelListener listener;

    @Test
    void shouldUseUserUidAsDistinctIdOnResponse() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        attributes.put(MetadataAppenderChatModelListener.USER_UID, "browser-uid-123");
        attributes.put(MetadataAppenderChatModelListener.INSTANCE_UID, "instance-uid-456");
        attributes.put(MetadataAppenderChatModelListener.CONVERSATION_ID, "conv-1");
        attributes.put(MetadataAppenderChatModelListener.PARENT_ID, "parent-span-1");
        attributes.put(MetadataAppenderChatModelListener.SPAN_NAME, "FlowGeneration");
        attributes.put(MetadataAppenderChatModelListener.PROVIDER, "google");
        attributes.put(MetadataAppenderChatModelListener.START_TIME_KEY_NAME, Clock.SYSTEM.monotonicTime());

        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("Generate a flow")))
            .modelName("gemini-2.0-flash")
            .build();

        ChatResponse response = ChatResponse.builder()
            .aiMessage(AiMessage.from("id: my-flow"))
            .metadata(ChatResponseMetadata.builder().id("resp-1").tokenUsage(new TokenUsage(10, 20)).build())
            .build();

        ChatModelResponseContext responseContext = new ChatModelResponseContext(response, request, null, attributes);

        // When
        listener.onResponse(responseContext);

        // Then
        ArgumentCaptor<String> distinctIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(posthogService).capture(distinctIdCaptor.capture(), eq("$ai_generation"), propsCaptor.capture());

        assertThat(distinctIdCaptor.getValue()).isEqualTo("browser-uid-123");
        assertThat(propsCaptor.getValue())
            .containsEntry("$ai_trace_id", "conv-1")
            .containsEntry("$ai_parent_id", "parent-span-1")
            .containsEntry("$ai_model", "gemini-2.0-flash")
            .containsEntry("$ai_input_tokens", 10)
            .containsEntry("$ai_output_tokens", 20);
    }

    @Test
    void shouldUseUserUidAsDistinctIdOnError() {
        // Given
        Map<Object, Object> attributes = new HashMap<>();
        attributes.put(MetadataAppenderChatModelListener.USER_UID, "browser-uid-123");
        attributes.put(MetadataAppenderChatModelListener.INSTANCE_UID, "instance-uid-456");
        attributes.put(MetadataAppenderChatModelListener.PARENT_ID, "parent-span-1");
        attributes.put(MetadataAppenderChatModelListener.SPAN_NAME, "FlowGeneration");
        attributes.put(MetadataAppenderChatModelListener.PROVIDER, "google");
        attributes.put(MetadataAppenderChatModelListener.START_TIME_KEY_NAME, Clock.SYSTEM.monotonicTime());

        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("Generate a flow")))
            .modelName("gemini-2.0-flash")
            .build();

        ChatModelErrorContext errorContext = new ChatModelErrorContext(
            new RuntimeException("API error"), request, null, attributes
        );

        // When
        listener.onError(errorContext);

        // Then
        ArgumentCaptor<String> distinctIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(posthogService).capture(distinctIdCaptor.capture(), eq("$ai_generation"), propsCaptor.capture());

        assertThat(distinctIdCaptor.getValue()).isEqualTo("browser-uid-123");
        assertThat(propsCaptor.getValue())
            .containsEntry("$ai_is_error", true)
            .containsEntry("$ai_error", "API error");
    }
}
