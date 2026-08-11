package io.kestra.executor;

/**
 * Executor queue message handler.
 *
 * @see ExecutorMessageHandler
 *
 * @param <T> the message type, this message should not be tied to execution processing.
 */
public interface MessageHandler<T> {
    /**
     * Handle a message.
     */
    void handle(T message);
}
