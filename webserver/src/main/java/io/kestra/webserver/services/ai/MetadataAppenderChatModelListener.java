package io.kestra.webserver.services.ai;

import java.util.Map;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import io.micrometer.core.instrument.Clock;

public record MetadataAppenderChatModelListener(String instanceUid, String provider, String spanName, String baseUrl,
    Supplier<AiService.ConversationMetadata> conversationMetadataGetter) implements ChatModelListener {

    public static final String SPAN_NAME = "spanName";
    public static final String PARENT_ID = "parentId";
    public static final String START_TIME_KEY_NAME = "startTime";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String IP = "ip";
    public static final String INSTANCE_UID = "instanceUid";
    public static final String USER_UID = "userUid";
    public static final String PROVIDER = "provider";
    public static final String BASE_URL = "baseUrl";

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        AiService.ConversationMetadata conversationMetadata = conversationMetadataGetter().get();
        requestContext.attributes().putAll(
            Map.of(
                PARENT_ID, conversationMetadata.parentSpanId(),
                SPAN_NAME, this.spanName(),
                START_TIME_KEY_NAME, Clock.SYSTEM.monotonicTime(),
                CONVERSATION_ID, conversationMetadata.conversationId(),
                PROVIDER, this.provider(),
                IP, conversationMetadata.ip(),
                INSTANCE_UID, this.instanceUid(),
                USER_UID, conversationMetadata.uid()
            )
        );
        if (this.baseUrl() != null) {
            requestContext.attributes().put(BASE_URL, this.baseUrl());
        }
    }
}
