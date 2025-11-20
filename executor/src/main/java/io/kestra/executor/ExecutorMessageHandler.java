package io.kestra.executor;

import java.util.Optional;

/**
 * Executor queue message handler that may return an {@link ExecutorContext} for updating the current execution.
 *
 * @see MessageHandler
 *
 * @param <T> the message type, this message should be tied to execution processing.
 */
public interface ExecutorMessageHandler<T> {
    /**
     * Handle a message then return an {@link ExecutorContext} if the current execution must be updated.
     *
     * @implNote implementors usually start by locking the current execution ,then process the message to avoid any concurrency issue.
     */
    Optional<ExecutorContext> handle(T message);
}
