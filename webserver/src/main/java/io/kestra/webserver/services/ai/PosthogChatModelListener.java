package io.kestra.webserver.services.ai;

import com.google.common.collect.Maps;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.posthog.PosthogService;
import io.micrometer.core.instrument.Clock;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@Slf4j
public class PosthogChatModelListener implements ChatModelListener {
    @Inject
    private PosthogService posthogService;

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatRequest request = responseContext.chatRequest();
        ChatResponse response = responseContext.chatResponse();

        Map<String, Object> properties = initBuilder(request, responseContext.attributes());

        properties.put("$ai_trace_id", responseContext.attributes().get(MetadataAppenderChatModelListener.CONVERSATION_ID));
        properties.put("$ai_http_status", 200);
        properties.put("$ai_response_id", response.metadata().id());

        properties.put("$ai_input", inputs(request));
        properties.put("$ai_output_choices", Map.of(
            "content", Map.of(
                "text", response.aiMessage().text(),
                "type", "text"
            ),
            "role", "assistant"
        ));

        if (response.tokenUsage() != null) {
            properties.put("$ai_input_tokens", response.tokenUsage().inputTokenCount());
            properties.put("$ai_output_tokens", response.tokenUsage().outputTokenCount());
        }

        duration(responseContext.attributes())
            .ifPresent(duration -> properties.put("$ai_latency", duration));

        this.send(responseContext.attributes(), properties);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        ChatRequest request = errorContext.chatRequest();
        Throwable error = errorContext.error();

        Map<String, Object> properties = initBuilder(request, errorContext.attributes());
        properties.put("$ai_http_status", 500);
        properties.put("$ai_is_error", true);
        properties.put("$ai_error", error.getMessage());

        duration(errorContext.attributes())
            .ifPresent(duration -> properties.put("$ai_latency", duration));

        this.send(errorContext.attributes(), properties);
    }

    private void send(Map<Object, Object> attributes, Map<String, Object> properties) {
        properties.put("$ai_parent_id", attributes.get(MetadataAppenderChatModelListener.PARENT_ID).toString());
        properties.put("$ai_span_name", attributes.get(MetadataAppenderChatModelListener.SPAN_NAME));
        properties.put("$ai_span_id", IdUtils.create());

        posthogService.capture(
            attributes.get(MetadataAppenderChatModelListener.INSTANCE_UID).toString(),
            "$ai_generation",
            properties
        );
    }

    private static Map<String, Object> initBuilder(ChatRequest request, Map<Object, Object> attributes) {
        Map<String, Object> properties = Maps.newHashMap();

        properties.put("$ai_model", request.modelName());
        if (attributes.containsKey(MetadataAppenderChatModelListener.PROVIDER)) {
            properties.put("$ai_provider", attributes.get(MetadataAppenderChatModelListener.PROVIDER));
        }
        properties.put("$ai_base_url", "https://generativelanguage.googleapis.com");

        if (attributes.containsKey(MetadataAppenderChatModelListener.IP)) {
            properties.put("$ip", attributes.get(MetadataAppenderChatModelListener.IP));
        }

        Map<String, Object> parameters = Maps.newHashMap();

        if (request.parameters().temperature() != null) {
            parameters.put("temperature", request.parameters().temperature());
        }

        if (request.parameters().temperature() != null) {
            parameters.put("top_k", request.parameters().topK());
        }

        if (request.parameters().temperature() != null) {
            parameters.put("top_p", request.parameters().topP());
        }

        if (!parameters.isEmpty()) {
            properties.put("$ai_model_parameters", parameters);
        }

        return properties;
    }

    private static Optional<Double> duration(Map<Object, Object> attributes) {
        Long startTime = (Long) attributes.get(MetadataAppenderChatModelListener.START_TIME_KEY_NAME);

        if (startTime == null) {
            // should never happen
            log.warn("No start time found in response");
            return Optional.empty();
        }

        final long endTime = Clock.SYSTEM.monotonicTime();

        return Optional.of((endTime - startTime) / 1_000_000_000.0);
    }

    private static List<? extends Map<String, String>> inputs(ChatRequest request) {
        return request.messages()
            .stream()
            .map(chatMessage -> {
                if (chatMessage instanceof AiMessage aiMessage) {
                    return Map.of(
                        "role", "assistant",
                        "content", aiMessage.text()
                    );
                } else if (chatMessage instanceof UserMessage userMessage) {
                    return Map.of(
                        "role", "user",
                        "content", userMessage.singleText()
                    );
                } else if (chatMessage instanceof SystemMessage systemMessage) {
                    return Map.of(
                        "role", "system",
                        "content", systemMessage.text()
                    );
                } else {
                    return Map.of(
                        "role", "unknown",
                        "content", chatMessage.type().toString()
                    );
                }
            })
            .toList();
    }
}
