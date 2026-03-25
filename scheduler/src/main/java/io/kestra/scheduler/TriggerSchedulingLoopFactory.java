package io.kestra.scheduler;

import java.time.Clock;

import io.kestra.core.metrics.MetricRegistry;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory class for constructing new {@link TriggerSchedulingLoop instances}.
 */
@Singleton
public class TriggerSchedulingLoopFactory {

    // Services
    private final TriggerScheduler triggerScheduler;
    private final TriggerEventHandler triggerEventHandler;
    private final MetricRegistry metricRegistry;

    @Inject
    public TriggerSchedulingLoopFactory(TriggerScheduler triggerScheduler,
        TriggerEventHandler triggerEventHandler,
        MetricRegistry metricRegistry) {
        this.triggerScheduler = triggerScheduler;
        this.triggerEventHandler = triggerEventHandler;
        this.metricRegistry = metricRegistry;
    }

    /**
     * Creates a new {@link TriggerSchedulingLoop} with the given id and clock.
     *
     * @param schedulingLoopId the ID of the scheduling loop.
     * @param clock the clock to be used by the scheduling loop.
     * @return a new {@link TriggerSchedulingLoop}
     */
    public TriggerSchedulingLoop create(int schedulingLoopId, Clock clock) {
        return new TriggerSchedulingLoop(
            schedulingLoopId,
            triggerScheduler,
            triggerEventHandler,
            metricRegistry,
            clock
        );
    }
}
