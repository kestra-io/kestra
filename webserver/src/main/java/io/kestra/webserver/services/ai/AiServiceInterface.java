package io.kestra.webserver.services.ai;

import java.util.List;
import java.util.Map;

import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.annotation.WebServerEnabled;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.micronaut.core.annotation.Nullable;

/**
 * Service for chatting with an AI model.
 */
@WebServerEnabled
public interface AiServiceInterface {
    GenerationResult generateFlow(UserInfo userInfo, FlowGenerationPrompt flowGenerationPrompt, String tenantId);

    GenerationResult generateDashboard(UserInfo userInfo, DashboardGenerationPrompt dashboardGenerationPrompt);

    String displayName();

    default StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        throw new UnsupportedOperationException("Streaming chat is not supported by this AI provider");
    }

    default AiService.GenerationContext beforeGeneration(UserInfo userInfo, String conversationId, String spanName, Map<String, String> inputState) {
        return null;
    }

    default String afterGeneration(AiService.GenerationContext context, String spanName, Map<String, Object> outputState, String result, String outputKey) {
        return result;
    }

    /**
     * Called by {@link AiService} once for an unchecked failure during preparation, generation, or {@code afterGeneration},
     * after {@code beforeGeneration} returns normally; its context may be null.
     * Unchecked callback failures are suppressed on the original throwable unless they are that same throwable.
     *
     * <p>
     * This is a cleanup notification, not a quota rollback signal: generation or accounting may already have completed,
     * and invocation does not establish that a reservation exists or should be refunded.
     *
     * <p>
     * Overrides in {@code AiService} subclasses must call {@code super.onGenerationFailure(context, failure)} to retain
     * OSS metadata cleanup.
     */
    default void onGenerationFailure(@Nullable AiService.GenerationContext context, Throwable failure) {
    }
}
