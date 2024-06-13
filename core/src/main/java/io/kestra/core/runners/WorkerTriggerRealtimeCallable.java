package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerRealtimeCallable extends AbstractWorkerTriggerCallable {
    RealtimeTriggerInterface streamingTrigger;
    Consumer<? super Throwable> onError;
    Consumer<Execution> onNext;

    WorkerTriggerRealtimeCallable(
        RunContext runContext,
        WorkerTrigger workerTrigger,
        RealtimeTriggerInterface realtimeTrigger,
        Consumer<? super Throwable> onError,
        Consumer<Execution> onNext
    ) {
        super(runContext, realtimeTrigger.getClass().getName(), workerTrigger);
        this.streamingTrigger = realtimeTrigger;
        this.onError = onError;
        this.onNext = onNext;
    }

    @Override
    public State.Type doCall() throws Exception {
            Publisher<Execution> evaluate;

            try {
                evaluate = streamingTrigger.evaluate(
                    workerTrigger.getConditionContext().withRunContext(runContext),
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
