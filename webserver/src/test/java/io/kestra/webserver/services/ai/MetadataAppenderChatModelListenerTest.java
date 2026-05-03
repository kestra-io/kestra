package io.kestra.webserver.services.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.model.chat.listener.ChatModelRequestContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataAppenderChatModelListenerTest {

    @Test
    void shouldNotThrowWhenIpIsNull() {
        // Given
        AiService.ConversationMetadata metadata = new AiService.ConversationMetadata(
            "conv-id", null, "parent-id", "user-uid"
        );
        MetadataAppenderChatModelListener listener = new MetadataAppenderChatModelListener(
            "instance-uid", "openai", "FlowYamlBuilder", null, () -> metadata
        );
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        when(requestContext.attributes()).thenReturn(attributes);

        // When/Then
        assertThatCode(() -> listener.onRequest(requestContext)).doesNotThrowAnyException();
        assertThat(attributes.get(MetadataAppenderChatModelListener.IP)).isEqualTo("");
    }

    @Test
    void shouldNotThrowWhenConversationMetadataIsNull() {
        // Given - metadata not yet stored (race or missing beforeGeneration call)
        MetadataAppenderChatModelListener listener = new MetadataAppenderChatModelListener(
            "instance-uid", "openai", "FlowYamlBuilder", null, () -> null
        );
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        when(requestContext.attributes()).thenReturn(attributes);

        // When/Then
        assertThatCode(() -> listener.onRequest(requestContext)).doesNotThrowAnyException();
        assertThat(attributes).isEmpty();
    }

    @Test
    void shouldPopulateAttributesWithNonNullIp() {
        // Given
        AiService.ConversationMetadata metadata = new AiService.ConversationMetadata(
            "conv-id", "127.0.0.1", "parent-id", "user-uid"
        );
        MetadataAppenderChatModelListener listener = new MetadataAppenderChatModelListener(
            "instance-uid", "openai", "FlowYamlBuilder", "http://base.url", () -> metadata
        );
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = mock(ChatModelRequestContext.class);
        when(requestContext.attributes()).thenReturn(attributes);

        // When
        listener.onRequest(requestContext);

        // Then
        assertThat(attributes)
            .containsEntry(MetadataAppenderChatModelListener.PARENT_ID, "parent-id")
            .containsEntry(MetadataAppenderChatModelListener.IP, "127.0.0.1")
            .containsEntry(MetadataAppenderChatModelListener.PROVIDER, "openai")
            .containsEntry(MetadataAppenderChatModelListener.BASE_URL, "http://base.url");
    }
}
