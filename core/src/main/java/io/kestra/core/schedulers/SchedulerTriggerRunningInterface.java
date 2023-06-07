package io.kestra.core.schedulers;

import io.kestra.core.models.triggers.TriggerContext;

import java.time.ZonedDateTime;

public interface SchedulerTriggerRunningInterface {
    void addRunningTrigger(TriggerContext triggerContext, ZonedDateTime date);

    boolean isTriggerRunning(TriggerContext triggerContext);

    void removeRunningTrigger(TriggerContext triggerContext);
}
