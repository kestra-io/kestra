package org.kestra.core.schedulers;

import io.micronaut.context.ApplicationContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.triggers.PollingTriggerInterface;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.repositories.TriggerRepositoryInterface;
import org.kestra.core.services.FlowListenersService;
import org.kestra.core.utils.Await;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;

@Slf4j
public class Scheduler implements Runnable, AutoCloseable {
    private final ApplicationContext applicationContext;
    private final QueueInterface<Execution> executionQueue;
    private final FlowListenersService flowListenersService;
    private final TriggerRepositoryInterface triggerContextRepository;
    private final ExecutionRepositoryInterface executionRepository;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private Map<String, Trigger> lastTriggers = new ConcurrentHashMap<>();

    @Inject
    public Scheduler(
        ApplicationContext applicationContext,
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        FlowListenersService flowListenersService,
        ExecutionRepositoryInterface executionRepository,
        TriggerRepositoryInterface triggerContextRepository
    ) {
        this.applicationContext = applicationContext;
        this.executionQueue = executionQueue;
        this.flowListenersService = flowListenersService;
        this.triggerContextRepository = triggerContextRepository;
        this.executionRepository = executionRepository;
    }

    @Override
    public void run() {
        ScheduledFuture<?> handle  = executor.scheduleAtFixedRate(
            this::handle,
            0,
            1,
            TimeUnit.SECONDS
        );

        // empty cache on any flow changes
        this.flowListenersService.listen(flows -> {
            synchronized (this) {
                lastTriggers = new ConcurrentHashMap<>();
            }
        });

        // look at exception on the main thread
        Thread thread = new Thread(
            () -> {
                Await.until(handle::isDone);

                try {
                    handle.get();
                } catch (ExecutionException | InterruptedException e) {
                    log.error("Executor fatal exception", e);
                    applicationContext.close();
                    Runtime.getRuntime().exit(1);
                }
            },
            "executor-listener"
        );

        thread.start();
    }

    private synchronized void handle() {
        List<FlowWithTrigger> schedulable = this.flowListenersService
            .getFlows()
            .stream()
            .filter(flow -> flow.getTriggers() != null && flow.getTriggers().size() > 0)
            .flatMap(flow -> flow.getTriggers().stream().map(trigger -> new FlowWithTrigger(flow, trigger)))
            .filter(flowWithTrigger -> flowWithTrigger.getTrigger() instanceof PollingTriggerInterface)
            .collect(Collectors.toList());

        if (log.isTraceEnabled()) {
            log.trace(
                "Scheduler next iteration with {} schedulables of {} flows",
                schedulable.size(),
                this.flowListenersService.getFlows().size()
            );
        }

        schedulable
            .stream()
            .map(flowWithTrigger -> FlowWithPollingTrigger.builder()
                .flow(flowWithTrigger.getFlow())
                .trigger((PollingTriggerInterface) flowWithTrigger.getTrigger())
                .triggerContext(TriggerContext
                    .builder()
                    .namespace(flowWithTrigger.getFlow().getNamespace())
                    .flowId(flowWithTrigger.getFlow().getId())
                    .flowRevision(flowWithTrigger.getFlow().getRevision())
                    .triggerId(flowWithTrigger.getTrigger().getId())
                    .date(now())
                    .build()
                )
                .build()
            )
            .peek(this::getLastTrigger)
            .filter(this::isExecutionNotRunning)
            .map(f -> {
                synchronized (this) {
                    if (!lastTriggers.containsKey(f.getTriggerContext().uid())) {
                        return null;
                    }

                    return FlowWithPollingTriggerNextDate.of(
                        f,
                        f.getTrigger().nextDate(Optional.of(lastTriggers.get(f.getTriggerContext().uid())))
                    );
                }
            })
            .filter(Objects::nonNull)
            .map(this::evaluatePollingTrigger)
            .filter(Objects::nonNull)
            .peek(this::log)
            .peek(this::saveLastTrigger)
            .map(ExecutionWithTrigger::getExecution)
            .forEach(executionQueue::emit);
    }

    private boolean isExecutionNotRunning(FlowWithPollingTrigger f) {
        Trigger lastTrigger = lastTriggers.get(f.getTriggerContext().uid());

        if (lastTrigger.getExecutionId() == null) {
            return true;
        }

        Optional<Execution> execution = executionRepository.findById(lastTrigger.getExecutionId());

        // indexer hasn't received the execution, we skip
        if (execution.isEmpty()) {
            log.warn("Execution '{}' for flow '{}.{}' is not found, schedule is blocked",
                lastTrigger.getExecutionId(),
                lastTrigger.getNamespace(),
                lastTrigger.getFlowId()
            );

            return false;
        }

        // execution is terminated, we can do other backfill
        if (execution.get().getState().isTerninated()) {
            return true;
        }

        if (log.isDebugEnabled()) {
            log.debug("Execution '{}' for flow '{}.{}' is still '{}', waiting for next backfill",
                lastTrigger.getExecutionId(),
                lastTrigger.getNamespace(),
                lastTrigger.getFlowId(),
                execution.get().getState().getCurrent()
            );
        }

        return false;
    }

    private void log(ExecutionWithTrigger executionWithTrigger) {
        log.info(
            "Schedule execution '{}' for flow '{}.{}' started at '{}' for trigger [{}]",
            executionWithTrigger.getExecution().getId(),
            executionWithTrigger.getExecution().getNamespace(),
            executionWithTrigger.getExecution().getFlowId(),
            executionWithTrigger.getTriggerContext().getDate(),
            executionWithTrigger.getTriggerContext().getTriggerId()
        );
    }

    private void getLastTrigger(FlowWithPollingTrigger f) {
        lastTriggers.computeIfAbsent(
            f.getTriggerContext().uid(),
            s -> triggerContextRepository
                .findLast(f.getTriggerContext())
                // we don't find, so never started execution, create an trigger context with next date.
                // this allow some edge case when the evaluation loop of schedulers will change second
                // between start and end
                .orElseGet(() -> {
                    ZonedDateTime nextDate = f.getTrigger().nextDate(Optional.empty());
                    ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

                    return Trigger.builder()
                            .date(nextDate.toEpochSecond() < now.toEpochSecond() ? nextDate : now)
                            .flowId(f.getFlow().getId())
                            .flowRevision(f.getFlow().getRevision())
                            .namespace(f.getFlow().getNamespace())
                            .triggerId(f.getTriggerContext().getTriggerId())
                            .build();
                    }
                )
        );
    }

    private void saveLastTrigger(ExecutionWithTrigger executionWithTrigger) {
        Trigger trigger = Trigger.of(
            executionWithTrigger.getTriggerContext(),
            executionWithTrigger.getExecution()
        );

        this.lastTriggers.put(executionWithTrigger.getTriggerContext().uid(), trigger);
        this.triggerContextRepository.save(trigger);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
    }

    private ExecutionWithTrigger evaluatePollingTrigger(FlowWithPollingTrigger flowWithTrigger) {
        Optional<Execution> evaluate = flowWithTrigger.getTrigger().evaluate(flowWithTrigger.getTriggerContext());

        if (log.isDebugEnabled() && evaluate.isEmpty()) {
            log.debug("Empty evaluation for flow '{}.{}' for date '{}, waiting !",
                flowWithTrigger.getFlow().getNamespace(),
                flowWithTrigger.getFlow().getId(),
                flowWithTrigger.getTriggerContext().getDate()
            );
        }

        if (evaluate.isEmpty()) {
            return null;
        }

        return new ExecutionWithTrigger(
            evaluate.get(),
            flowWithTrigger.getTriggerContext()
        );
    }

    @Override
    public void close() {
        this.executor.shutdown();
    }

    @SuperBuilder
    @Getter
    @NoArgsConstructor
    private static class FlowWithPollingTrigger {
        private Flow flow;
        private PollingTriggerInterface trigger;
        private TriggerContext triggerContext;
    }

    @SuperBuilder
    @Getter
    @NoArgsConstructor
    private static class FlowWithPollingTriggerNextDate extends FlowWithPollingTrigger {
        private ZonedDateTime next;

        public static FlowWithPollingTriggerNextDate of (FlowWithPollingTrigger f, ZonedDateTime next) {
            return FlowWithPollingTriggerNextDate.builder()
                .flow(f.getFlow())
                .trigger(f.getTrigger())
                .triggerContext(TriggerContext.builder()
                    .namespace(f.getTriggerContext().getNamespace())
                    .flowId(f.getTriggerContext().getFlowId())
                    .flowRevision(f.getTriggerContext().getFlowRevision())
                    .triggerId(f.getTriggerContext().getTriggerId())
                    .date(next)
                    .build()
                )
                .next(next)
                .build();
        }
    }

    @AllArgsConstructor
    @Getter
    private static class FlowWithTrigger {
        private final Flow flow;
        private final AbstractTrigger trigger;
    }

    @AllArgsConstructor
    @Getter
    private static class ExecutionWithTrigger {
        private final Execution execution;
        private final TriggerContext triggerContext;
    }
}
