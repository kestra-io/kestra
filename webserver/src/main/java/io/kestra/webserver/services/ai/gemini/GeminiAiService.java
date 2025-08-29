package io.kestra.webserver.services.ai.gemini;

import com.posthog.java.PostHog;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.*;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Singleton
@Requires(property = "kestra.ai.type", value = GeminiAiService.TYPE)
@Requires(property = "kestra.ai.gemini.api-key")
@Slf4j
public class GeminiAiService extends AiService<GeminiConfiguration> {
    public static final String TYPE = "gemini";

    public GeminiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService, PostHog postHog, List<ChatModelListener> listeners, GeminiConfiguration geminiConfiguration) {
        super(pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, postHog, TYPE, listeners, geminiConfiguration);
    }

    public ChatModel chatModel(List<ChatModelListener> listeners, String modelName, String apiKey, double temperature, Double topP, int maxOutputTokens) {
        return GoogleAiGeminiChatModel.builder()
            .listeners(listeners)
            .modelName(modelName)
            .apiKey(apiKey)
            .temperature(temperature)
            .topP(topP)
            .topK(this.getAiConfiguration().topK())
            .seed(this.getAiConfiguration().seed())
            .maxOutputTokens(maxOutputTokens)
            .logRequestsAndResponses(this.getAiConfiguration().logRequestsAndResponses())
            .build();
    }
}

