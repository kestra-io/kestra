package io.kestra.webserver.controllers;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import org.apache.commons.io.FilenameUtils;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.storage.FileMetas;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.types.Webhook;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Await;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import org.reactivestreams.Publisher;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Validated
@Controller("/api/v1/")
public class ExecutionController {
    @Nullable
    @Value("${micronaut.server.context-path}")
    protected String basePath;

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
    private ConditionService conditionService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killQueue;

    @Inject
    private ApplicationEventPublisher eventPublisher;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/search", produces = MediaType.TEXT_JSON)
    public PagedResults<Execution> find(
        @QueryValue(value = "q") String query,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "state") List<State.Type> state,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) {
        return PagedResults.of(
            executionRepository
                .find(query, PageableUtils.from(page, size, sort), state)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "taskruns/search", produces = MediaType.TEXT_JSON)
    public PagedResults<TaskRun> findTaskRun(
        @QueryValue(value = "q") String query,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "state") List<State.Type> state,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) {
        return PagedResults.of(
            executionRepository
                .findTaskRun(query, PageableUtils.from(page, size, sort), state)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "taskruns/maxTaskRunSetting")
    public Integer maxTaskRunSetting() {
        return executionRepository.maxTaskRunSetting();
    }

    /**
     * Get an execution flow tree
     *
     * @param executionId The execution identifier
     * @return the flow tree  with the provided identifier
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/graph", produces = MediaType.TEXT_JSON)
    public FlowGraph flowGraph(String executionId) throws IllegalVariableEvaluationException {
        return executionRepository
            .findById(executionId)
            .map(throwFunction(execution -> {
                Optional<Flow> flow = flowRepository.findById(
                    execution.getNamespace(),
                    execution.getFlowId(),
                    Optional.of(execution.getFlowRevision())
                );

                return flow
                    .map(throwFunction(value -> FlowGraph.of(value, execution)))
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
    @ExecuteOn(TaskExecutors.IO)
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
    @ExecuteOn(TaskExecutors.IO)
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
     * Trigger an new execution for a webhook trigger
     *
     * @param namespace The flow namespace
     * @param id The flow id
     * @param key The webhook trigger uid
     * @return execution created
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/webhook/{namespace}/{id}/{key}", produces = MediaType.TEXT_JSON)
    public Execution webhookTriggerPost(
        String namespace,
        String id,
        String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    /**
     * Trigger an new execution for a webhook trigger
     *
     * @param namespace The flow namespace
     * @param id The flow id
     * @param key The webhook trigger uid
     * @return execution created
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/webhook/{namespace}/{id}/{key}", produces = MediaType.TEXT_JSON)
    public Execution webhookTriggerGet(
        String namespace,
        String id,
        String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    /**
     * Trigger an new execution for a webhook trigger
     *
     * @param namespace The flow namespace
     * @param id The flow id
     * @param key The webhook trigger uid
     * @return execution created
     */
    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "executions/webhook/{namespace}/{id}/{key}", produces = MediaType.TEXT_JSON)
    public Execution webhookTriggerPut(
        String namespace,
        String id,
        String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    private Execution webhook(
        String namespace,
        String id,
        String key,
        HttpRequest<String> request
    ) {
        Optional<Flow> find = flowRepository.findById(namespace, id);
        if (find.isEmpty()) {
            return null;
        }

        Optional<Webhook> webhook = (find.get().getTriggers() == null ? new ArrayList<AbstractTrigger>() : find.get()
            .getTriggers())
            .stream()
            .filter(o -> o instanceof Webhook)
            .map(o -> (Webhook) o)
            .filter(w -> w.getKey().equals(key))
            .findFirst();

        if (webhook.isEmpty()) {
            return null;
        }

        Optional<Execution> execution = webhook.get().evaluate(request, find.get());

        if (execution.isEmpty()) {
            return null;
        }

        executionQueue.emit(execution.get());
        eventPublisher.publishEvent(new CrudEvent<>(execution.get(), CrudEventType.CREATE));

        return execution.get();
    }

    /**
     * Trigger an new execution for current flow
     *
     * @param namespace The flow namespace
     * @param id The flow id
     * @return execution created
     */
    @ExecuteOn(TaskExecutors.IO)
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
        eventPublisher.publishEvent(new CrudEvent<>(current, CrudEventType.CREATE));

        return current;
    }

    protected <T> HttpResponse<T> validateFile(String executionId, URI path, String redirect) {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isEmpty()) {
            throw new NoSuchElementException("Unable to find execution id '" + executionId + "'");
        }

        Optional<Flow> flow = flowRepository.findById(execution.get().getNamespace(), execution.get().getFlowId());
        if (flow.isEmpty()) {
            throw new NoSuchElementException("Unable to find flow id '" + executionId + "'");
        }

        // maybe redirect to correct execution
        String prefix = storageInterface.executionPrefix(flow.get(), execution.get());
        if (!path.getPath().substring(1).startsWith(prefix)) {
            Optional<String> redirectedExecution = storageInterface.extractExecutionId(path);

            if (redirectedExecution.isPresent()) {
                return HttpResponse.redirect(URI.create((basePath != null? basePath : "") +
                    redirect.replace("{executionId}", redirectedExecution.get()))
                );
            }

            throw new IllegalArgumentException("Invalid prefix path");
        }

        return null;
    }
    /**
     * Download file binary from uri parameter
     *
     * @param path The file URI to return
     * @return data binary content
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/file", produces = MediaType.APPLICATION_OCTET_STREAM)
    public HttpResponse<StreamedFile> file(
        String executionId,
        @QueryValue(value = "path") URI path
    ) throws IOException, URISyntaxException {
        HttpResponse<StreamedFile> httpResponse = this.validateFile(executionId, path, "/api/v1/executions/{executionId}/file?path=" + path);
        if (httpResponse != null) {
            return httpResponse;
        }

        InputStream fileHandler = storageInterface.get(path);
        return HttpResponse.ok(new StreamedFile(fileHandler, MediaType.APPLICATION_OCTET_STREAM_TYPE)
            .attach(FilenameUtils.getName(path.toString()))
        );
    }

    /**
     * Get file meta information from given path
     *
     * @param path The file URI to gather metas values
     * @return meta data about given file
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/file/metas", produces = MediaType.TEXT_JSON)
    public HttpResponse<FileMetas> filesize(
        String executionId,
        @QueryValue(value = "path") URI path
    ) throws IOException {
        HttpResponse<FileMetas> httpResponse =this.validateFile(executionId, path, "/api/v1/executions/{executionId}/file/metas?path=" + path);
        if (httpResponse != null) {
            return httpResponse;
        }

        return HttpResponse.ok(FileMetas.builder()
            .size(storageInterface.size(path))
            .build()
        );
    }

    /**
     * Create a new execution from an old one and start it from a specified ("reference") task id
     *
     * @param executionId the origin execution id to clone
     * @param taskId the reference task id
     * @return the restarted execution
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/{executionId}/restart", produces = MediaType.TEXT_JSON, consumes = MediaType.MULTIPART_FORM_DATA)
    public Execution restart(String executionId, @Nullable @QueryValue(value = "taskId") String taskId) throws Exception {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isEmpty()) {
            return null;
        }

        Execution restart = executionService.restart(execution.get(), taskId);
        eventPublisher.publishEvent(new CrudEvent<>(restart, CrudEventType.UPDATE));

        return restart;
    }

    /**
     * Kill an execution and stop all works
     *
     * @param executionId the execution id to kill
     * @throws IllegalArgumentException if the executions is already finished
     */
    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "executions/{executionId}/kill", produces = MediaType.TEXT_JSON)
    public HttpResponse<?> kill(String executionId) throws Exception {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isPresent() && execution.get().getState().isTerninated()) {
            throw new IllegalArgumentException("Execution is already finished, can't kill it");
        }

        killQueue.emit(ExecutionKilled
            .builder()
            .executionId(executionId)
            .build()
        );

        return HttpResponse.noContent();
    }

    /**
     * Trigger an new execution for current flow and follow execution
     *
     * @param executionId The execution id to follow
     * @return execution sse event
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    public Flowable<Event<Execution>> follow(String executionId) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();

        return Flowable
            .<Event<Execution>>create(emitter -> {
                // already finished execution
                Execution execution = Await.until(
                    () -> executionRepository.findById(executionId).orElse(null),
                    Duration.ofMillis(500)
                );
                Flow flow = flowRepository.findByExecution(execution);

                if (conditionService.isTerminatedWithListeners(flow, execution)) {
                    emitter.onNext(Event.of(execution).id("end"));
                    emitter.onComplete();
                    return;
                }

                // emit the reposiytory one first in order to wait the queue connections
                emitter.onNext(Event.of(execution).id("progress"));

                // consume new value
                Runnable receive = this.executionQueue.receive(current -> {
                    if (current.getId().equals(executionId)) {

                        emitter.onNext(Event.of(current).id("progress"));

                        if (conditionService.isTerminatedWithListeners(flow, current)) {
                            emitter.onNext(Event.of(current).id("end"));
                            emitter.onComplete();
                        }
                    }
                });

                cancel.set(receive);
            }, BackpressureStrategy.BUFFER)
            .doOnCancel(() -> {
                if (cancel.get() != null) {
                    cancel.get().run();
                }
            })
            .doOnComplete(() -> {
                if (cancel.get() != null) {
                    cancel.get().run();
                }
            });
    }
}
