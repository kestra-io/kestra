package io.kestra.worker.processors;

import com.google.common.base.Throwables;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.worker.models.WorkerTriggerResult;
import io.kestra.core.services.LabelService;
import io.kestra.core.trace.Tracer;
import io.kestra.core.utils.Logs;
import io.kestra.worker.services.ExecutionKilledManager;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.processors.internals.WorkerTriggerCallable;
import io.kestra.worker.processors.internals.WorkerTriggerRealtimeCallable;
import io.kestra.worker.queues.WorkerQueue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
            .record(() -> {
                    StopWatch stopWatch = new StopWatch();
                    stopWatch.start();

                    this.evaluateTriggerRunningCount.computeIfAbsent(workerTrigger.getTriggerContext().uid(), s -> metricRegistry
                        .gauge(MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_RUNNING_COUNT_DESCRIPTION, new AtomicInteger(0), metricsTags));

                    this.evaluateTriggerRunningCount.get(workerTrigger.getTriggerContext().uid()).addAndGet(1);

                    ConditionContext conditionContext = runContextInitializer.forWorker(workerTrigger);
                    RunContext runContext = conditionContext.getRunContext();
                    try {

                        Logs.logTrigger(
                            workerTrigger.getTriggerContext(),
                            runContext.logger(),
                            Level.INFO,
                            "Type {} started",
                            workerTrigger.getTrigger().getType()
                        );

                        if (workerTrigger.getTrigger() instanceof PollingTriggerInterface pollingTrigger) {
                            WorkerTriggerCallable workerCallable = new WorkerTriggerCallable(runContext, conditionContext, workerTrigger, pollingTrigger);
                            io.kestra.core.models.flows.State.Type state = callJob(workerCallable);

                            if (workerCallable.getException() != null || !state.equals(SUCCESS)) {
                                this.handleTriggerError(workerTrigger, conditionContext, workerCallable.getException());
                            }

                            if (!state.equals(FAILED)) {
                                this.publishTriggerExecution(workerTrigger, conditionContext, workerCallable.getEvaluate());
                            }
                        } else if (workerTrigger.getTrigger() instanceof RealtimeTriggerInterface streamingTrigger) {
                            WorkerTriggerRealtimeCallable workerCallable = new WorkerTriggerRealtimeCallable(
                                runContext,
                                conditionContext,
                                workerTrigger,
                                streamingTrigger,
                                throwable -> this.handleTriggerError(workerTrigger, conditionContext, throwable),
                                execution -> this.publishTriggerExecution(workerTrigger, conditionContext, Optional.of(execution))
                            );
                            io.kestra.core.models.flows.State.Type state = callJob(workerCallable);

                            // here the realtime trigger fail before the publisher being call so we create a fail execution
                            if (workerCallable.getException() != null || !state.equals(SUCCESS)) {
                                this.handleRealtimeTriggerError(workerTrigger, conditionContext, runContext, workerCallable.getException());
                            }
                        }
                    } catch (Exception e) {
                        this.handleTriggerError(workerTrigger, conditionContext, e);
                    } finally {
                        Logs.logTrigger(
                            workerTrigger.getTriggerContext(),
                            runContext.logger(),
                            Level.INFO,
                            "Type {} completed in {}",
                            workerTrigger.getTrigger().getType(),
                            DurationFormatUtils.formatDurationHMS(stopWatch.getTime(TimeUnit.MILLISECONDS))
                        );

                        runContext.cleanup();
                    }

                    this.evaluateTriggerRunningCount.get(workerTrigger.getTriggerContext().uid()).addAndGet(-1);
                }
            );

        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ENDED_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ENDED_COUNT_DESCRIPTION, metricsTags)
            .increment();
    }

    private void handleTriggerError(WorkerTrigger workerTrigger, ConditionContext conditionContext, Throwable e) {
        String[] tags = metricRegistry.tags(workerTrigger, workerGroup);

        metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT_DESCRIPTION, tags)
            .increment();

        logError(workerTrigger, conditionContext.getRunContext().logger(), e);
        Execution execution = workerTrigger.getTrigger().isFailOnTriggerError() ? TriggerService.generateExecution(workerTrigger.getTrigger(), conditionContext, workerTrigger.getTriggerContext(), (Output) null)
            .withState(FAILED) : null;
        if (execution != null) {
            RunContextLogger.logEntries(Execution.loggingEventFromException(e), LogEntry.of(execution)).forEach(workerLogQueue::put);
        }
        this.workerTriggerResultQueue.put(WorkerTriggerResult.of(workerTrigger, execution));
    }

    private void handleRealtimeTriggerError(WorkerTrigger workerTrigger, ConditionContext conditionContext, RunContext runContext, Throwable e) {
        String[] tags = metricRegistry.tags(workerTrigger, workerGroup);

        this.metricRegistry
            .counter(MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT, MetricRegistry.METRIC_WORKER_TRIGGER_ERROR_COUNT_DESCRIPTION, tags)
            .increment();

        // We create a FAILED execution, so the user is aware that the realtime trigger failed to be created
        var execution = TriggerService
            .generateRealtimeExecution(workerTrigger.getTrigger(), conditionContext, workerTrigger.getTriggerContext(), null)
            .withState(FAILED);

        // We create an ERROR log attached to the execution
        Logger logger = runContext.logger();
        Logs.logExecution(
            execution,
            logger,
            Level.ERROR,
            "[date: {}] Realtime trigger failed to be created in the worker with error: {}",
            workerTrigger.getTriggerContext().getDate(),
            e != null ? e.getMessage() : "unknown",
            e
        );
        if (logger.isTraceEnabled() && e != null) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }
        this.workerTriggerResultQueue.put(WorkerTriggerResult.of(workerTrigger, execution));
    }

    private void publishTriggerExecution(WorkerTrigger workerTrigger, ConditionContext conditionContext, Optional<Execution> evaluate) {
        metricRegistry
            .counter(
                MetricRegistry.METRIC_WORKER_TRIGGER_EXECUTION_COUNT,
                MetricRegistry.METRIC_WORKER_TRIGGER_EXECUTION_COUNT_DESCRIPTION,
                metricRegistry.tags(workerTrigger, workerGroup)
            ).increment();

        if (log.isDebugEnabled()) {
            Logs.logTrigger(
                workerTrigger.getTriggerContext(),
                Level.DEBUG,
                "[type: {}] {}",
                workerTrigger.getTrigger().getType(),
                evaluate.map(execution -> "New execution '" + execution.getId() + "'").orElse("Empty evaluation")
            );
        }

        var flow = conditionContext.getFlow();
        evaluate = evaluate.map(execution -> {
                List<Label> executionLabels = execution.getLabels() != null ? execution.getLabels() : new ArrayList<>();
                executionLabels.addAll(LabelService.labelsExcludingSystem(flow.getLabels()));
                return execution.withLabels(executionLabels);
            }
        );

        this.workerTriggerResultQueue.put(WorkerTriggerResult.of(workerTrigger, evaluate.orElse(null)));
    }


    private void logError(WorkerTrigger workerTrigger, Logger logger, Throwable e) {
        if (e instanceof InterruptedException || (e != null && e.getCause() instanceof InterruptedException)) {
            Logs.logTrigger(
                workerTrigger.getTriggerContext(),
                logger,
                Level.WARN,
                "[date: {}] Trigger evaluation interrupted in the worker",
                workerTrigger.getTriggerContext().getDate()
            );
        } else {
            Logs.logTrigger(
                workerTrigger.getTriggerContext(),
                logger,
                Level.WARN,
                "[date: {}] Trigger evaluation failed in the worker with error: {}",
                workerTrigger.getTriggerContext().getDate(),
                e != null ? e.getMessage() : "unknown",
                e
            );
        }

        if (logger.isTraceEnabled() && e != null) {
            logger.trace(Throwables.getStackTraceAsString(e));
        }
    }
}
