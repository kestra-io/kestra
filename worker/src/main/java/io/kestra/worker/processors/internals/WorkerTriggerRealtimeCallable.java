package io.kestra.worker.processors.internals;

import java.util.function.Consumer;

import org.reactivestreams.Publisher;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.models.triggers.TriggerEvaluationResult;

import reactor.core.publisher.Flux;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerRealtimeCallable extends AbstractWorkerTriggerCallable {
    RealtimeTriggerInterface streamingTrigger;
    ConditionContext conditionContext;
    TriggerContext triggerContext;
    Consumer<? super Throwable> onError;
    Consumer<TriggerEvaluationResult> onNext;

    public WorkerTriggerRealtimeCallable(
        RunContext runContext,
        ConditionContext conditionContext,
        TriggerContext triggerContext,
        WorkerTrigger workerTrigger,
        RealtimeTriggerInterface realtimeTrigger,
        Consumer<? super Throwable> onError,
        Consumer<TriggerEvaluationResult> onNext) {
        super(runContext, realtimeTrigger.getClass().getName(), workerTrigger);
        this.streamingTrigger = realtimeTrigger;
        this.conditionContext = conditionContext;
        this.triggerContext = triggerContext;
        this.onError = onError;
        this.onNext = onNext;
    }

    @Override
    public State.Type doCall() throws Exception {
        Publisher<TriggerEvaluationResult> evaluate;

        try {
            evaluate = streamingTrigger.eval(
                conditionContext.withRunContext(runContext),
                triggerContext
            );
        } catch (Exception e) {
            // If the Publisher cannot be created, we create a failed execution
            exception = e;
            return FAILED;
        }

        Flux.from(evaluate)
            .onBackpressureBuffer()
            .doOnError(onError)
            .doOnNext(onNext)
            .onErrorComplete()
            .blockLast();

        // Here the publisher can be created, so the task is in success.
        // Errors can still occur, but they should be recovered automatically.
        return SUCCESS;
    }
}
