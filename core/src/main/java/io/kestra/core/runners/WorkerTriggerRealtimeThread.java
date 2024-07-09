package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;

public class WorkerTriggerRealtimeThread extends AbstractWorkerTriggerThread {
    RealtimeTriggerInterface streamingTrigger;
    Consumer<? super Throwable> onError;
    Consumer<Execution> onNext;

    public WorkerTriggerRealtimeThread(
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
    public void doRun() throws Exception {
        Publisher<Execution> evaluate;

        try {
            evaluate = streamingTrigger.evaluate(
                workerTrigger.getConditionContext().withRunContext(runContext),
                workerTrigger.getTriggerContext()
            );
        } catch (Exception e) {
            // If the Publisher cannot be created, we create a failed execution
            taskState = FAILED;
            exception = e;
            return;
        }

        // Here the publisher can be created, so the task is in success.
        // Errors can still occur, but they should be recovered automatically.
        taskState = SUCCESS;
        Flux.from(evaluate)
            .onBackpressureBuffer()
            .doOnError(onError)
            .doOnNext(onNext)
            .onErrorComplete()
            .blockLast();
    }
}
