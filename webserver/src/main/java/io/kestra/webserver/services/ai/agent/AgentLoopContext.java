package io.kestra.webserver.services.ai.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kestra.webserver.services.ai.agent.ModeProfiles.ResolvedProfile;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentPrincipal;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.micronaut.core.annotation.Nullable;

/**
 * Carries the state of one in-flight turn as it moves through the orchestrator loop. Everything is
 * fixed for the turn except {@code planApproved}, which is an {@link AtomicBoolean} because it is the
 * one flag flipped mid-loop — when the user approves a Plan, the resumed loop must see it as approved.
 */
record AgentLoopContext(
    AgentThread thread,
    String tenant,
    @Nullable AgentPrincipal principal,
    String providerId,
    AgentMode mode,
    ResolvedProfile profile,
    StreamingChatModel model,
    List<ChatMessage> messages,
    String traceId,
    AtomicBoolean planApproved) {
}
