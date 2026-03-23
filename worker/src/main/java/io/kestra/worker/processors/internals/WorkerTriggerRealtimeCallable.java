package io.kestra.worker.processors.internals;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTrigger;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerRealtimeCallable extends AbstractWorkerTriggerCallable {
    RealtimeTriggerInterface streamingTrigger;
    ConditionContext conditionContext;
    Consumer<? super Throwable> onError;
    Consumer<Execution> onNext;

    public WorkerTriggerRealtimeCallable(
        RunContext runContext,
        ConditionContext conditionContext,
        WorkerTrigger workerTrigger,
        RealtimeTriggerInterface realtimeTrigger,
        Consumer<? super Throwable> onError,
        Consumer<Execution> onNext
    ) {
        super(runContext, realtimeTrigger.getClass().getName(), workerTrigger);
        this.streamingTrigger = realtimeTrigger;
        this.conditionContext = conditionContext;
        this.onError = onError;
        this.onNext = onNext;
    }

    @Override
    public State.Type doCall() throws Exception {
            Publisher<Execution> evaluate;

            try {
                evaluate = streamingTrigger.evaluate(
                    conditionContext.withRunContext(runContext),
                    workerTrigger.getTriggerContext()
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
