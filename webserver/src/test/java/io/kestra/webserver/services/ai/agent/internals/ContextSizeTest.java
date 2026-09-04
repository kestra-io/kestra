package io.kestra.webserver.services.ai.agent.internals;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentMessageRole;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.ai.agent.models.AgentToolCall;
import io.kestra.core.ai.agent.models.AgentToolFamily;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSizeTest {

    @Test
    void shouldReturnZeroForEmptyHistory() {
        assertThat(ContextSize.charsOf(List.of())).isZero();
    }

    @Test
    void shouldCountMessageText() {
        // Given
        List<AgentMessage> history = List.of(text("hello"), text("worldly"));

        // When / Then
        assertThat(ContextSize.charsOf(history)).isEqualTo(12);
    }

    @Test
    void shouldIgnoreNullContent() {
        // Given — a row with no text at all (e.g. a CANCELLED marker)
        List<AgentMessage> history = List.of(text(null));

        // When / Then
        assertThat(ContextSize.charsOf(history)).isZero();
    }

    @Test
    void shouldCountToolCallArgumentsAndToolResults() {
        // Given — a tool call and its result, the parts of a conversation that actually grow
        AgentMessage call = AgentMessage.builder()
            .uid("1")
            .threadId("thread-1")
            .role(AgentMessageRole.ASSISTANT)
            .type(AgentMessageType.TOOL_CALL)
            .toolCall(AgentToolCall.platform("c1", "read-flow", AgentToolFamily.READ, Map.of("id", "abc"), null))
            .traceId("t1")
            .createdAt(Instant.now())
            .build();
        AgentMessage result = AgentMessage.builder()
            .uid("2")
            .threadId("thread-1")
            .role(AgentMessageRole.TOOL)
            .type(AgentMessageType.TOOL_RESULT)
            .toolResult(Map.of("outcome", "ok"))
            .traceId("t1")
            .createdAt(Instant.now())
            .build();

        // When — {"id":"abc"} is 12 chars, {"outcome":"ok"} is 16
        long size = ContextSize.charsOf(List.of(call, result));

        // Then
        assertThat(size).isEqualTo(28);
    }

    @Test
    void shouldGrowWithAToolResultSoALargeResultIsVisibleToTheGuard() {
        // Given — the same conversation with a small and then a large tool result
        long small = ContextSize.charsOf(List.of(toolResult("x")));
        long large = ContextSize.charsOf(List.of(toolResult("x".repeat(10_000))));

        // Then — the estimate tracks the result payload, which is what the budget is guarding
        assertThat(large - small).isEqualTo(9_999);
    }

    private static AgentMessage text(final String content) {
        return AgentMessage.builder()
            .uid("uid-" + content)
            .threadId("thread-1")
            .role(AgentMessageRole.USER)
            .type(AgentMessageType.TEXT)
            .content(content)
            .traceId("t1")
            .createdAt(Instant.now())
            .build();
    }

    private static AgentMessage toolResult(final String payload) {
        return AgentMessage.builder()
            .uid("uid-result")
            .threadId("thread-1")
            .role(AgentMessageRole.TOOL)
            .type(AgentMessageType.TOOL_RESULT)
            .toolResult(Map.of("result", payload))
            .traceId("t1")
            .createdAt(Instant.now())
            .build();
    }
}
