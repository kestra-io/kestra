package io.kestra.webserver.services.ai.agent.tool;

import java.util.function.Supplier;

import io.kestra.webserver.services.ai.agent.AgentCallContext;

/**
 * Wraps every tool call, so a replacement can expose the caller of {@code context} to the services the
 * tool reaches: tools run outside the HTTP request that started the turn.
 */
public interface AgentToolCallScope {
    <T> T call(AgentCallContext.Context context, Supplier<T> toolCall);
}
