package io.kestra.core.scheduler.events;

import java.time.Instant;

import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.TriggerId;

/**
 * A trigger the scheduler does not evaluate created an execution.
 * <p>
 * Webhook, MCP tool, flow and asset event triggers are fired by the webserver or the executor, so the
 * scheduler never observes them running: without this event their state would report them as having
 * never fired.
 */
public record UnscheduledTriggerFired(
    TriggerId id,
    String executionId,
    Instant timestamp,
    EventId eventId) implements TriggerEvent {

    public UnscheduledTriggerFired(TriggerId id, String executionId) {
        this(id, executionId, Instant.now(), EventId.create());
    }

    /**
     * Builds the event for an execution a trigger has just created.
     *
     * @param execution the execution, which must carry the trigger that created it.
     */
    public static UnscheduledTriggerFired of(Execution execution) {
        return new UnscheduledTriggerFired(
            TriggerId.of(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getTrigger().getId()),
            execution.getId()
        );
    }
}
