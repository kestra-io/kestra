package io.kestra.webserver.controllers;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.convert.format.Format;
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
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.ZonedDateTime;
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
    private ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/search", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Search for executions")
    public PagedResults<Execution> find(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "A flow id filter") @Nullable String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate,
        @Parameter(description = "A state filter") @Nullable @QueryValue(value = "state") List<State.Type> state
    ) {
        return PagedResults.of(executionRepository.find(
            PageableUtils.from(page, size, sort, executionRepository.sortMapping()),
            query,
            namespace,
            flowId,
            startDate,
            endDate,
            state
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "taskruns/search", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Search for taskruns")
    public PagedResults<TaskRun> findTaskRun(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable String namespace,
        @Parameter(description = "A flow id filter") @Nullable String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") ZonedDateTime endDate,
        @Parameter(description = "A state filter") @Nullable @QueryValue(value = "state") List<State.Type> state
    ) {
        return PagedResults.of(executionRepository.findTaskRun(
            PageableUtils.from(page, size, sort, executionRepository.sortMapping()),
            query,
            namespace,
            flowId,
            startDate,
            endDate,
            state
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "taskruns/maxTaskRunSetting")
    @Hidden
    public Integer maxTaskRunSetting() {
        return executionRepository.maxTaskRunSetting();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/graph", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Generate a graph for an execution")
    public FlowGraph flowGraph(
        @Parameter(description = "The execution id") String executionId
    ) throws IllegalVariableEvaluationException {
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

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Get an execution")
    public Execution get(
        @Parameter(description = "The execution id") String executionId
    ) {
        return executionRepository
            .findById(executionId)
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Search for executions for a flow")
    public PagedResults<Execution> findByFlowId(
        @Parameter(description = "The flow namespace") @QueryValue(value = "namespace") String namespace,
        @Parameter(description = "The flow id") @QueryValue(value = "flowId") String flowId,
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size
    ) {
        return PagedResults.of(
            executionRepository
                .findByFlowId(namespace, flowId, Pageable.from(page, size))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/webhook/{namespace}/{id}/{key}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution by POST webhook trigger")
    public Execution webhookTriggerPost(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id,
        @Parameter(description = "The webhook trigger uid") String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/webhook/{namespace}/{id}/{key}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution by GET webhook trigger")
    public Execution webhookTriggerGet(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id,
        @Parameter(description = "The webhook trigger uid") String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "executions/webhook/{namespace}/{id}/{key}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution by PUT webhook trigger")
    public Execution webhookTriggerPut(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id,
        @Parameter(description = "The webhook trigger uid") String key,
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

        if (find.get().isDisabled()) {
            throw new IllegalStateException("Cannot execute disabled flow");
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

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/trigger/{namespace}/{id}", produces = MediaType.TEXT_JSON, consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution for a flow")
    @ApiResponse(responseCode = "409", description = "if the flow is disabled")
    public Execution trigger(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id,
        @Nullable Map<String, String> inputs,
        @Nullable Publisher<StreamingFileUpload> files
    ) {
        Optional<Flow> find = flowRepository.findById(namespace, id);
        if (find.isEmpty()) {
            return null;
        }

        if (find.get().isDisabled()) {
            throw new IllegalStateException("Cannot execute disabled flow");
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

        String prefix = storageInterface.executionPrefix(flow.get(), execution.get());
        if (path.getPath().substring(1).startsWith(prefix)) {
            return null;
        }

        // maybe state
        prefix = storageInterface.statePrefix(flow.get().getNamespace(), flow.get().getId(), null, null);
        if (path.getPath().substring(1).startsWith(prefix)) {
            return null;
        }

        // maybe redirect to correct execution
        Optional<String> redirectedExecution = storageInterface.extractExecutionId(path);

        if (redirectedExecution.isPresent()) {
            return HttpResponse.redirect(URI.create((basePath != null? basePath : "") +
                redirect.replace("{executionId}", redirectedExecution.get()))
            );
        }

        throw new IllegalArgumentException("Invalid prefix path");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/file", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Executions"}, summary = "Download file for an execution")
    public HttpResponse<StreamedFile> file(
        @Parameter(description = "The execution id") String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue(value = "path") URI path
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

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/file/metas", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Get file meta information for an execution")
    public HttpResponse<FileMetas> filesize(
        @Parameter(description = "The execution id") String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue(value = "path") URI path
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

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/{executionId}/restart", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Restart a new execution from an old one")
    public Execution restart(
        @Parameter(description = "The execution id") String executionId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue(value = "revision") Integer revision
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isEmpty()) {
            return null;
        }

        this.controlRevision(execution.get(), revision);

        Execution restart = executionService.restart(execution.get(), revision);
        executionQueue.emit(restart);
        eventPublisher.publishEvent(new CrudEvent<>(restart, CrudEventType.UPDATE));

        return restart;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/{executionId}/replay", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Create a new execution from an old one and start it from a specified task run id")
    public Execution replay(
        @Parameter(description = "the original execution id to clone") String executionId,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue(value = "taskRunId") String taskRunId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue(value = "revision") Integer revision
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isEmpty()) {
            return null;
        }

        this.controlRevision(execution.get(), revision);

        Execution replay = executionService.replay(execution.get(), taskRunId, revision);
        executionQueue.emit(replay);
        eventPublisher.publishEvent(new CrudEvent<>(replay, CrudEventType.CREATE));

        return replay;
    }

    private void controlRevision(Execution execution, Integer revision) {
        if (revision != null) {
            Optional<Flow> flowRevision = this.flowRepository.findById(
                execution.getNamespace(),
                execution.getFlowId(),
                Optional.of(revision)
            );

            if (flowRevision.isEmpty()) {
                throw new NoSuchElementException("Unable to find revision " + revision  +
                    " on flow " + execution.getNamespace() + "." + execution.getFlowId()
                );
            }
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/{executionId}/state", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Change state for a taskrun in an execution")
    public Execution changeState(
        @Parameter(description = "The execution id") String executionId,
        @Parameter(description = "the taskRun id and state to apply") @Body StateRequest stateRequest
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isEmpty()) {
            return null;
        }

        Execution replay = executionService.markAs(execution.get(), stateRequest.getTaskRunId(), stateRequest.getState());
        executionQueue.emit(replay);
        eventPublisher.publishEvent(new CrudEvent<>(replay, CrudEventType.UPDATE));

        return replay;
    }

    @lombok.Value
    public static class StateRequest {
        String taskRunId;
        State.Type state;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "executions/{executionId}/kill", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Kill an execution")
    @ApiResponses(
        value = {
            @ApiResponse(responseCode = "204", description = "On success"),
            @ApiResponse(responseCode = "409", description = "if the executions is already finished")
        }
    )
    public HttpResponse<?> kill(
        @Parameter(description = "The execution id") String executionId
    ) {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isPresent() && execution.get().getState().isTerninated()) {
            throw new IllegalStateException("Execution is already finished, can't kill it");
        }

        killQueue.emit(ExecutionKilled
            .builder()
            .executionId(executionId)
            .build()
        );

        return HttpResponse.noContent();
    }

    private boolean isStopFollow(Flow flow, Execution execution) {
        return conditionService.isTerminatedWithListeners(flow, execution) &&
            execution.getState().getCurrent() != State.Type.PAUSED;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = {"Executions"}, summary = "Follow an execution")
    public Flowable<Event<Execution>> follow(
        @Parameter(description = "The execution id") String executionId
    ) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();

        return Flowable
            .<Event<Execution>>create(emitter -> {
                // already finished execution
                Execution execution = Await.until(
                    () -> executionRepository.findById(executionId).orElse(null),
                    Duration.ofMillis(500)
                );
                Flow flow = flowRepository.findByExecution(execution);

                if (this.isStopFollow(flow, execution)) {
                    emitter.onNext(Event.of(execution).id("end"));
                    emitter.onComplete();
                    return;
                }

                // emit the repository one first in order to wait the queue connections
                emitter.onNext(Event.of(execution).id("progress"));

                // consume new value
                Runnable receive = this.executionQueue.receive(current -> {
                    if (current.getId().equals(executionId)) {

                        emitter.onNext(Event.of(current).id("progress"));

                        if (this.isStopFollow(flow, current)) {
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
