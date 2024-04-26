package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerRealtimeThread extends AbstractWorkerTriggerThread {
    RealtimeTriggerInterface streamingTrigger;
    Consumer<? super Throwable> onError;
    Consumer<Execution> onNext;

    public WorkerTriggerRealtimeThread(
        WorkerTrigger workerTrigger,
        RealtimeTriggerInterface realtimeTrigger,
        Consumer<? super Throwable> onError,
        Consumer<Execution> onNext
    ) {
        super(workerTrigger.getConditionContext().getRunContext(), realtimeTrigger.getClass().getName(), workerTrigger);
        this.streamingTrigger = realtimeTrigger;
        this.onError = onError;
        this.onNext = onNext;
    }

    @Override
    public void run() {
        Thread.currentThread().setContextClassLoader(this.streamingTrigger.getClass().getClassLoader());

        Publisher<Execution> evaluate;
        try {
            evaluate = streamingTrigger.evaluate(
                workerTrigger.getConditionContext().withRunContext(runContext),
                workerTrigger.getTriggerContext()
            );
            taskState = SUCCESS;
        } catch (Exception e) {
            this.exceptionHandler(this, e);
            return;
        }

        Flux.from(evaluate)
            .onBackpressureBuffer()
            .doOnError(onError)
            .doOnNext(onNext)
            .onErrorComplete()
            .blockLast();
    }
}
