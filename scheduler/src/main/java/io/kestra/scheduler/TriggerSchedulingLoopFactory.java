package io.kestra.scheduler;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * Factory class for constructing new {@link TriggerSchedulingLoop instances}.
 */
@Singleton
public class TriggerSchedulingLoopFactory {
    
    // Services
    private final TriggerScheduler triggerScheduler;
    private final TriggerEventHandler triggerEventHandler;
    
    @Inject
    public TriggerSchedulingLoopFactory(TriggerScheduler triggerScheduler,
                                        TriggerEventHandler triggerEventHandler) {
        this.triggerScheduler = triggerScheduler;
        this.triggerEventHandler = triggerEventHandler;
    }
    
    /**
     * Creates a new {@link TriggerSchedulingLoop} with the given id and clock.
     *
     * @param schedulingLoopId the ID of the scheduling loop.
     * @param clock            the clock to be used by the scheduling loop.
     * @return a new {@link TriggerSchedulingLoop}
     */
    public TriggerSchedulingLoop create(int schedulingLoopId, Clock clock) {
        return new TriggerSchedulingLoop(
            schedulingLoopId,
            triggerScheduler,
            triggerEventHandler,
            clock
        );
    }
}
