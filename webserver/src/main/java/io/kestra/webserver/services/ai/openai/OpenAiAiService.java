package io.kestra.webserver.services.ai.openai;

import com.posthog.java.PostHog;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
@Requires(property = "kestra.ai.type", value = OpenAiAiService.TYPE)
@Requires(property = "kestra.ai.openai.api-key")
@Slf4j
public class OpenAiAiService extends AiService<OpenAiConfiguration> {
    public static final String TYPE = "openai";

    public OpenAiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService, PostHog postHog, List<ChatModelListener> listeners, OpenAiConfiguration openAiConfiguration) {
        super(pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, postHog, TYPE, listeners, openAiConfiguration);
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        return OpenAiChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .topP(getAiConfiguration().topP())
            .maxCompletionTokens(getAiConfiguration().maxOutputTokens())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .baseUrl(getAiConfiguration().baseUrl())
            .build();
    }
}

