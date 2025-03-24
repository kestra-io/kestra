package io.kestra.core.models;

/**
 * A {@code Pauseable} is a source or destination of events that can be paused (no more events received),
 * then resumed (events are received again).
 */
public interface Pauseable {
    /**
     * Pause a source or destination of events, no more events will be received.
     * If already paused, this is a no-op.
     */
    void pause();

    /**
     * Resume a source or destination of events, events will be received again.
     * If already resumed, this is a no-op.
     */
    void resume();
}
