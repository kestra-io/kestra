package io.kestra.webserver.services.ai;

import io.kestra.webserver.annotation.WebServerEnabled;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;

/**
 * Service for chatting with an AI model.
 */
@WebServerEnabled
public interface AiServiceInterface {
    String generateFlow(String ip, FlowGenerationPrompt flowGenerationPrompt);
}
