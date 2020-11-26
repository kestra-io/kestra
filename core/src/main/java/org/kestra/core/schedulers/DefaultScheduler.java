package org.kestra.core.schedulers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.services.FlowListenersInterface;

import javax.inject.Inject;

@Slf4j
@Prototype
public class DefaultScheduler extends AbstractScheduler {
    @Inject
    public DefaultScheduler(
        ApplicationContext applicationContext,
        FlowListenersInterface flowListeners,
        SchedulerExecutionStateInterface executionState,
        SchedulerTriggerStateInterface triggerState
    ) {
        super(applicationContext, flowListeners);
        this.triggerState = triggerState;
        this.executionState = executionState;
    }
}
