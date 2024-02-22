package io.kestra.core.schedulers;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Slf4j
@Singleton
//TODO maybe move it to the MemoryRunner ?
public class DefaultScheduler extends AbstractScheduler {
    private final Map<String, Trigger> watchingTrigger = new ConcurrentHashMap<>();

    private final ConditionService conditionService;

    private final FlowRepositoryInterface flowRepository;

    @Inject
    public DefaultScheduler(
        ApplicationContext applicationContext,
        FlowListenersInterface flowListeners,
        SchedulerTriggerStateInterface triggerState
    ) {
        super(applicationContext, flowListeners);
        this.triggerState = triggerState;

        this.conditionService = applicationContext.getBean(ConditionService.class);
        this.flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        QueueInterface<Execution> executionQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.EXECUTION_NAMED));
        QueueInterface<Trigger> triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));

        executionQueue.receive(either -> {
            if (either.isRight()) {
                log.error("Unable to deserialize and execution: {}", either.getRight().getMessage());
                return;
            }

            Execution execution = either.getLeft();
            if (execution.getTrigger() != null) {
                Trigger trigger = Await.until(()  -> watchingTrigger.get(execution.getId()), Duration.ofSeconds(5));
                var flow = flowRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId()).orElse(null);
                if (execution.isDeleted() || conditionService.isTerminatedWithListeners(flow, execution)) {
                    triggerState.update(trigger.resetExecution(execution.getState().getCurrent()));
                    watchingTrigger.remove(execution.getId());
                } else {
                    triggerState.update(Trigger.of(execution, trigger));
                }
            }
        });

        triggerQueue.receive(either -> {
            if (either.isRight()) {
                log.error("Unable to deserialize a trigger: {}", either.getRight().getMessage());
                return;
            }

            Trigger trigger = either.getLeft();
            if (trigger != null && trigger.getExecutionId() != null) {
                this.watchingTrigger.put(trigger.getExecutionId(), trigger);
            }
        });

        super.run();
    }

    @Override
    public void handleNext(List<Flow> flows, ZonedDateTime now, BiConsumer<List<Trigger>, ScheduleContextInterface> consumer) {
        List<Trigger> triggers =  triggerState.findAllForAllTenants().stream().filter(trigger -> trigger.getNextExecutionDate() == null || trigger.getNextExecutionDate().isBefore(now)).toList();
        DefaultScheduleContext schedulerContext = new DefaultScheduleContext();
        consumer.accept(triggers, schedulerContext);
    }
}
