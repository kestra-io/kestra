package io.kestra.webserver.services.ai;

import java.util.List;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataAppenderChatModelListenerTest {

    @Test
    void shouldPopulateAttributesWithUserUid() {
        // Given
        AiService.ConversationMetadata metadata = new AiService.ConversationMetadata(
            "conv-1", "192.168.1.1", "parent-span-1", "browser-uid-123"
        );
        MetadataAppenderChatModelListener listener = new MetadataAppenderChatModelListener(
            "instance-uid-456", "google", "FlowGeneration", null, () -> metadata
        );

        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("test")))
            .modelName("gemini-2.0-flash")
            .build();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, null, new HashMap<>());

        // When
        listener.onRequest(requestContext);

        // Then
        assertThat(requestContext.attributes())
            .containsEntry(MetadataAppenderChatModelListener.USER_UID, "browser-uid-123")
            .containsEntry(MetadataAppenderChatModelListener.INSTANCE_UID, "instance-uid-456")
            .containsEntry(MetadataAppenderChatModelListener.CONVERSATION_ID, "conv-1")
            .containsEntry(MetadataAppenderChatModelListener.IP, "192.168.1.1")
            .containsEntry(MetadataAppenderChatModelListener.PARENT_ID, "parent-span-1")
            .containsEntry(MetadataAppenderChatModelListener.SPAN_NAME, "FlowGeneration")
            .containsEntry(MetadataAppenderChatModelListener.PROVIDER, "google")
            .doesNotContainKey(MetadataAppenderChatModelListener.BASE_URL);
    }

    @Test
    void shouldIncludeBaseUrlWhenProvided() {
        // Given
        AiService.ConversationMetadata metadata = new AiService.ConversationMetadata(
            "conv-2", "10.0.0.1", "parent-span-2", "user-uid-789"
        );
        MetadataAppenderChatModelListener listener = new MetadataAppenderChatModelListener(
            "instance-uid-456", "gemini", "FlowGeneration", "https://generativelanguage.googleapis.com", () -> metadata
        );

        ChatRequest request = ChatRequest.builder()
            .messages(List.of(UserMessage.from("test")))
            .modelName("gemini-2.0-flash")
            .build();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, null, new HashMap<>());

        // When
        listener.onRequest(requestContext);

        // Then
        assertThat(requestContext.attributes())
            .containsEntry(MetadataAppenderChatModelListener.BASE_URL, "https://generativelanguage.googleapis.com");
    }
}
