package io.kestra.webserver.services.ai.agent;

import java.util.function.Consumer;

import io.kestra.webserver.services.ai.agent.domain.AgentPrincipal;
import io.kestra.webserver.services.ai.agent.domain.ArtefactDraft;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import io.micronaut.core.annotation.Nullable;

/**
 * The per-call context a {@code @Tool} method runs against: the caller's tenant and principal, the
 * turn's provider and conversation, and the channel to publish artefact drafts back to the turn.
 *
 * <p>
 * It travels to the tool through langchain4j's {@link InvocationContext} (set by
 * {@link io.kestra.webserver.services.ai.agent.tool.ToolCatalog#dispatch} and injected by
 * {@code DefaultToolExecutor} into the tool method's {@link InvocationContext} argument), not a
 * thread-local — so nothing is bound to the executor thread. A tool reads it with
 * {@link #from(InvocationContext)}.
 * </p>
 */
public final class AgentCallContext {
    private static final String KEY = AgentCallContext.class.getName();

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

        public void publishDraft(final ArtefactDraft draft) {
            if (draftPublisher == null) {
                throw new IllegalStateException("No draft publisher bound to this agent call context");
            }
            draftPublisher.accept(draft);
        }
    }

    /** Build an {@link InvocationContext} carrying the context for a single tool dispatch. */
    public static InvocationContext into(final Context context) {
        InvocationParameters parameters = new InvocationParameters();
        parameters.put(KEY, context);
        return InvocationContext.builder().invocationParameters(parameters).build();
    }

    /** Read the context a {@code @Tool} method was dispatched with from its injected invocation. */
    public static Context from(final InvocationContext invocationContext) {
        InvocationParameters parameters = invocationContext == null ? null : invocationContext.invocationParameters();
        Context context = parameters == null ? null : parameters.get(KEY);
        if (context == null) {
            throw new IllegalStateException("No agent call context in the invocation");
        }
        return context;
    }
}
