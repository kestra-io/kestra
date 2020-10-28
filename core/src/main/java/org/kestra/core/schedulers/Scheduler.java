package org.kestra.core.schedulers;

import com.google.common.util.concurrent.*;
import io.micronaut.context.ApplicationContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.metrics.MetricRegistry;
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
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.services.ConditionService;
import org.kestra.core.services.FlowListenersService;
import org.kestra.core.utils.Await;
import org.kestra.core.utils.ExecutorsUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;

import static org.kestra.core.utils.Rethrow.throwSupplier;

@Slf4j
public class Scheduler implements Runnable, AutoCloseable {
    private final ApplicationContext applicationContext;
    private final QueueInterface<Execution> executionQueue;
    private final FlowListenersService flowListenersService;
    private final TriggerRepositoryInterface triggerContextRepository;
    private final ExecutionRepositoryInterface executionRepository;
    private final RunContextFactory runContextFactory;
    private final MetricRegistry metricRegistry;
    private final ConditionService conditionService;

    private final ScheduledExecutorService scheduleExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ListeningExecutorService cachedExecutor;
    private Map<String, Trigger> lastTriggers = new ConcurrentHashMap<>();
    private final Map<String, ZonedDateTime> lastEvaluate = new ConcurrentHashMap<>();
    private final Map<String, ZonedDateTime> evaluateRunning = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> evaluateRunningCount = new ConcurrentHashMap<>();

    @Inject
    public Scheduler(
        ApplicationContext applicationContext,
        ExecutorsUtils executorsUtils,
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
        this.runContextFactory = applicationContext.getBean(RunContextFactory.class);
        this.metricRegistry = applicationContext.getBean(MetricRegistry.class);
        this.conditionService = applicationContext.getBean(ConditionService.class);

        this.cachedExecutor = MoreExecutors.listeningDecorator(executorsUtils.cachedThreadPool("scheduler-polling"));
    }

    @Override
    public void run() {
        ScheduledFuture<?> handle  = scheduleExecutor.scheduleAtFixedRate(
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

    private void handle() {
        synchronized (this) {
            // get all PollingTriggerInterface from flow
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

            // get all that is ready from evaluation
            List<FlowWithPollingTriggerNextDate> readyForEvaluate = schedulable
                .stream()
                .filter(f -> conditionService.isValid(f.getTrigger(), f.getFlow()))
                .map(flowWithTrigger -> FlowWithPollingTrigger.builder()
                    .flow(flowWithTrigger.getFlow())
                    .trigger(flowWithTrigger.getTrigger())
                    .pollingTrigger((PollingTriggerInterface) flowWithTrigger.getTrigger())
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
                .filter(this::isEvaluationInterval)
                .peek(this::getLastTrigger)
                .filter(this::isExecutionNotRunning)
                .map(f -> {
                    synchronized (this) {
                        if (!lastTriggers.containsKey(f.getTriggerContext().uid())) {
                            return null;
                        }

                        return FlowWithPollingTriggerNextDate.of(
                            f,
                            f.getPollingTrigger().nextDate(Optional.of(lastTriggers.get(f.getTriggerContext().uid())))
                        );
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // submit ready one to cached thread pool
            readyForEvaluate
                .forEach(f -> {
                    this.addToRunning(f.getTriggerContext());

                    ListenableFuture<SchedulerExecutionWithTrigger> result = cachedExecutor
                        .submit(() -> this.evaluatePollingTrigger(f));

                    Futures.addCallback(
                        result,
                        new EvaluateFuture(this, f),
                        cachedExecutor
                    );
                });
        }
    }

    private static class EvaluateFuture implements FutureCallback<SchedulerExecutionWithTrigger> {
        private final Scheduler scheduler;
        private final FlowWithPollingTriggerNextDate flowWithPollingTriggerNextDate;

        public EvaluateFuture(Scheduler scheduler, FlowWithPollingTriggerNextDate flowWithPollingTriggerNextDate) {
            this.scheduler = scheduler;
            this.flowWithPollingTriggerNextDate = flowWithPollingTriggerNextDate;
        }

        @Override
        public void onSuccess(SchedulerExecutionWithTrigger result) {
            scheduler.removeFromRunning(flowWithPollingTriggerNextDate.getTriggerContext());

            Stream.of(result)
                .filter(Objects::nonNull)
                .peek(scheduler::log)
                .peek(scheduler::saveLastTrigger)
                .map(SchedulerExecutionWithTrigger::getExecution)
                .forEach(scheduler.executionQueue::emit);
        }

        @Override
        public void onFailure(Throwable e) {
            scheduler.removeFromRunning(flowWithPollingTriggerNextDate.getTriggerContext());

            log.warn(
                "Evaluate failed for flow '{}.{}' started at '{}' for trigger [{}] with error '{}",
                flowWithPollingTriggerNextDate.getFlow().getNamespace(),
                flowWithPollingTriggerNextDate.getFlow().getId(),
                flowWithPollingTriggerNextDate.getTriggerContext().getDate(),
                flowWithPollingTriggerNextDate.getTriggerContext().getTriggerId(),
                e.getMessage(),
                e
            );
        }
    }

    private void addToRunning(TriggerContext triggerContext) {
        synchronized (this) {
            this.evaluateRunningCount.computeIfAbsent(triggerContext.uid(), s -> metricRegistry
                .gauge(MetricRegistry.SCHEDULER_EVALUATE_RUNNING_COUNT, new AtomicInteger(0), metricRegistry.tags(triggerContext)));

            this.evaluateRunning.put(triggerContext.uid(), ZonedDateTime.now());
            this.evaluateRunningCount.get(triggerContext.uid()).addAndGet(1);
        }
    }


    private void removeFromRunning(TriggerContext triggerContext) {
        synchronized (this) {
            if (this.evaluateRunning.remove(triggerContext.uid()) == null) {
                throw new IllegalStateException("Can't remove trigger '" + triggerContext.uid() + "' from running");
            }
            this.evaluateRunningCount.get(triggerContext.uid()).addAndGet(-1);
        }
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

    private void log(SchedulerExecutionWithTrigger executionWithTrigger) {
        metricRegistry
            .counter(MetricRegistry.SCHEDULER_TRIGGER_COUNT, metricRegistry.tags(executionWithTrigger))
            .increment();

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
                    ZonedDateTime nextDate = f.getPollingTrigger().nextDate(Optional.empty());
                    ZonedDateTime now = ZonedDateTime.now();

                    return Trigger.builder()
                            .date(nextDate.compareTo(now) < 0 ? nextDate : now)
                            .flowId(f.getFlow().getId())
                            .flowRevision(f.getFlow().getRevision())
                            .namespace(f.getFlow().getNamespace())
                            .triggerId(f.getTriggerContext().getTriggerId())
                            .build();
                    }
                )
        );
    }

    private boolean isEvaluationInterval(FlowWithPollingTrigger flowWithPollingTrigger) {
        String key = flowWithPollingTrigger.getTriggerContext().uid();
        ZonedDateTime now = ZonedDateTime.now();

        if (this.evaluateRunning.containsKey(key)) {
            return false;
        }

        if (!this.lastEvaluate.containsKey(key)) {
            this.lastEvaluate.put(key, now);
            return true;
        }

        boolean result = this.lastEvaluate.get(key)
            .plus(flowWithPollingTrigger.getPollingTrigger().getInterval())
            .compareTo(now) < 0;

        if (result) {
            this.lastEvaluate.put(key, now);
        }

        return result;
    }

    private void saveLastTrigger(SchedulerExecutionWithTrigger executionWithTrigger) {
        Trigger trigger = Trigger.of(
            executionWithTrigger.getTriggerContext(),
            executionWithTrigger.getExecution()
        );

        this.lastTriggers.put(executionWithTrigger.getTriggerContext().uid(), trigger);
        this.triggerContextRepository.save(trigger);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private SchedulerExecutionWithTrigger evaluatePollingTrigger(FlowWithPollingTrigger flowWithTrigger) throws Exception {
        Optional<Execution> evaluate = this.metricRegistry
            .timer(MetricRegistry.SCHEDULER_EVALUATE_DURATION, metricRegistry.tags(flowWithTrigger.getTriggerContext()))
            .record(throwSupplier(() -> flowWithTrigger.getPollingTrigger().evaluate(
                runContextFactory.of(flowWithTrigger.getFlow(), flowWithTrigger.getTrigger()),
                flowWithTrigger.getTriggerContext()
            )));

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

        return new SchedulerExecutionWithTrigger(
            evaluate.get(),
            flowWithTrigger.getTriggerContext()
        );
    }

    @Override
    public void close() {
        this.scheduleExecutor.shutdown();
    }

    @SuperBuilder
    @Getter
    @NoArgsConstructor
    private static class FlowWithPollingTrigger {
        private Flow flow;
        private AbstractTrigger trigger;
        private PollingTriggerInterface pollingTrigger;
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
                .pollingTrigger(f.getPollingTrigger())
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
}
