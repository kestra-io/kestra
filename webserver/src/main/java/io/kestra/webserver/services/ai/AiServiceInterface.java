package io.kestra.webserver.services.ai;

import io.kestra.webserver.annotation.WebServerEnabled;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import io.kestra.webserver.models.ai.DashboardGenerationPrompt;

import java.util.Map;

/**
 * Service for chatting with an AI model.
 */
@WebServerEnabled
public interface AiServiceInterface {
    String generateFlow(String ip, FlowGenerationPrompt flowGenerationPrompt);

    String generateDashboard(String ip, DashboardGenerationPrompt dashboardGenerationPrompt);

    String displayName();

    default AiService.GenerationContext beforeGeneration(String ip, String conversationId, String spanName, Map<String, String> inputState) {
        return null;
    }

    default String afterGeneration(AiService.GenerationContext context, String spanName, Map<String, Object> outputState, String result, String outputKey) {
        return result;
    }
}
