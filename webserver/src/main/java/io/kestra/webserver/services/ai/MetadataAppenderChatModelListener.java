package io.kestra.webserver.services.ai;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import io.kestra.core.utils.IdUtils;
import io.micrometer.core.instrument.Clock;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record MetadataAppenderChatModelListener(String instanceUid, String provider, String spanName, Supplier<AiService.ConversationMetadata> conversationMetadataGetter) implements ChatModelListener {
    public static final String SPAN_NAME = "spanName";
    public static final String PARENT_ID = "parentId";
    public static final String START_TIME_KEY_NAME = "startTime";
    public static final String CONVERSATION_ID = "conversationId";
    public static final String IP = "ip";
    public static final String INSTANCE_UID = "instanceUid";
    public static final String PROVIDER = "provider";

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        AiService.ConversationMetadata conversationMetadata = conversationMetadataGetter().get();
        requestContext.attributes().putAll(Map.of(
            PARENT_ID, conversationMetadata.parentSpanId(),
            SPAN_NAME, this.spanName(),
            START_TIME_KEY_NAME, Clock.SYSTEM.monotonicTime(),
            CONVERSATION_ID, conversationMetadata.conversationId(),
            PROVIDER, this.provider(),
            IP, conversationMetadata.ip(),
            INSTANCE_UID, this.instanceUid()
        ));
    }
}
