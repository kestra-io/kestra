package io.kestra.core.scheduler.model;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.Schedulable;

/**
 * Types of triggers supported by the scheduler.
 */
public enum TriggerType {
    SCHEDULE,
    POLLING,
    REALTIME;

    /**
     * Resolves the trigger type for the given trigger class.
     * 
     * @param trigger the trigger object.
     * @return the {@link TriggerType} of {@code null} if the trigger is not supported.
     */
    public static TriggerType from(final AbstractTrigger trigger) {
        return switch (trigger) {
            case Schedulable unused -> SCHEDULE;
            case RealtimeTriggerInterface unused -> REALTIME;
            case PollingTriggerInterface unused -> POLLING;
            case null, default -> null;
        };
    }
}
