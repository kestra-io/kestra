package io.kestra.scheduler.internals;

import java.util.Optional;

import io.kestra.core.models.triggers.TriggerEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.google.common.base.Throwables;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Schedulable;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.utils.Logs;

import jakarta.inject.Singleton;

@Singleton
public class SchedulableEvaluator {

    private static final Logger log = LoggerFactory.getLogger(SchedulableEvaluator.class);

    private final MetricRegistry metricRegistry;
    private final RunContextInitializer runContextInitializer;

    public SchedulableEvaluator(MetricRegistry metricRegistry, RunContextInitializer runContextInitializer) {
        this.metricRegistry = metricRegistry;
        this.runContextInitializer = runContextInitializer;
    }

    public Optional<TriggerEvaluationResult> evaluate(Schedulable schedulable, TriggerContext context, ConditionContext conditionContext) {
        return metricRegistry
            .timer(
                MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION, MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION_DESCRIPTION,
                metricRegistry.tags((AbstractTrigger) schedulable)
            )
            .record(() ->
            {
                DefaultRunContext runContext = (DefaultRunContext) conditionContext.getRunContext();
                try {
                    // mutability dirty hack that forces the creation of a new triggerExecutionId
                    runContextInitializer.forScheduler(
                        runContext,
                        context,
                        (AbstractTrigger) schedulable
                    );

                    Optional<TriggerEvaluationResult> evaluationResult = schedulable.eval(conditionContext, context);

                    if (log.isDebugEnabled()) {
                        Logs.logTrigger(
                            context,
                            Level.DEBUG,
                            "[type: {}] {}",
                            ((AbstractTrigger) schedulable).getType(),
                            evaluationResult.map(eval -> "New execution '" + eval.executionId() + "'").orElse("Empty evaluation")
                        );
                    }

                    conditionContext.getRunContext().cleanup();

                    return evaluationResult;
                } catch (Exception e) {
                    Logger logger = runContext.logger();
                    Logs.logTrigger(
                        context,
                        logger,
                        Level.WARN,
                        "[date: {}] Evaluate Failed with error '{}'",
                        context.getDate(),
                        e.getMessage(),
                        e
                    );

                    if (logger.isTraceEnabled()) {
                        logger.trace(Throwables.getStackTraceAsString(e));
                    }
                    return Optional.empty();
                }
            });
    }
}
