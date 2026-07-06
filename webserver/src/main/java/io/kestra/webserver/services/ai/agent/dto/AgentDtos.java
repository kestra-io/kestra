package io.kestra.webserver.services.ai.agent.dto;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.kestra.webserver.services.ai.agent.domain.Message;
import io.kestra.webserver.services.ai.agent.domain.MessageRole;
import io.kestra.webserver.services.ai.agent.domain.MessageType;
import io.kestra.webserver.services.ai.agent.domain.Mode;
import io.kestra.webserver.services.ai.agent.domain.ScopeBinding;
import io.kestra.webserver.services.ai.agent.domain.Thread;
import io.kestra.webserver.services.ai.agent.domain.ThreadStatus;
import io.kestra.webserver.services.ai.agent.domain.ToolCall;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Nullable;

public final class AgentDtos {
    private AgentDtos() {
    }

    public record CreateThreadRequest(@Nullable Mode mode, @Nullable String title, @Nullable ScopeBinding scope) {
    }

    public record ChatTurnRequest(
        String prompt,
        @Nullable Mode mode,
        @Nullable ScopeBinding inFocus,
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
        Mode mode,
        @Nullable ScopeBinding scope,
        ThreadStatus status,
        Instant createdAt,
        Instant updatedAt,
        @Nullable Instant lastTurnAt
    ) {
        public static ThreadSummary from(final Thread thread) {
            return new ThreadSummary(
                thread.uid(), thread.title(), thread.mode(), thread.scope(),
                thread.status(), thread.createdAt(), thread.updatedAt(), thread.lastTurnAt()
            );
        }
    }

    public record MessageView(
        String uid,
        MessageRole role,
        MessageType type,
        @Nullable String content,
        @Nullable ToolCall toolCall,
        @Nullable Map<String, Object> toolResult,
        Instant createdAt
    ) {
        public static MessageView from(final Message message) {
            return new MessageView(
                message.uid(), message.role(), message.type(), message.content(),
                message.toolCall(), message.toolResult(), message.createdAt()
            );
        }
    }

    public record ThreadDetail(
        String uid,
        @Nullable String title,
        Mode mode,
        @Nullable ScopeBinding scope,
        ThreadStatus status,
        List<MessageView> messages
    ) {
        public static ThreadDetail from(final Thread thread, final List<Message> messages) {
            return new ThreadDetail(
                thread.uid(), thread.title(), thread.mode(), thread.scope(), thread.status(),
                messages.stream().map(MessageView::from).toList()
            );
        }
    }
}
