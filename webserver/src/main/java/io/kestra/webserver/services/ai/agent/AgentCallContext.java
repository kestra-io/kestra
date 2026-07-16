package io.kestra.webserver.services.ai.agent;

import java.util.function.Consumer;

import io.kestra.webserver.services.ai.agent.domain.AgentPrincipal;
import io.kestra.webserver.services.ai.agent.domain.ArtefactDraft;

import io.micronaut.core.annotation.Nullable;

/**
 * Per-dispatch context bound to the executor thread while a tool call runs, so {@code @Tool} methods
 * can read the caller's tenant and principal, the turn's provider and conversation, and publish
 * artefact drafts back to the turn without depending on the orchestrator.
 */
public final class AgentCallContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private AgentCallContext() {
    }

    public record Context(
        String tenant,
        @Nullable AgentPrincipal principal,
        @Nullable String providerId,
        @Nullable String conversationId,
        @Nullable Consumer<ArtefactDraft> draftPublisher) {
        public static Context ofTenant(final String tenant) {
            return new Context(tenant, null, null, null, null);
        }
    }

    public static void set(final Context context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Context require() {
        Context context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("No agent call context bound to this thread");
        }
        return context;
    }

    /**
     * The effective tenant a tool call runs against: the explicitly requested {@code tenantId} if the
     * caller provided one, otherwise the caller's own (conversation) tenant. This is plumbing only —
     * it performs no authorization. On a single-tenant surface the parameter is hidden from the tool
     * spec and this always returns the caller's tenant; a surface with multiple tenants exposes the
     * parameter, and its tool implementations validate the returned tenant before acting on it.
     */
    public static String resolveTenant(@Nullable final String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return require().tenant();
        }
        return tenantId;
    }

    public static void publishDraft(final ArtefactDraft draft) {
        Consumer<ArtefactDraft> publisher = require().draftPublisher();
        if (publisher == null) {
            throw new IllegalStateException("No draft publisher bound to this agent call context");
        }
        publisher.accept(draft);
    }
}
