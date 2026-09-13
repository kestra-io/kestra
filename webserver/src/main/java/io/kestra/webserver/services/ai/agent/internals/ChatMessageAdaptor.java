package io.kestra.webserver.services.ai.agent.internals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentThinking;
import io.kestra.core.ai.agent.models.AgentToolCall;
import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.serializers.JacksonMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.micronaut.core.annotation.Nullable;

/**
 * Converts between the durable {@link AgentMessage} log and the LangChain4j {@link ChatMessage} shapes,
 * and parses tool-call argument JSON. Stateless.
 */
public final class ChatMessageAdaptor {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    /**
     * AiMessage attribute key under which LangChain4j stores a turn's reasoning signature — Gemini and
     * Anthropic both use this exact key. We read it off the live response and write it back on projection so
     * the provider's cross-turn signature requirement is met. Must match LangChain4j's key exactly.
     */
    public static final String THINKING_SIGNATURE_KEY = "thinking_signature";

    /** AiMessage attribute key for Anthropic's encrypted/redacted thinking blocks. Must match LangChain4j's key exactly. */
    public static final String REDACTED_THINKING_KEY = "redacted_thinking";

    private ChatMessageAdaptor() {
    }

    /** Project the durable AgentMessage log down to the four LangChain4j message kinds for the next request. */
    public static List<ChatMessage> project(final List<AgentMessage> rows) {
        List<ChatMessage> out = new ArrayList<>();
        for (AgentMessage m : rows) {
            switch (m.type()) {
                case TEXT -> {
                    switch (m.role()) {
                        case USER -> out.add(UserMessage.from(nullToEmpty(m.content())));
                        // Never replay an empty assistant/model turn: a blank model message is a
                        // documented trigger for empty finishReason=STOP responses, so a single empty
                        // response would otherwise keep poisoning every subsequent turn. Skipping it
                        // also heals threads that already persisted one before this guard existed.
                        case ASSISTANT -> {
                            if (m.content() != null && !m.content().isBlank()) {
                                out.add(AiMessage.from(m.content()));
                            }
                        }
                        default -> {
                            /* SYSTEM/TOOL text: skip */ }
                    }
                }
                case TOOL_CALL -> {
                    if (m.toolCall() != null) {
                        out.add(toAiMessage(m.toolCall()));
                    }
                }
                case TOOL_RESULT -> {
                    if (m.toolCall() != null) {
                        ToolExecutionRequest req = toRequest(m.toolCall());
                        Map<String, Object> toolResult = m.toolResult();
                        // Preserve the error flag on reload so a previously-failed tool call is still
                        // presented to the model (and provider APIs) as an error, matching the live path.
                        boolean isError = toolResult != null && "error".equals(toolResult.get("outcome"));
                        out.add(ToolExecutionResultMessage.builder()
                            .id(req.id())
                            .toolName(req.name())
                            .text(toJson(toolResult))
                            .isError(isError)
                            .build());
                    }
                }
                // PROPOSED_ACTION is superseded by the following TOOL_RESULT — never projected.
                case PROPOSED_ACTION -> {
                    /* omit */ }
                // ARTEFACT_DRAFT is a UI-facing card; the authoring tool's TOOL_RESULT already
                // carries the draft for the model.
                case ARTEFACT_DRAFT -> {
                    /* omit */ }
                // CANCELLED is a trace-only marker for an aborted turn — never sent to the model.
                case CANCELLED -> {
                    /* omit */ }
            }
        }
        return out;
    }

    public static AgentToolCall toToolCall(final ToolExecutionRequest req, final AgentToolCall.Kind kind, @Nullable final AgentToolFamily family) {
        return toToolCall(req, kind, family, null);
    }

    public static AgentToolCall toToolCall(final ToolExecutionRequest req, final AgentToolCall.Kind kind, @Nullable final AgentToolFamily family, @Nullable final AgentThinking thinking) {
        return kind == AgentToolCall.Kind.AUTHORING
            ? AgentToolCall.authoring(req.id(), req.name(), parseArguments(req.arguments()), thinking)
            : AgentToolCall.platform(req.id(), req.name(), family, parseArguments(req.arguments()), thinking);
    }

    /**
     * Snapshot the model turn's reasoning state from the response {@link AiMessage} — the {@code thinking()}
     * text plus the reasoning attributes LangChain4j stashes ({@code thinking_signature},
     * {@code redacted_thinking}) — or {@code null} when the provider/model emits none. Captured once per model
     * turn and persisted on each of that turn's tool calls so they can be replayed to the provider intact.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static AgentThinking thinkingOf(final AiMessage ai) {
        if (ai == null) {
            return null;
        }
        AgentThinking thinking = new AgentThinking(
            ai.thinking(),
            ai.attribute(THINKING_SIGNATURE_KEY, String.class),
            ai.attribute(REDACTED_THINKING_KEY, List.class)
        );
        return thinking.isEmpty() ? null : thinking;
    }

    /**
     * Rebuild the model-facing {@link AiMessage} for a persisted tool call, re-attaching the stored reasoning
     * state (thinking text + signature/redacted attributes) exactly where LangChain4j expects it, so the
     * provider round-trip stays valid across turns.
     */
    private static AiMessage toAiMessage(final AgentToolCall toolCall) {
        AiMessage.Builder builder = AiMessage.builder().toolExecutionRequests(List.of(toRequest(toolCall)));
        AgentThinking thinking = toolCall.thinking();
        if (thinking != null && !thinking.isEmpty()) {
            if (thinking.text() != null && !thinking.text().isBlank()) {
                builder.thinking(thinking.text());
            }
            Map<String, Object> attributes = new java.util.HashMap<>();
            if (thinking.signature() != null && !thinking.signature().isBlank()) {
                attributes.put(THINKING_SIGNATURE_KEY, thinking.signature());
            }
            if (thinking.redacted() != null && !thinking.redacted().isEmpty()) {
                attributes.put(REDACTED_THINKING_KEY, thinking.redacted());
            }
            if (!attributes.isEmpty()) {
                builder.attributes(attributes);
            }
        }
        return builder.build();
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

    public static ToolExecutionRequest toRequest(final AgentToolCall toolCall) {
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
