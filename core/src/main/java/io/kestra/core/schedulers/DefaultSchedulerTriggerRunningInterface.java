package io.kestra.core.schedulers;

import io.kestra.core.models.triggers.TriggerContext;
import jakarta.inject.Singleton;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class DefaultSchedulerTriggerRunningInterface implements SchedulerTriggerRunningInterface {
    private final Map<String, ZonedDateTime> runningTriggers = new ConcurrentHashMap<>();

    @Override
    public synchronized void addRunningTrigger(TriggerContext triggerContext, ZonedDateTime lockDate) {
        this.runningTriggers.put(triggerContext.uid(), lockDate);
    }

    @Override
    public synchronized boolean isTriggerRunning(TriggerContext triggerContext) {
        return this.runningTriggers.containsKey(triggerContext.uid());
    }

    @Override
    public synchronized void removeRunningTrigger(TriggerContext triggerContext) {
        if (this.runningTriggers.remove(triggerContext.uid()) == null) {
            throw new IllegalStateException("Can't remove trigger '" + triggerContext.uid() + "' from running");
        }
    }
}
