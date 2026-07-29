package io.kestra.webserver.services.ai.agent;

import java.util.Map;

import io.kestra.core.ai.agent.models.AgentPrincipal;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.LangChain4jManaged;
import io.micronaut.core.annotation.Nullable;

/**
 * The per-call context a {@code @Tool} method runs against: the caller's tenant and principal, and the
 * turn's provider and conversation.
 *
 * <p>
 * {@link Context} is a langchain4j {@link LangChain4jManaged managed argument}: {@link #into(Context)}
 * places it in the {@link InvocationContext}'s managed parameters (set by
 * {@link io.kestra.webserver.services.ai.agent.tool.ToolCatalog#dispatch}), and
 * {@code DefaultToolExecutor} injects it <em>by type</em> into any {@code @Tool} method that declares
 * a {@link Context} parameter. langchain4j also omits managed parameters from the generated tool
 * schema, so the context never reaches the model — and nothing is bound to the executor thread.
 * </p>
 */
public final class AgentCallContext {

    private AgentCallContext() {
    }

    public record Context(
        String tenant,
        @Nullable AgentPrincipal principal,
        @Nullable String providerId,
        @Nullable String conversationId) implements LangChain4jManaged {

        public static Context ofTenant(final String tenant) {
            return new Context(tenant, null, null, null);
        }
    }

    /**
     * Build an {@link InvocationContext} carrying the context as a managed argument for a single tool
     * dispatch; {@code DefaultToolExecutor} injects it into the tool method's {@link Context} parameter.
     */
    public static InvocationContext into(final Context context) {
        Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managed = Map.of(Context.class, context);
        return InvocationContext.builder().managedParameters(managed).build();
    }
}
