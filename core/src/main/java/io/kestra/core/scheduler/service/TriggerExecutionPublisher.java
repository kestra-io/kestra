package io.kestra.core.scheduler.service;

import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;

/**
 * Interface for publishing trigger execution events.
 */
public interface TriggerExecutionPublisher {

    /**
     * Publish the execution produced by a trigger evaluation.
     *
     * @param triggerId  the trigger identifier providing the tenant, namespace and flow.
     * @param evaluation the trigger evaluation result to publish.
     */
    void send(final TriggerId triggerId, final TriggerEvaluationResult evaluation);
}
