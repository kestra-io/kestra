package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.services.FlowListenersInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Slf4j
@Singleton
public class DefaultScheduler extends AbstractScheduler {
    private final Map<String, Trigger> watchingTrigger = new ConcurrentHashMap<>();

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
        this.isReady = true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        flowListeners.run();

        QueueInterface<Execution> executionQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));
        QueueInterface<Trigger> triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));

        executionQueue.receive(execution -> {
            if (execution.getState().getCurrent().isTerninated() && this.watchingTrigger.containsKey(execution.getId())) {
                Trigger trigger = watchingTrigger.get(execution.getId());
                triggerQueue.emit(trigger.resetExecution());
                triggerState.save(trigger.resetExecution());
            }
        });

        triggerQueue.receive(trigger -> {
            if (trigger.getExecutionId() != null) {
                this.watchingTrigger.put(trigger.getExecutionId(), trigger);
            }
        });

        super.run();
    }
}
