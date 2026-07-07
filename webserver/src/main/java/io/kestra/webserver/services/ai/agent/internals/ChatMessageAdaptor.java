package io.kestra.webserver.services.ai.agent.internals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.kestra.core.serializers.JacksonMapper;
import io.kestra.webserver.services.ai.agent.domain.Message;
import io.kestra.webserver.services.ai.agent.domain.ToolCall;
import io.kestra.webserver.services.ai.agent.domain.ToolFamily;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Converts between the durable {@link Message} log and the LangChain4j {@link ChatMessage} shapes,
 * and parses tool-call argument JSON. Stateless.
 */
public final class ChatMessageAdaptor {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private ChatMessageAdaptor() {
    }

    /** Project the durable Message log down to the four LangChain4j message kinds for the next request. */
    public static List<ChatMessage> project(final List<Message> rows) {
        List<ChatMessage> out = new ArrayList<>();
        for (Message m : rows) {
            switch (m.type()) {
                case TEXT -> {
                    switch (m.role()) {
                        case USER -> out.add(UserMessage.from(nullToEmpty(m.content())));
                        case ASSISTANT -> out.add(AiMessage.from(nullToEmpty(m.content())));
                        default -> { /* SYSTEM/TOOL text: skip */ }
                    }
                }
                case TOOL_CALL -> {
                    if (m.toolCall() != null) {
                        out.add(AiMessage.from(toRequest(m.toolCall())));
                    }
                }
                case TOOL_RESULT -> {
                    if (m.toolCall() != null) {
                        out.add(ToolExecutionResultMessage.from(toRequest(m.toolCall()), toJson(m.toolResult())));
                    }
                }
                // PROPOSED_ACTION is superseded by the following TOOL_RESULT — never projected.
                case PROPOSED_ACTION -> { /* omit */ }
            }
        }
        return out;
    }

    public static ToolCall toToolCall(final ToolExecutionRequest req, final ToolFamily family) {
        return ToolCall.platform(req.id(), req.name(), family, parseArguments(req.arguments()));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseArguments(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("_raw", json);
        }
    }

    private static ToolExecutionRequest toRequest(final ToolCall toolCall) {
        return ToolExecutionRequest.builder()
            .id(toolCall.id())
            .name(toolCall.tool())
            .arguments(toJson(toolCall.arguments()))
            .build();
    }

    private static String toJson(final Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String nullToEmpty(final String s) {
        return s == null ? "" : s;
    }
}
