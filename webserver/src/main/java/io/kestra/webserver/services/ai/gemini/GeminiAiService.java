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

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        return GoogleAiGeminiChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .temperature(getAiConfiguration().temperature())
            .topP(getAiConfiguration().topP())
            .topK(getAiConfiguration().topK())
            .maxOutputTokens(getAiConfiguration().maxOutputTokens())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .build();
    }
}

