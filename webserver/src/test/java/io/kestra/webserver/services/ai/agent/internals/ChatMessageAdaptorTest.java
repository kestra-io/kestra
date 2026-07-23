package io.kestra.webserver.services.ai.agent.internals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import java.util.Map;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentMessageRole;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.ai.agent.models.AgentToolCall;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageAdaptorTest {

    @Test
    void shouldSkipEmptyAssistantMessagesWhenProjecting() {
        // Given — a persisted history containing a blank assistant message (e.g. from a prior empty
        // finishReason=STOP response) between two real turns
        List<AgentMessage> history = List.of(
            text(AgentMessageRole.USER, "what is a trigger?"),
            text(AgentMessageRole.ASSISTANT, "A trigger starts a flow."),
            text(AgentMessageRole.USER, "and a task?"),
            text(AgentMessageRole.ASSISTANT, ""),
            text(AgentMessageRole.ASSISTANT, "   ")
        );

        // When
        List<ChatMessage> projected = ChatMessageAdaptor.project(history);

        // Then — the blank/whitespace assistant messages are dropped so they are never replayed as an
        // empty model turn (a documented trigger for empty responses); real messages are kept
        assertThat(projected).hasSize(3);
        assertThat(projected).element(0).isInstanceOf(UserMessage.class);
        assertThat(projected).element(1).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) projected.get(1)).text()).isEqualTo("A trigger starts a flow.");
        assertThat(projected).element(2).isInstanceOf(UserMessage.class);
    }

    @Test
    void shouldFlagErrorToolResultsAsIsErrorWhenProjecting() {
        // Given — a persisted error tool result and a persisted ok tool result
        AgentToolCall call = AgentToolCall.platform("c1", "read-execution", null, Map.of("executionId", "missing"));
        AgentMessage errorResult = toolResult(call, Map.of("outcome", "error", "error", "Execution not found"));
        AgentMessage okResult = toolResult(call, Map.of("outcome", "ok", "result", "done"));

        // When
        ToolExecutionResultMessage projectedError = (ToolExecutionResultMessage) ChatMessageAdaptor.project(java.util.List.of(errorResult)).get(0);
        ToolExecutionResultMessage projectedOk = (ToolExecutionResultMessage) ChatMessageAdaptor.project(java.util.List.of(okResult)).get(0);

        // Then — the error result is flagged isError=true on reload; the ok result is not
        assertThat(projectedError.isError()).isTrue();
        assertThat(projectedOk.isError()).isFalse();
    }

    private static AgentMessage toolResult(final AgentToolCall call, final Map<String, Object> result) {
        return AgentMessage.builder()
            .uid("tr-" + result.hashCode())
            .threadId("thread-1")
            .role(AgentMessageRole.TOOL)
            .type(AgentMessageType.TOOL_RESULT)
            .toolCall(call)
            .toolResult(result)
            .traceId("t1")
            .createdAt(Instant.now())
            .build();
    }

    private static AgentMessage text(final AgentMessageRole role, final String content) {
        return AgentMessage.builder()
            .uid(role + "-" + content.hashCode())
            .threadId("thread-1")
            .role(role)
            .type(AgentMessageType.TEXT)
            .content(content)
            .traceId("t1")
            .createdAt(Instant.now())
            .build();
    }
}
