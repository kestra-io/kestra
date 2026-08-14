package io.kestra.webserver.services.ai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.ai.agent.models.AgentPrincipal;
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

    /**
     * The streaming model for a turn run on behalf of {@code principal}. Defaults to ignoring it, so only a
     * provider whose request varies by caller — the hosted free tier, whose identity headers are metered —
     * needs to implement this.
     */
    default StreamingChatModel streamingChatModel(@Nullable AgentPrincipal principal, List<ChatModelListener> listeners) {
        return streamingChatModel(listeners);
    }

    /**
     * This provider's ceiling, or empty when it has none switched on. Empty covers both "configured nothing"
     * and "configured then disabled", so callers need no second {@code enabled()} check.
     */
    default Optional<AiUsageLimitConfiguration> usageLimit() {
        return Optional.empty();
    }

    default AiService.GenerationContext beforeGeneration(UserInfo userInfo, String conversationId, String spanName, Map<String, String> inputState) {
        return null;
    }

    default String afterGeneration(AiService.GenerationContext context, String spanName, Map<String, Object> outputState, String result, String outputKey) {
        return result;
    }
}
