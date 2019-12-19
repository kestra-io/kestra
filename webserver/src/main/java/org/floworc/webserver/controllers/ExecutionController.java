package org.floworc.webserver.controllers;

import io.micronaut.data.model.Pageable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
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
import org.floworc.core.repositories.ExecutionRepositoryInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.runners.RunnerUtils;
import org.floworc.webserver.responses.PagedResults;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Validated
@Controller("/api/v1/")

public class ExecutionController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Get(uri = "executions/search", produces = MediaType.TEXT_JSON)
    public PagedResults<Execution> find(
        @QueryValue(value = "q") String query,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size
    ) {
        return PagedResults.of(
            executionRepository
                .find(query, Pageable.from(page, size))
        );
    }

    /**
     * Get a execution
     *
     * @param executionId The execution identifier
     * @return the execution with the provided identifier
     */
    @Get(uri = "executions/{executionId}", produces = MediaType.TEXT_JSON)
    public Maybe<Execution> get(String executionId) {
        return executionRepository
            .findById(executionId)
            .map(Maybe::just)
            .orElse(Maybe.empty());
    }

    /**
     * Find and returns all executions for a specific namespace and flow identifier
     *
     * @param namespace The flow namespace
     * @param flowId    The flow identifier
     * @param page      The number of result pages to return
     * @param size      The number of result by page
     * @return a list of found executions
     */
    @Get(uri = "executions", produces = MediaType.TEXT_JSON)
    public PagedResults<Execution> findByFlowId(
        @QueryValue(value = "namespace") String namespace,
        @QueryValue(value = "flowId") String flowId,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size) {
        return PagedResults.of(
            executionRepository
                .findByFlowId(namespace, flowId, Pageable.from(page, size))
        );
    }

    /**
     * Trigger an new execution for current flow
     *
     * @param namespace The flow namespace
     * @param id        The flow id
     * @return execution created
     */
    @Post(uri = "executions/trigger/{namespace}/{id}", produces = MediaType.TEXT_JSON, consumes = MediaType.MULTIPART_FORM_DATA)
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
     * @param executionId The execution id to follow
     * @return execution sse event
     */
    @Get(uri = "executions/{executionId}/follow", produces = MediaType.TEXT_JSON)
    public Flowable<Event<Execution>> triggerAndFollow(String executionId) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();

        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isEmpty()) {
            return Flowable.empty();
        }

        Optional<Flow> flow = flowRepository.findById(execution.get().getNamespace(), execution.get().getFlowId());
        if (flow.isEmpty()) {
            return Flowable.empty();
        }

        return Flowable
            .<Event<Execution>>create(emitter -> {
                Runnable receive = this.executionQueue.receive(current -> {
                    if (current.getId().equals(executionId)) {
                        emitter.onNext(Event.of(current).id(Execution.class.getSimpleName()));

                        if (current.isTerminatedWithListeners(flow.get())) {
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
