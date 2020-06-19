package org.kestra.core.schedulers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.services.FlowListenersService;
import org.kestra.core.utils.ThreadMainFactoryBuilder;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Named;

@Slf4j
public class Scheduler implements Runnable, AutoCloseable {
    private final QueueInterface<Execution> executionQueue;
    private final FlowListenersService flowListenersService;
    private final ScheduledExecutorService executor;

    @Inject
    public Scheduler(
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        FlowListenersService flowListenersService,
        ThreadMainFactoryBuilder threadFactoryBuilder
    ) {
        this.executionQueue = executionQueue;
        this.flowListenersService = flowListenersService;
        this.executor = Executors.newScheduledThreadPool(1,
            threadFactoryBuilder.build("scheduler-%d")
        );
    }


    @Override
    public void run() {
        final AtomicReference<ZonedDateTime> currentDate = new AtomicReference<>(now());
        
        executor.scheduleAtFixedRate(
            () -> {
                try {
                    while (currentDate.get().toEpochSecond() <= now().toEpochSecond()) {
                        runSchedules(currentDate.get());
                        currentDate.set(currentDate.get().plus(Duration.ofSeconds(1)));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            },
            0,
            1,
            TimeUnit.SECONDS
        );
    }

    ZonedDateTime now() {
        return ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private void runSchedules(ZonedDateTime now) {
        if (log.isDebugEnabled()) {
            log.debug("Schedule lookup for '{}'", now.toString());
        }

        this.flowListenersService
            .getFlows()
            .stream()
            .filter(flow -> flow.getTriggers() != null && flow.getTriggers().size() > 0)
            .flatMap(flow -> flow.getTriggers().stream().map(trigger -> new FlowWithTrigger(flow, trigger)))
            .filter(flowWithTrigger -> flowWithTrigger.getTrigger() instanceof PollingTriggerInterface)
            .map(flowWithTrigger -> new FlowWithPollingTrigger(flowWithTrigger.getFlow(), (PollingTriggerInterface) flowWithTrigger.getTrigger()))
            .map(flowWithTrigger -> this.evaluate(flowWithTrigger, now))
            .filter(Objects::nonNull)
            .peek(execution -> {
                log.info(
                    "Schedule execution '{}' for flow '{}.{}' started at '{}' for trigger [{}]",
                    execution.getExecution().getId(),
                    execution.getExecution().getNamespace(),
                    execution.getExecution().getFlowId(),
                    now,
                    execution.getTrigger().toLog()
                );
            })
            .map(ExecutionWithTrigger::getExecution)
            .forEach(executionQueue::emit);
    }

    private ExecutionWithTrigger evaluate(FlowWithPollingTrigger flowWithTrigger, ZonedDateTime now) {
        TriggerContext triggerContext = TriggerContext
            .builder()
            .flow(flowWithTrigger.getFlow())
            .date(now)
            .build();

        Optional<Execution> evaluate = flowWithTrigger.getTrigger().evaluate(triggerContext);

        if (evaluate.isEmpty()) {
            return null;
        }

        return new ExecutionWithTrigger(
            evaluate.get(),
            flowWithTrigger.getTrigger()
        );
    }

    @Override
    public void close() {
        this.executor.shutdown();
    }

    @AllArgsConstructor
    @Getter
    private static class FlowWithPollingTrigger {
        private final Flow flow;
        private final PollingTriggerInterface trigger;
    }

    @AllArgsConstructor
    @Getter
    private static class FlowWithTrigger {
        private final Flow flow;
        private final Trigger trigger;
    }

    @AllArgsConstructor
    @Getter
    private static class ExecutionWithTrigger {
        private final Execution execution;
        private final PollingTriggerInterface trigger;
    }
}
