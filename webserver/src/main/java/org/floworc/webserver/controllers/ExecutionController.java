package org.floworc.webserver.controllers;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.sse.Event;
import io.micronaut.validation.Validated;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.runners.RunnerUtils;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Validated
@Controller("/api/v1/flows/{namespace}/{id}")
public class ExecutionController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    /**
     * Trigger an new execution for current flow
     *
     * @param namespace The flow namespace
     * @param id The flow id
     * @return execution created
     */
    @Post(uri = "trigger", produces = MediaType.TEXT_JSON, consumes = MediaType.MULTIPART_FORM_DATA)
    public Maybe<Execution> trigger(
        String namespace,
        String id,
        @Nullable Map<String, String> inputs,
        @Nullable Publisher<StreamingFileUpload> files
    ) {
        Optional<Flow> find = flowRepository.findById(namespace, id);
        if (find.isEmpty()) {
            return Maybe.empty();
        }

        Execution current = runnerUtils.newExecution(
            find.get(),
            (flow, execution) -> runnerUtils.typedInputs(flow, execution, inputs, files)
        );

        executionQueue.emit(current);

        return Maybe.just(current);
    }

    /**
     * Trigger an new execution for current flow and follow execution
     *
     * @param namespace The flow namespace
     * @param id The flow id
     * @param executionId The execution id to follow
     * @return execution sse event
     */
    @Get(uri = "executions/{executionId}/follow", produces = MediaType.TEXT_JSON)
    public Flowable<Event<Execution>> triggerAndFollow(String namespace, String id, String executionId) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();

        return Flowable
            .<Event<Execution>>create(emitter -> {
                Runnable receive = this.executionQueue.receive(current -> {
                    if (current.getId().equals(executionId)) {
                        emitter.onNext(Event.of(current).id(Execution.class.getSimpleName()));

                        if (current.getState().isTerninated()) {
                            emitter.onComplete();
                        }
                    }
                });
                cancel.set(receive);
            }, BackpressureStrategy.BUFFER)
            .doOnComplete(() -> {
                if (cancel.get() != null) {
                    cancel.get().run();
                }
            });
    }
}
