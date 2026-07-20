package io.kestra.webserver.services.ai.agent.internals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentMessageRole;
import io.kestra.core.ai.agent.models.AgentMessageType;

import static org.assertj.core.api.Assertions.assertThat;

class TurnWindowTest {

    @Test
    void shouldReturnInputWhenWithinLimit() {
        // Given — three turns
        List<AgentMessage> history = List.of(
            message("t1"), message("t1"), message("t2"), message("t3")
        );

        // When / Then — 3 turns kept, nothing dropped
        assertThat(TurnWindow.lastNTurns(history, 3)).isSameAs(history);
        assertThat(TurnWindow.lastNTurns(history, 5)).isSameAs(history);
    }

    @Test
    void shouldDisableWindowingWhenLimitNotPositive() {
        // Given
        List<AgentMessage> history = List.of(message("t1"), message("t2"), message("t3"));

        // When / Then
        assertThat(TurnWindow.lastNTurns(history, 0)).isSameAs(history);
        assertThat(TurnWindow.lastNTurns(history, -1)).isSameAs(history);
    }

    @Test
    void shouldKeepOnlyTheMostRecentTurnsAndAllTheirMessages() {
        // Given — turn t1 has 2 messages, t2 has 1, t3 has 3
        AgentMessage t1a = message("t1");
        AgentMessage t1b = message("t1");
        AgentMessage t2a = message("t2");
        AgentMessage t3a = message("t3");
        AgentMessage t3b = message("t3");
        AgentMessage t3c = message("t3");
        List<AgentMessage> history = List.of(t1a, t1b, t2a, t3a, t3b, t3c);

        // When — keep the last 2 turns (t2, t3)
        List<AgentMessage> windowed = TurnWindow.lastNTurns(history, 2);

        // Then — every message of the kept turns, none of the evicted turn t1
        assertThat(windowed).containsExactly(t2a, t3a, t3b, t3c);
    }

    @Test
    void shouldNeverSplitATurnAtTheWindowBoundary() {
        // Given — a turn (t2) that carries a tool call and its result together
        AgentMessage t1 = message("t1");
        AgentMessage t2Call = message("t2", AgentMessageType.TOOL_CALL);
        AgentMessage t2Result = message("t2", AgentMessageType.TOOL_RESULT);
        List<AgentMessage> history = List.of(t1, t2Call, t2Result);

        // When — keep only the last turn
        List<AgentMessage> windowed = TurnWindow.lastNTurns(history, 1);

        // Then — the whole turn is kept; the call/result pair is never split
        assertThat(windowed).containsExactly(t2Call, t2Result);
    }

    private static AgentMessage message(final String traceId) {
        return message(traceId, AgentMessageType.TEXT);
    }

    private static AgentMessage message(final String traceId, final AgentMessageType type) {
        return AgentMessage.builder()
            .uid(traceId + "-" + type)
            .threadId("thread-1")
            .role(AgentMessageRole.USER)
            .type(type)
            .traceId(traceId)
            .createdAt(Instant.now())
            .build();
    }
}
