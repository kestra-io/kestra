package io.kestra.webserver.services.ai.agent.dto;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.kestra.webserver.services.ai.agent.domain.AgentMessage;
import io.kestra.webserver.services.ai.agent.domain.AgentMessageRole;
import io.kestra.webserver.services.ai.agent.domain.AgentMessageType;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentScopeBinding;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;
import io.kestra.webserver.services.ai.agent.domain.AgentToolCall;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Nullable;

public final class AgentDtos {
    private AgentDtos() {
    }

    public record CreateThreadRequest(@Nullable AgentMode mode, @Nullable String title, @Nullable AgentScopeBinding scope) {
    }

    public record ChatTurnRequest(
        String prompt,
        @Nullable AgentMode mode,
        @Nullable AgentScopeBinding inFocus,
        @Nullable String providerId
    ) {
    }

    public enum Decision {
        APPROVE,
        REJECT;

        @JsonCreator
        public static Decision fromString(final String value) {
            if (value == null) {
                return null;
            }
            return Decision.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record ConfirmActionRequest(String confirmationId, Decision decision, @Nullable String reason) {
    }

    public record ThreadSummary(
        String uid,
        @Nullable String title,
        AgentMode mode,
        @Nullable AgentScopeBinding scope,
        AgentThreadStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant lastTurnAt
    ) {
        public static ThreadSummary from(final AgentThread thread) {
            return new ThreadSummary(
                thread.uid(), thread.title(), thread.mode(), thread.scope(),
                thread.status(), thread.createdAt(), thread.updatedAt(), thread.lastTurnAt()
            );
        }
    }

    public record MessageView(
        String uid,
        AgentMessageRole role,
        AgentMessageType type,
        @Nullable String content,
        @Nullable AgentToolCall toolCall,
        @Nullable Map<String, Object> toolResult,
        Instant createdAt
    ) {
        public static MessageView from(final AgentMessage message) {
            return new MessageView(
                message.uid(), message.role(), message.type(), message.content(),
                message.toolCall(), message.toolResult(), message.createdAt()
            );
        }
    }

    public record ThreadDetail(
        String uid,
        @Nullable String title,
        AgentMode mode,
        @Nullable AgentScopeBinding scope,
        AgentThreadStatus status,
        List<MessageView> messages
    ) {
        public static ThreadDetail from(final AgentThread thread, final List<AgentMessage> messages) {
            return new ThreadDetail(
                thread.uid(), thread.title(), thread.mode(), thread.scope(), thread.status(),
                messages.stream().map(MessageView::from).toList()
            );
        }
    }
}
