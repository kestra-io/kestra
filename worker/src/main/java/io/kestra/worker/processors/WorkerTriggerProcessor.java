package io.kestra.worker.processors;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import com.google.common.base.Throwables;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.trace.Tracer;
import io.kestra.core.utils.Logs;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.processors.internals.WorkerTriggerCallable;
import io.kestra.worker.processors.internals.WorkerTriggerRealtimeCallable;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.services.ExecutionKilledManager;

import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;

@Slf4j
public class WorkerTriggerProcessor extends AbstractWorkerJobProcessor<WorkerTrigger> {

    private final Map<String, AtomicInteger> evaluateTriggerRunningCount = new ConcurrentHashMap<>();
    private final WorkerQueue<LogEntry> workerLogQueue;
    private final WorkerQueue<WorkerTriggerResult> workerTriggerResultQueue;
    private final RunContextInitializer runContextInitializer;

    public WorkerTriggerProcessor(String workerGroup,
        MetricRegistry metricRegistry,
        WorkerSecurityService workerSecurityService,
        Tracer tracer,
        RunContextInitializer runContextInitializer,
        WorkerQueue<LogEntry> workerLogQueue,
        WorkerQueue<WorkerTriggerResult> workerTriggerResultQueue,
        ExecutionKilledManager executionKilledManager) {
        super(workerGroup, metricRegistry, workerSecurityService, tracer, executionKilledManager);
        this.workerLogQueue = workerLogQueue;
        this.workerTriggerResultQueue = workerTriggerResultQueue;
        this.runContextInitializer = runContextInitializer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doProcess(WorkerTrigger workerTrigger) {
        final String[] metricsTags = metricRegistry.tags(workerTrigger, workerGroup);

        this.metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_STARTED_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_STARTED_COUNT_DESCRIPTION, metricsTags)
            .increment();

        this.metricRegistry
            .timer(MetricRegistry.METRIC_WORKER_TRIGGER_DURATION, MetricRegistry.METRIC_WORKER_TRIGGER_DURATION_DESCRIPTION, metricsTags)
            .record(() ->
            {
                StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                this.evaluateTriggerRunningCount.computeIfAbsent(
                    workerTrigger.uid(), s -> metricRegistry
                        .gauge(MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT_DESCRIPTION, new AtomicInteger(0), metricsTags)
                );

                this.evaluateTriggerRunningCount.get(workerTrigger.uid()).addAndGet(1);

                ConditionContext conditionContext = runContextInitializer.forWorker(workerTrigger);
                TriggerContext triggerContext = TriggerContext.of(workerTrigger);
                RunContext runContext = conditionContext.getRunContext();
                try {

                    Logs.logTrigger(
                        workerTrigger.triggerId(),
                        runContext.logger(),
                        Level.INFO,
                        "Type {} started",
                        workerTrigger.getTrigger().getType()
                    );

                    if (workerTrigger.getTrigger() instanceof PollingTriggerInterface pollingTrigger) {
                        WorkerTriggerCallable workerCallable = new WorkerTriggerCallable(runContext, conditionContext, triggerContext, workerTrigger, pollingTrigger);
                        io.kestra.core.models.flows.State.Type state = callJob(workerCallable);

                        if (workerCallable.getException() != null || !state.equals(SUCCESS)) {
                            this.handleTriggerError(workerTrigger, triggerContext, conditionContext, workerCallable.getException());
                        }

                        if (!state.equals(FAILED)) {
                            this.publishTriggerExecution(workerTrigger, workerCallable.getEvaluate());
                        }
                    } else if (workerTrigger.getTrigger() instanceof RealtimeTriggerInterface streamingTrigger) {
                        WorkerTriggerRealtimeCallable workerCallable = new WorkerTriggerRealtimeCallable(
                            runContext,
                            conditionContext,
                            triggerContext,
                            workerTrigger,
                            streamingTrigger,
                            throwable -> this.handleTriggerError(workerTrigger, triggerContext, conditionContext, throwable),
                            result -> this.publishTriggerExecution(workerTrigger, Optional.of(result))
                        );
                        io.kestra.core.models.flows.State.Type state = callJob(workerCallable);

                        // here the realtime trigger fail before the publisher being call so we create a fail execution
                        if (workerCallable.getException() != null || !state.equals(SUCCESS)) {
                            this.handleRealtimeTriggerError(workerTrigger, triggerContext, conditionContext, runContext, workerCallable.getException());
                        }
                    }
                } catch (Exception e) {
                    this.handleTriggerError(workerTrigger, triggerContext, conditionContext, e);
                } finally {
                    Logs.logTrigger(
                        workerTrigger.triggerId(),
                        runContext.logger(),
                        Level.INFO,
                        "Type {} completed in {}",
                        workerTrigger.getTrigger().getType(),
                        DurationFormatUtils.formatDurationHMS(stopWatch.getTime(TimeUnit.MILLISECONDS))
                    );

                    runContext.cleanup();
                }

                this.evaluateTriggerRunningCount.get(workerTrigger.uid()).addAndGet(-1);
            }
            );

        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ENDED_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ENDED_COUNT_DESCRIPTION, metricsTags)
            .increment();
    }

    private void handleTriggerError(WorkerTrigger workerTrigger, TriggerContext triggerContext, ConditionContext conditionContext, Throwable e) {
        String[] tags = metricRegistry.tags(workerTrigger, workerGroup);

        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT_DESCRIPTION, tags)
            .increment();

        logError(workerTrigger, conditionContext.getRunContext().logger(), e);

        TriggerEvaluationResult result = null;
        if (workerTrigger.getTrigger().isFailOnTriggerError()) {
            result = TriggerService.generateEvaluationResult(
                workerTrigger.getTrigger(), conditionContext, (Output) null
            ).withState(FAILED);
            Execution execution = result.toExecution(workerTrigger.triggerId());
            RunContextLogger.logEntries(Execution.loggingEventFromException(e), LogEntry.of(execution)).forEach(workerLogQueue::put);
        }
        this.workerTriggerResultQueue.put(WorkerTriggerResult.of(workerTrigger, result));
    }

    private void handleRealtimeTriggerError(WorkerTrigger workerTrigger, TriggerContext triggerContext, ConditionContext conditionContext, RunContext runContext, Throwable e) {
        String[] tags = metricRegistry.tags(workerTrigger, workerGroup);

        this.metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT_DESCRIPTION, tags)
            .increment();

        // We create a FAILED result, so the user is aware that the realtime trigger failed to be created
        TriggerEvaluationResult result = TriggerService.generateRealtimeEvaluationResult(
            workerTrigger.getTrigger(), conditionContext, null
        ).withState(FAILED);

        // We create an ERROR log attached to the execution
        Execution execution = result.toExecution(workerTrigger.triggerId());
        Logger logger = runContext.logger();
        Logs.logExecution(
            execution,
            logger,
            Level.ERROR,
            "[date: {}] Realtime trigger failed to be created in the worker with error: {}",
            workerTrigger.getData().date(),
            e != null ? e.getMessage() : "unknown",
            e
        );
        if (logger.isTraceEnabled() && e != null) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }
        this.workerTriggerResultQueue.put(WorkerTriggerResult.of(workerTrigger, result));
    }

    private void publishTriggerExecution(WorkerTrigger workerTrigger, Optional<TriggerEvaluationResult> evaluate) {
        metricRegistry
            .counter(
                MetricRegistry.METRIC_WORKER_TRIGGER_EXECUTION_COUNT,
                MetricRegistry.METRIC_WORKER_TRIGGER_EXECUTION_COUNT_DESCRIPTION,
                metricRegistry.tags(workerTrigger, workerGroup)
            ).increment();

        if (log.isDebugEnabled()) {
            Logs.logTrigger(
                workerTrigger.triggerId(),
                Level.DEBUG,
                "[type: {}] {}",
                workerTrigger.getTrigger().getType(),
                evaluate.map(result -> "New execution '" + result.executionId() + "'").orElse("Empty evaluation")
            );
        }

        this.workerTriggerResultQueue.put(WorkerTriggerResult.of(workerTrigger, evaluate.orElse(null)));
    }

    private void logError(WorkerTrigger workerTrigger, Logger logger, Throwable e) {
        if (e instanceof InterruptedException || (e != null && e.getCause() instanceof InterruptedException)) {
            Logs.logTrigger(
                workerTrigger.triggerId(),
                logger,
                Level.WARN,
                "[date: {}] Trigger evaluation interrupted in the worker",
                workerTrigger.getData().date()
            );
        } else {
            Logs.logTrigger(
                workerTrigger.triggerId(),
                logger,
                Level.WARN,
                "[date: {}] Trigger evaluation failed in the worker with error: {}",
                workerTrigger.getData().date(),
                e != null ? e.getMessage() : "unknown",
                e
            );
        }

        if (logger.isTraceEnabled() && e != null) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }
    }
}
