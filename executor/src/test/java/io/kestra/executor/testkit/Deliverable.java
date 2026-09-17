package io.kestra.executor.testkit;

/**
 * A recording queue the real executor subscribed to: emitted messages wait here until a test
 * delivers them, one at a time, to the executor's own subscriber. Never delivering inline is what
 * preserves production's "emit after commit" semantics without threads.
 */
public interface Deliverable {
    String queueName();

    boolean isSubscribed();

    boolean hasPending();

    Object peekPending();

    void deliverNext();
}
