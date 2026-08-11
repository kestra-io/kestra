package io.kestra.core.scheduler.service;

import io.kestra.core.models.executions.Execution;

/**
 * Interface for publishing trigger execution events.
 */
public interface TriggerExecutionPublisher {

    /**
     * Publish a trigger execution event.
     *
     * @param execution the execution to publish.
     */
    void send(final Execution execution);
}
