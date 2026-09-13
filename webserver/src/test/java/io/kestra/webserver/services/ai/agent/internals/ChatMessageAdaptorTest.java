package io.kestra.webserver.services.ai.agent.internals;

import java.time.Instant;
import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentMessageRole;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.ai.agent.models.AgentThinking;
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

    @Test
    void shouldReattachReasoningStateWhenProjectingToolCall() {
        // Given — a persisted tool call carrying the provider's reasoning state (thinking text + opaque
        // signature); projection must rebuild the AiMessage with both so the cross-turn replay stays valid
        AgentToolCall call = AgentToolCall.platform("c1", "search-docs", null, Map.of("q", "trigger"),
            new AgentThinking("let me search the docs", "sig-abc", null));
        AgentMessage toolCallRow = AgentMessage.builder()
            .uid("tc-1")
            .threadId("thread-1")
            .role(AgentMessageRole.ASSISTANT)
            .type(AgentMessageType.TOOL_CALL)
            .toolCall(call)
            .traceId("t1")
            .createdAt(Instant.now())
            .build();

        // When
        List<ChatMessage> projected = ChatMessageAdaptor.project(List.of(toolCallRow));

        // Then — the tool request is preserved and the reasoning state is re-attached under the keys
        // LangChain4j reads back when sending the call to the provider
        assertThat(projected).hasSize(1).first().isInstanceOf(AiMessage.class);
        AiMessage ai = (AiMessage) projected.getFirst();
        assertThat(ai.hasToolExecutionRequests()).isTrue();
        assertThat(ai.toolExecutionRequests()).first().extracting(ToolExecutionRequest::name).isEqualTo("search-docs");
        assertThat(ai.thinking()).isEqualTo("let me search the docs");
        assertThat(ai.attribute(ChatMessageAdaptor.THINKING_SIGNATURE_KEY, String.class)).isEqualTo("sig-abc");
        assertThat(ChatMessageAdaptor.thinkingOf(ai)).isEqualTo(new AgentThinking("let me search the docs", "sig-abc", null));
    }

    @Test
    void shouldOmitReasoningStateWhenToolCallHasNone() {
        // Given — a legacy/non-thinking-provider tool call with no reasoning state
        AgentToolCall call = AgentToolCall.platform("c1", "search-docs", null, Map.of("q", "trigger"));
        AgentMessage toolCallRow = AgentMessage.builder()
            .uid("tc-2")
            .threadId("thread-1")
            .role(AgentMessageRole.ASSISTANT)
            .type(AgentMessageType.TOOL_CALL)
            .toolCall(call)
            .traceId("t1")
            .createdAt(Instant.now())
            .build();

        // When
        AiMessage ai = (AiMessage) ChatMessageAdaptor.project(List.of(toolCallRow)).get(0);

        // Then — nothing spurious is attached, so nothing is sent back to the provider
        assertThat(ai.thinking()).isNull();
        assertThat(ai.attribute(ChatMessageAdaptor.THINKING_SIGNATURE_KEY, String.class)).isNull();
        assertThat(ChatMessageAdaptor.thinkingOf(ai)).isNull();
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
