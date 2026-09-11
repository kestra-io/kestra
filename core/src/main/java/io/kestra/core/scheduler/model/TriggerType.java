package io.kestra.core.scheduler.model;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.Schedulable;

/**
 * Types of triggers the scheduler holds a state for.
 */
public enum TriggerType {
    SCHEDULE,
    POLLING,
    REALTIME,
    /**
     * A trigger the scheduler stores a state for but never evaluates, because something else fires it:
     * a webhook or MCP call served by the webserver, or a flow trigger evaluated by the executor.
     */
    UNSCHEDULED;

    /**
     * Resolves the trigger type for the given trigger class.
     *
     * @param trigger the trigger object.
     * @return the {@link TriggerType}, or {@code null} if the trigger is {@code null}.
     */
    public static TriggerType from(final AbstractTrigger trigger) {
        return switch (trigger) {
            case null -> null;
            case Schedulable _ -> SCHEDULE;
            case RealtimeTriggerInterface _ -> REALTIME;
            case PollingTriggerInterface _ -> POLLING;
            default -> UNSCHEDULED;
        };
    }

    /**
     * Returns whether the scheduler evaluates a trigger of this type.
     *
     * @param type the trigger type; {@code null} for a state written before {@link #UNSCHEDULED} existed
     *        (the 1.x migration leaves the type unset), which is always a scheduler-evaluated trigger.
     */
    public static boolean isEvaluatedByScheduler(final TriggerType type) {
        return UNSCHEDULED != type;
    }
}
