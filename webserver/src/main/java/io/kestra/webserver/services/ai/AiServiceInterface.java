package io.kestra.webserver.services.ai;

import java.util.List;
import java.util.Map;

import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.webserver.annotation.WebServerEnabled;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

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
}
