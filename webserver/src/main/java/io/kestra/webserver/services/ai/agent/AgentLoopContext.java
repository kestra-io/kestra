package io.kestra.webserver.services.ai.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.webserver.services.ai.agent.ModeProfiles.ResolvedProfile;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.micronaut.core.annotation.Nullable;

/**
 * Carries the state of one in-flight turn as it moves through the orchestrator loop. Everything is
 * fixed for the turn except the two mutable counters flipped mid-loop: {@code planApproved} (when the
 * user approves a Plan, the resumed loop must see it as approved) and {@code toolInvocations} (the
 * running count of sequential tool-calling round-trips this turn, guarded against a runaway loop and
 * preserved across a confirmation suspend/resume).
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
    AtomicBoolean planApproved,
    AtomicInteger toolInvocations) {
}
