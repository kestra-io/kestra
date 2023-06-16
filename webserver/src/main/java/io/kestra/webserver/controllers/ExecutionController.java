package io.kestra.webserver.controllers;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.webserver.responses.BulkErrorResponse;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.convert.format.Format;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
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
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
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

    @Inject
    private RunContextFactory runContextFactory;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/search", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Search for executions")
    public PagedResults<Execution> find(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue List<String> labels
    ) {
        return PagedResults.of(executionRepository.find(
            PageableUtils.from(page, size, sort, executionRepository.sortMapping()),
            query,
            namespace,
            flowId,
            startDate,
            endDate,
            state,
            RequestUtils.toMap(labels)
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "taskruns/search", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Search for taskruns")
    public PagedResults<TaskRun> findTaskRun(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state
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
        @Parameter(description = "The execution id") @PathVariable String executionId
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
    @Post(uri = "executions/{executionId}/eval/{taskRunId}", produces = MediaType.TEXT_JSON, consumes = MediaType.TEXT_PLAIN)
    @Operation(tags = {"Executions"}, summary = "Evaluate a variable expression for this taskrun")
    public EvalResult eval(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @PathVariable String taskRunId,
        @Body String expression
    ) throws InternalException {
        Execution execution = executionRepository
            .findById(executionId)
            .orElseThrow(() -> new NoSuchElementException("Unable to find execution '" + executionId + "'"));

        TaskRun taskRun = execution
            .findTaskRunByTaskRunId(taskRunId);

        Flow flow = flowRepository
            .findByExecution(execution);

        Task task = flow.findTaskByTaskId(taskRun.getTaskId());

        RunContext runContext = runContextFactory.of(flow, task, execution, taskRun);

        try {
            return EvalResult.builder()
                .result(runContext.render(expression))
                .build();
        } catch (IllegalVariableEvaluationException e) {
            return EvalResult.builder()
                .error(e.getMessage())
                .stackTrace(ExceptionUtils.getStackTrace(e))
                .build();
        }
    }

    @SuperBuilder
    @Getter
    @NoArgsConstructor
    public static class EvalResult {
        String result;
        String error;
        String stackTrace;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Get an execution")
    public Execution get(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) {
        return executionRepository
            .findById(executionId)
            .orElse(null);
    }

    @Delete(uri = "executions/{executionId}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Executions"}, summary = "Delete an execution")
    @ApiResponse(responseCode = "204", description = "On success")
    public HttpResponse<Void> delete(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) {
        Optional<Execution> execution = executionRepository.findById(executionId);
        if (execution.isPresent()) {
            executionRepository.delete(execution.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @Delete(uri = "executions/by-ids", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Executions"}, summary = "Delete a list of executions")
    @ApiResponse(responseCode = "200", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> deleteByIds(
        @Parameter(description = "The execution id") @Body List<String> executionsId
    ) {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(executionId);
            if (execution.isPresent()) {
                executions.add(execution.get());
            } else {
                invalids.add(ManualConstraintViolation.of(
                    "execution not found",
                    executionId,
                    String.class,
                    "execution",
                    executionId
                ));
            }
        }
        if (invalids.size() > 0) {
            return HttpResponse.badRequest()
                .body(BulkErrorResponse
                    .builder()
                    .message("invalid bulk delete")
                    .invalids(invalids)
                    .build()
                );
        }

        executions
            .forEach(execution -> executionRepository.delete(execution));

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @Delete(uri = "executions/by-query", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Executions"}, summary = "Delete executions filter by query parameters")
    public HttpResponse<BulkResponse> deleteByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue List<String> labels
    ) {
        Integer count = executionRepository
            .find(
                query,
                namespace,
                flowId,
                startDate,
                endDate,
                state,
                RequestUtils.toMap(labels)
            )
            .map(e -> {
                executionRepository.delete(e);
                return 1;
            })
            .reduce(Integer::sum)
            .blockingGet();

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }


    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Search for executions for a flow")
    public PagedResults<Execution> findByFlowId(
        @Parameter(description = "The flow namespace") @QueryValue String namespace,
        @Parameter(description = "The flow id") @QueryValue String flowId,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size
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
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/webhook/{namespace}/{id}/{key}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution by GET webhook trigger")
    public Execution webhookTriggerGet(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "executions/webhook/{namespace}/{id}/{key}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution by PUT webhook trigger")
    public Execution webhookTriggerPut(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
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
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The inputs") @Nullable @Body Map<String, String> inputs,
        @Parameter(description = "The labels as a list of 'key:value'") @Nullable @QueryValue List<String> labels,
        @Parameter(description = "The inputs of type file") @Nullable @Part Publisher<StreamingFileUpload> files,
        @Parameter(description = "If the server will wait the end of the execution") @QueryValue(defaultValue = "false") Boolean wait,
        @Parameter(description = "The flow revision or latest if null") @QueryValue Optional<Integer> revision
    ) {
        Optional<Flow> find = flowRepository.findById(namespace, id, revision);
        if (find.isEmpty()) {
            return null;
        }

        if (find.get().isDisabled()) {
            throw new IllegalStateException("Cannot execute disabled flow");
        }

        Execution current = runnerUtils.newExecution(
            find.get(),
            (flow, execution) -> runnerUtils.typedInputs(flow, execution, inputs, files),
            RequestUtils.toMap(labels)
        );

        executionQueue.emit(current);
        eventPublisher.publishEvent(new CrudEvent<>(current, CrudEventType.CREATE));

        if (!wait) {
            return current;
        }

        AtomicReference<Runnable> cancel = new AtomicReference<>();

        return Single
            .<Execution>create(emitter -> {
                Runnable receive = this.executionQueue.receive(item -> {
                    Flow flow = flowRepository.findByExecution(current);

                    if (item.getId().equals(current.getId())) {

                        if (this.isStopFollow(flow, item)) {
                            emitter.onSuccess(item);
                        }
                    }
                });

                cancel.set(receive);
            })
            .doFinally(() -> {
                if (cancel.get() != null) {
                    cancel.get().run();
                }
            })
            .blockingGet();
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

        prefix = storageInterface.statePrefix(flow.get().getNamespace(), null, null, null);
        if (path.getPath().substring(1).startsWith(prefix)) {
            return null;
        }

        // maybe redirect to correct execution
        Optional<String> redirectedExecution = storageInterface.extractExecutionId(path);

        if (redirectedExecution.isPresent()) {
            return HttpResponse.redirect(URI.create((basePath != null ? basePath : "") +
                redirect.replace("{executionId}", redirectedExecution.get()))
            );
        }

        throw new IllegalArgumentException("Invalid prefix path");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/file", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Executions"}, summary = "Download file for an execution")
    public HttpResponse<StreamedFile> file(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path
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
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path
    ) throws IOException {
        HttpResponse<FileMetas> httpResponse = this.validateFile(executionId, path, "/api/v1/executions/{executionId}/file/metas?path=" + path);
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
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue Integer revision
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
    @Post(uri = "executions/restart/by-ids", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Restart a list of executions")
    @ApiResponse(responseCode = "200", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> restartByIds(
        @Parameter(description = "The execution id") @Body List<String> executionsId
    ) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(executionId);

            if (execution.isPresent() && !execution.get().getState().isFailed()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution not in state FAILED",
                    executionId,
                    String.class,
                    "execution",
                    executionId
                ));
            } else if (execution.isEmpty()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution not found",
                    executionId,
                    String.class,
                    "execution",
                    executionId
                ));
            } else {
                executions.add(execution.get());
            }
        }
        if (invalids.size() > 0) {
            return HttpResponse.badRequest(BulkErrorResponse
                .builder()
                .message("invalid bulk restart")
                .invalids(invalids)
                .build()
            );
        }
        for (Execution execution : executions) {
            Execution restart = executionService.restart(execution, null);
            executionQueue.emit(restart);
            eventPublisher.publishEvent(new CrudEvent<>(restart, CrudEventType.UPDATE));
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/restart/by-query", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Restart executions filter by query parameters")
    public HttpResponse<BulkResponse> restartByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue List<String> labels
    ) {
        Integer count = executionRepository
            .find(
                query,
                namespace,
                flowId,
                startDate,
                endDate,
                state,
                RequestUtils.toMap(labels)
            )
            .map(e -> {
                Execution restart = executionService.restart(e, null);
                executionQueue.emit(restart);
                eventPublisher.publishEvent(new CrudEvent<>(restart, CrudEventType.UPDATE));
                return 1;
            })
            .reduce(Integer::sum)
            .blockingGet();

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/{executionId}/replay", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Create a new execution from an old one and start it from a specified task run id")
    public Execution replay(
        @Parameter(description = "the original execution id to clone") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue Integer revision
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
                throw new NoSuchElementException("Unable to find revision " + revision +
                    " on flow " + execution.getNamespace() + "." + execution.getFlowId()
                );
            }
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/{executionId}/state", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Change state for a taskrun in an execution")
    public Execution changeState(
        @Parameter(description = "The execution id") @PathVariable String executionId,
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
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "if the executions is already finished")
    @ApiResponse(responseCode = "404", description = "if the executions is not found")
    public HttpResponse<?> kill(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) throws InternalException {
        Optional<Execution> maybeExecution = executionRepository.findById(executionId);
        if (maybeExecution.isEmpty()) {
            return HttpResponse.notFound();
        }

        var execution = maybeExecution.get();
        if (execution.getState().isTerminated()) {
            throw new IllegalStateException("Execution is already finished, can't kill it");
        }

        if (execution.getState().isPaused()) {
            // Must be resumed and killed, no need to send killing event to the worker as the execution is not executing anything in it.
            // An edge case can exist where the execution is resumed automatically before we resume it with a killing.
            this.executionService.resume(execution, State.Type.KILLING);
            return HttpResponse.noContent();
        }

        killQueue.emit(ExecutionKilled
            .builder()
            .executionId(executionId)
            .build()
        );

        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "executions/{executionId}/resume", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Resume a paused execution.")
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "if the executions is not paused")
    public HttpResponse<?> resume(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) throws InternalException {
        Optional<Execution> maybeExecution = executionRepository.findById(executionId);
        if (maybeExecution.isEmpty()) {
            return HttpResponse.notFound();
        }

        var execution = maybeExecution.get();
        if (!execution.getState().isPaused()) {
            throw new IllegalStateException("Execution is not paused, can't resume it");
        }

        this.executionService.resume(execution, State.Type.RUNNING);

        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "executions/kill/by-ids", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Kill a list of executions")
    @ApiResponse(responseCode = "200", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> killByIds(
        @Parameter(description = "The execution id") @Body List<String> executionsId
    ) {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(executionId);
            if (execution.isPresent() && execution.get().getState().isTerminated()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution already finished",
                    executionId,
                    String.class,
                    "execution",
                    executionId
                ));
            } else if (execution.isEmpty()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution not found",
                    executionId,
                    String.class,
                    "execution",
                    executionId
                ));
            } else {
                executions.add(execution.get());
            }
        }

        if (invalids.size() > 0) {
            return HttpResponse.badRequest(BulkErrorResponse
                .builder()
                .message("invalid bulk kill")
                .invalids(invalids)
                .build()
            );
        }

        executions.forEach(execution -> {
            if (execution.getState().isPaused()) {
                // Must be resumed and killed, no need to send killing event to the worker as the execution is not executing anything in it.
                // An edge case can exist where the execution is resumed automatically before we resume it with a killing.
                try {
                    this.executionService.resume(execution, State.Type.KILLING);
                } catch (InternalException e) {
                    log.warn("Unable to kill the paused execution {}, ignoring it", execution.getId(), e);
                }
            } else {
                killQueue.emit(ExecutionKilled
                    .builder()
                    .executionId(execution.getId())
                    .build()
                );
            }
        });

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "executions/kill/by-query", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Kill executions filter by query parameters")
    public HttpResponse<?> killByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue List<String> labels
    ) {
        var ids = executionRepository
            .find(
                query,
                namespace,
                flowId,
                startDate,
                endDate,
                state,
                RequestUtils.toMap(labels)
            )
            .map(execution -> execution.getId())
            .toList()
            .blockingGet();

        return killByIds(ids);
    }

    private boolean isStopFollow(Flow flow, Execution execution) {
        return conditionService.isTerminatedWithListeners(flow, execution) &&
            execution.getState().getCurrent() != State.Type.PAUSED;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = {"Executions"}, summary = "Follow an execution")
    public Flowable<Event<Execution>> follow(
        @Parameter(description = "The execution id") @PathVariable String executionId
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
