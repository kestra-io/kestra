package io.kestra.webserver.services.ai;

import java.util.List;
import java.util.Map;

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
     * The streaming model for a turn run on behalf of {@code principal}.
     *
     * <p>Defaults to ignoring the principal, so a provider only implements this if the caller's identity
     * changes what it builds — which today means the hosted free tier, where the identity is what the relay
     * meters spend against.
     */
    default StreamingChatModel streamingChatModel(@Nullable AgentPrincipal principal, List<ChatModelListener> listeners) {
        return streamingChatModel(listeners);
    }

    default AiService.GenerationContext beforeGeneration(UserInfo userInfo, String conversationId, String spanName, Map<String, String> inputState) {
        return null;
    }

    default String afterGeneration(AiService.GenerationContext context, String spanName, Map<String, Object> outputState, String result, String outputKey) {
        return result;
    }
}
