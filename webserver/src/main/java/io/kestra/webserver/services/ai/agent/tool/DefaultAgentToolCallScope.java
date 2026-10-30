package io.kestra.webserver.services.ai.agent.tool;

import java.util.function.Supplier;

import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Singleton;

@Singleton
public class DefaultAgentToolCallScope implements AgentToolCallScope {
    @Override
    public <T> T call(final AgentCallContext.Context context, final Supplier<T> toolCall) {
        return toolCall.get();
    }
}
