package org.kestra.webserver.controllers;

import io.micronaut.data.model.Pageable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.sse.Event;
import io.micronaut.validation.Validated;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.apache.commons.io.FilenameUtils;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.hierarchies.FlowTree;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.RunnerUtils;
import org.kestra.core.services.ExecutionService;
import org.kestra.core.storages.StorageInterface;
import org.kestra.webserver.responses.PagedResults;
import org.kestra.webserver.utils.PageableUtils;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.kestra.core.utils.Rethrow.throwFunction;

@Validated
@Controller("/api/v1/")

public class ExecutionController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private ExecutionService executionService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Get(uri = "executions/search", produces = MediaType.TEXT_JSON)
    public PagedResults<Execution> find(
        @QueryValue(value = "q") String query,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "state") State.Type state,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) throws HttpStatusException {
        return PagedResults.of(
            executionRepository
                .find(query, PageableUtils.from(page, size, sort), state)
        );
    }

    /**
     * Get an execution flow tree
     *
     * @param executionId The execution identifier
     * @return the flow tree  with the provided identifier
     */
    @Get(uri = "executions/{executionId}/tree", produces = MediaType.TEXT_JSON)
    public FlowTree getTree(String executionId) throws IllegalVariableEvaluationException {
        return executionRepository
            .findById(executionId)
            .map(throwFunction(execution -> {
                Optional<Flow> flow = flowRepository.findById(
                    execution.getNamespace(),
                    execution.getFlowId(),
                    Optional.of(execution.getFlowRevision())
                );

                return flow
                    .map(throwFunction(value -> FlowTree.of(value, execution)))
                    .orElse(null);
            }))
            .orElse(null);
    }

    /**
     * Get a execution
     *
     * @param executionId The execution identifier
     * @return the execution with the provided identifier
     */
    @Get(uri = "executions/{executionId}", produces = MediaType.TEXT_JSON)
    public Execution get(String executionId) {
        return executionRepository
            .findById(executionId)
            .orElse(null);
    }

    /**
     * Find and returns all executions for a specific namespace and flow identifier
     *
     * @param namespace The flow namespace
     * @param flowId The flow identifier
     * @param page The number of result pages to return
     * @param size The number of result by page
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
     * @param id The flow id
     * @return execution created
     */
    @Post(uri = "executions/trigger/{namespace}/{id}", produces = MediaType.TEXT_JSON, consumes = MediaType.MULTIPART_FORM_DATA)
    public Execution trigger(
        String namespace,
        String id,
        @Nullable Map<String, String> inputs,
        @Nullable Publisher<StreamingFileUpload> files
    ) {
        Optional<Flow> find = flowRepository.findById(namespace, id);
        if (find.isEmpty()) {
            return null;
        }

        Execution current = runnerUtils.newExecution(
            find.get(),
            (flow, execution) -> runnerUtils.typedInputs(flow, execution, inputs, files)
        );

        executionQueue.emit(current);

        return current;
    }

    /**
     * Download file binary from uri parameter
     *
     * @param path The file URI to return
     * @return data binary content
     */
    @Get(uri = "executions/{executionId}/file", produces = MediaType.APPLICATION_OCTET_STREAM)
    public StreamedFile file(
        String executionId,
        @QueryValue(value = "path") URI path
    ) throws IOException, URISyntaxException {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isEmpty()) {
            return null;
        }

        Optional<Flow> flow = flowRepository.findById(execution.get().getNamespace(), execution.get().getFlowId());
        if (flow.isEmpty()) {
            return null;
        }

        String prefix = storageInterface.executionPrefix(flow.get(), execution.get());
        if (!path.getPath().substring(1).startsWith(prefix)) {
            throw new IllegalArgumentException("Invalid prefix path");
        }

        InputStream fileHandler = storageInterface.get(path);
        return new StreamedFile(fileHandler, MediaType.APPLICATION_OCTET_STREAM_TYPE)
            .attach(FilenameUtils.getName(path.toString()));
    }

    /**
     * Create a new execution from an old one and start it from a specified ("reference") task id
     *
     * @param executionId the origin execution id to clone
     * @param taskId the reference task id
     * @return the restarted execution
     */
    @Post(uri = "executions/{executionId}/restart", produces = MediaType.TEXT_JSON, consumes = MediaType.MULTIPART_FORM_DATA)
    public Execution restart(String executionId, @Nullable @QueryValue(value = "taskId") String taskId) throws Exception {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isEmpty()) {
            return null;
        }

        return executionService.restart(execution.get(), taskId);
    }

    /**
     * Trigger an new execution for current flow and follow execution
     *
     * @param executionId The execution id to follow
     * @return execution sse event
     */
    @Get(uri = "executions/{executionId}/follow", produces = MediaType.TEXT_JSON)
    public Flowable<Event<Execution>> follow(String executionId) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();
        final Flow[] flow = {null};

        return Flowable
            .<Event<Execution>>create(emitter -> {
                // already finished execution
                Optional<Execution> execution = executionRepository.findById(executionId);
                if (execution.isPresent()) {
                    Flow current = flowRepository.findByExecution(execution.get());
                    if (execution.get().isTerminatedWithListeners(current)) {
                        emitter.onNext(Event.of(execution.get()).id("end"));
                        emitter.onComplete();
                        return;
                    }
                }

                // emit the reposiytory one first in order to wait the queue connections 
                execution.ifPresent(value -> emitter.onNext(Event.of(value).id("progress")));

                // consume new value
                Runnable receive = this.executionQueue.receive(current -> {
                    if (current.getId().equals(executionId)) {

                        if (flow[0] == null) {
                            flow[0] = flowRepository.findByExecution(current);
                        }

                        emitter.onNext(Event.of(current).id("progress"));

                        if (current.isTerminatedWithListeners(flow[0])) {
                            emitter.onNext(Event.of(current).id("end"));
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
