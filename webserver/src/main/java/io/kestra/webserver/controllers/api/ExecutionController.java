package io.kestra.webserver.controllers.api;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.storage.FileMetas;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.GraphService;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.Await;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.webserver.responses.BulkErrorResponse;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.RequestUtils;
import io.kestra.webserver.utils.filepreview.FileRender;
import io.kestra.webserver.utils.filepreview.FileRenderBuilder;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.kestra.core.utils.DateUtils.validateTimeline;
import static io.kestra.core.utils.Rethrow.*;

@Slf4j
@Validated
@Controller("/api/v1/executions")
public class ExecutionController {
    @Nullable
    @Value("${micronaut.server.context-path}")
    protected String basePath;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowService flowService;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private GraphService graphService;

    @Inject
    private FlowInputOutput flowInputOutput;

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

    @Value("${kestra.server.preview.initial-rows:100}")
    private Integer initialPreviewRows;

    @Value("${kestra.server.preview.max-rows:5000}")
    private Integer maxPreviewRows;

    @Inject
    private TenantService tenantService;

    @Value("${kestra.url}")
    private Optional<String> kestraUrl;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = {"Executions"}, summary = "Search for executions")
    public PagedResults<Execution> find(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) {
        validateTimeline(startDate, endDate);
        final ZonedDateTime now = ZonedDateTime.now();

        return PagedResults.of(executionRepository.find(
            PageableUtils.from(page, size, sort, executionRepository.sortMapping()),
            query,
            tenantService.resolveTenant(),
            scope,
            namespace,
            flowId,
            resolveAbsoluteDateTime(startDate, timeRange, now),
            endDate,
            state,
            RequestUtils.toMap(labels),
            triggerExecutionId,
            childFilter
        ));
    }

    @VisibleForTesting
    ZonedDateTime resolveAbsoluteDateTime(ZonedDateTime absoluteDateTime, Duration timeRange, ZonedDateTime now) {
        if (timeRange != null) {
            if (absoluteDateTime != null) {
                throw new IllegalArgumentException("Parameters 'startDate' and 'timeRange' are mutually exclusive");
            }
            return now.minus(timeRange.abs());
        }

        return absoluteDateTime;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/graph")
    @Operation(tags = {"Executions"}, summary = "Generate a graph for an execution")
    public FlowGraph flowGraph(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The subflow tasks to display") @Nullable @QueryValue List<String> subflows
    ) throws IllegalVariableEvaluationException {
        return executionRepository
            .findById(tenantService.resolveTenant(), executionId)
            .map(throwFunction(execution -> {
                Optional<Flow> flow = flowRepository.findByIdWithoutAcl(
                    execution.getTenantId(),
                    execution.getNamespace(),
                    execution.getFlowId(),
                    Optional.of(execution.getFlowRevision())
                );

                return flow
                    .map(throwFunction(value ->
                        graphService.flowGraph(value, subflows,  execution).forExecution()
                    ))
                    .orElse(null);
            }))
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/eval/{taskRunId}", consumes = MediaType.TEXT_PLAIN)
    @Operation(tags = {"Executions"}, summary = "Evaluate a variable expression for this taskrun")
    public EvalResult eval(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @PathVariable String taskRunId,
        @Body String expression
    ) throws InternalException {
        Execution execution = executionRepository
            .findById(tenantService.resolveTenant(), executionId)
            .orElseThrow(() -> new NoSuchElementException("Unable to find execution '" + executionId + "'"));

        TaskRun taskRun = execution
            .findTaskRunByTaskRunId(taskRunId);

        Flow flow = flowRepository
            .findByExecution(execution);

        Task task = flow.findTaskByTaskId(taskRun.getTaskId());

        try {
            return EvalResult.builder()
                .result(runContextRender(flow, task, execution, taskRun, expression))
                .build();
        } catch (IllegalVariableEvaluationException e) {
            return EvalResult.builder()
                .error(e.getMessage())
                .stackTrace(ExceptionUtils.getStackTrace(e))
                .build();
        }
    }

    protected String runContextRender(Flow flow, Task task, Execution execution, TaskRun taskRun, String expression) throws IllegalVariableEvaluationException {
        return runContextFactory.of(flow, task, execution, taskRun, false).render(expression);
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
    @Get(uri = "/{executionId}")
    @Operation(tags = {"Executions"}, summary = "Get an execution")
    public Execution get(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) {
        return executionRepository
            .findById(tenantService.resolveTenant(), executionId)
            .orElse(null);
    }

    @Delete(uri = "/{executionId}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Executions"}, summary = "Delete an execution")
    @ApiResponse(responseCode = "204", description = "On success")
    public HttpResponse<Void> delete(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "Whether to delete execution logs") @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics") @QueryValue(defaultValue = "true")  Boolean  deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage") @QueryValue(defaultValue = "true")  Boolean deleteStorage
    ) throws IOException {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isPresent()) {
            executionService.delete(execution.get(), deleteLogs, deleteMetrics, deleteStorage);
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @Delete(uri = "/by-ids")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Executions"}, summary = "Delete a list of executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Deleted with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> deleteByIds(
        @Parameter(description = "The execution id") @Body List<String> executionsId,
        @Parameter(description = "Whether to delete non-terminated executions") @Nullable @QueryValue(defaultValue = "false") Boolean includeNonTerminated,
        @Parameter(description = "Whether to delete execution logs") @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics") @QueryValue(defaultValue = "true")  Boolean  deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage") @QueryValue(defaultValue = "true")  Boolean deleteStorage
    ) throws IOException {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && (execution.get().getState().isTerminated() || includeNonTerminated)) {
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
        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest()
                .body(BulkErrorResponse
                    .builder()
                    .message("invalid bulk delete")
                    .invalids(invalids)
                    .build()
                );
        }

        executions
            .forEach(throwConsumer(execution -> executionService.delete(execution, deleteLogs, deleteMetrics, deleteStorage)));

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @Delete(uri = "/by-query")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Executions"}, summary = "Delete executions filter by query parameters")
    public HttpResponse<BulkResponse> deleteByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter,
        @Parameter(description = "Whether to delete non-terminated executions") @Nullable @QueryValue(defaultValue = "false") Boolean includeNonTerminated,
        @Parameter(description = "Whether to delete execution logs") @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics") @QueryValue(defaultValue = "true")  Boolean  deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage") @QueryValue(defaultValue = "true")  Boolean deleteStorage
    ) throws IOException {
        validateTimeline(startDate, endDate);

        Integer count = executionRepository
            .find(
                query,
                tenantService.resolveTenant(),
                scope,
                namespace,
                flowId,
                resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
                endDate,
                state,
                RequestUtils.toMap(labels),
                triggerExecutionId,
                childFilter
            )
            .filter(it -> it.getState().isTerminated() || includeNonTerminated)
            .map(throwFunction(e -> {
                executionService.delete(e, deleteLogs, deleteMetrics, deleteStorage);
                return 1;
            }))
            .reduce(Integer::sum)
            .blockOptional()
            .orElse(0);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Executions"}, summary = "Search for executions for a flow")
    public PagedResults<Execution> findByFlowId(
        @Parameter(description = "The flow namespace") @QueryValue String namespace,
        @Parameter(description = "The flow id") @QueryValue String flowId,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size
    ) {
        return PagedResults.of(
            executionRepository
                .findByFlowId(tenantService.resolveTenant(), namespace, flowId, PageableUtils.from(page, size))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/webhook/{namespace}/{id}/{key}")
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution by POST webhook trigger")
    public HttpResponse<Execution> webhookTriggerPost(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/webhook/{namespace}/{id}/{key}")
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution by GET webhook trigger")
    public HttpResponse<Execution> webhookTriggerGet(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/webhook/{namespace}/{id}/{key}")
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution by PUT webhook trigger")
    public HttpResponse<Execution> webhookTriggerPut(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
        HttpRequest<String> request
    ) {
        return this.webhook(namespace, id, key, request);
    }

    private HttpResponse<Execution> webhook(
        String namespace,
        String id,
        String key,
        HttpRequest<String> request
    ) {
        Optional<Flow> find = flowRepository.findById(tenantService.resolveTenant(), namespace, id);
        return webhook(find, key, request);
    }

    protected HttpResponse<Execution> webhook(
        Optional<Flow> maybeFlow,
        String key,
        HttpRequest<String> request
    ) {
        if (maybeFlow.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Flow not found");
        }

        var flow = maybeFlow.get();
        if (flow.isDisabled()) {
            throw new IllegalStateException("Cannot execute a disabled flow");
        }

        if (flow instanceof FlowWithException fwe) {
            throw new IllegalStateException("Cannot execute an invalid flow: " + fwe.getException());
        }

        Optional<Webhook> webhook = (flow.getTriggers() == null ? new ArrayList<AbstractTrigger>() : flow
            .getTriggers())
            .stream()
            .filter(o -> o instanceof Webhook)
            .map(o -> (Webhook) o)
            .filter(w -> {
                RunContext runContext = runContextFactory.of(flow, w);
                try {
                    String webhookKey = runContext.render(w.getKey()).trim();
                    return webhookKey.equals(key);
                } catch (IllegalVariableEvaluationException e) {
                    // be conservative, don't crash but filter the webhook
                    log.warn("Unable to render the webhook key {}, the webhook will be ignored", key, e);
                    return false;
                }
            })
            .findFirst();

        if (webhook.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Webhook not found");
        }

        Optional<Execution> execution = webhook.get().evaluate(request, flow);

        if (execution.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "No execution triggered");
        }

        var result = execution.get();
        if (flow.getLabels() != null) {
            result = result.withLabels(flow.getLabels());
        }

        // we check conditions here as it's easier as the execution is created we have the body and headers available for the runContext
        var conditionContext = conditionService.conditionContext(runContextFactory.of(flow, result), flow, result);
        if (!conditionService.isValid(flow, webhook.get(), conditionContext)) {
            return HttpResponse.noContent();
        }

        try {
            executionQueue.emit(result);
            eventPublisher.publishEvent(new CrudEvent<>(result, CrudEventType.CREATE));
            return HttpResponse.ok(result);
        } catch (QueueException e) {
            log.error(e.getMessage(), e);
            return HttpResponse.serverError();
        }

    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/trigger/{namespace}/{id}", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Trigger a new execution for a flow")
    @ApiResponse(responseCode = "409", description = "if the flow is disabled")
    @SingleResult
    @Deprecated
    public Publisher<ExecutionResponse> trigger(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @Nullable @PathVariable String id,
        @Parameter(description = "The inputs") @Nullable  @Body MultipartBody inputs,
        @Parameter(description = "The labels as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "If the server will wait the end of the execution") @QueryValue(defaultValue = "false") Boolean wait,
        @Parameter(description = "The flow revision or latest if null") @QueryValue Optional<Integer> revision
    ) throws IOException {
        return this.create(namespace, id, inputs, labels, wait, revision, Optional.empty());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{id}/validate", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Validate the creation of a new execution for a flow")
    @ApiResponse(responseCode = "409", description = "if the flow is disabled")
    @SingleResult
    public Publisher<ApiValidateExecutionInputsResponse> validateInputsOnCreate(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The inputs") @Nullable @Body MultipartBody inputs,
        @Parameter(description = "The labels as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The flow revision or latest if null") @QueryValue Optional<Integer> revision
    ) {
        return Mono.create(
            sink -> {
                Flow flow;
                try {
                    flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), namespace, id, revision);
                } catch (IllegalStateException e) {
                    sink.error(new IllegalStateException("Cannot validate execution inputs. Cause: " + e.getMessage()));
                    return;
                }

                try {
                    Execution execution = Execution.newExecution(flow, parseLabels(labels));
                    List<InputAndValue> values = flowInputOutput.validateExecutionInputs(flow.getInputs(), execution, inputs, true);
                    sink.success(ApiValidateExecutionInputsResponse.of(id, namespace, values));
                } catch (Exception e) {
                    sink.error(e);
                }
            });
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{id}", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Create a new execution for a flow")
    @ApiResponse(responseCode = "409", description = "if the flow is disabled")
    @SingleResult
    public Publisher<ExecutionResponse> create(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The inputs") @Nullable @Body MultipartBody inputs,
        @Parameter(description = "The labels as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "If the server will wait the end of the execution") @QueryValue(defaultValue = "false") Boolean wait,
        @Parameter(description = "The flow revision or latest if null") @QueryValue Optional<Integer> revision,
        @Parameter(description = "Schedule the flow on a specific date") @QueryValue Optional<ZonedDateTime> scheduleDate
    ) throws IOException {
        return Mono.create(
            sink -> {

                Flow flow;
                try {
                    flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), namespace, id, revision);
                } catch (IllegalStateException e) {
                    sink.error(new IllegalStateException("Cannot execute flow. Cause: " + e.getMessage()));
                    return;
                }

                try {
                    Execution current = Execution.newExecution(
                        flow,
                        throwBiFunction((f, execution) -> flowInputOutput.readExecutionInputs(f, execution, inputs)),
                        parseLabels(labels),
                        scheduleDate
                    );

                    executionQueue.emit(current);
                    eventPublisher.publishEvent(new CrudEvent<>(current, CrudEventType.CREATE));

                    if (!wait) {
                        sink.success(ExecutionResponse.fromExecution(current, executionUrl(current)));
                    } else {
                        Runnable receive = this.executionQueue.receive(either -> {
                            if (either.isRight()) {
                                log.error("Unable to deserialize the execution: {}", either.getRight().getMessage());
                                sink.success();
                            }

                            Execution item = either.getLeft();
                            if (item.getId().equals(current.getId()) && this.isStopFollow(flow, item)) {
                                sink.success(ExecutionResponse.fromExecution(item, executionUrl(item)));
                            }
                        });

                        sink.onDispose(receive::run);
                    }
                } catch (IOException | QueueException e) {
                    sink.error(new RuntimeException(e));
                }
            });
    }

    private URI executionUrl(Execution execution) {
        return URI.create(kestraUrl.orElse("") + "/ui" + (execution.getTenantId() != null ? "/" + execution.getTenantId(): "")
            + "/executions/"
            + execution.getNamespace() + "/"
            + execution.getFlowId() + "/"
            + execution.getId()
        );
    }

    @Getter
    public static class ExecutionResponse extends Execution {
        private final URI url;

        // This is not nice, but we cannot use @AllArgsConstructor as it would open a bunch of necessary changes on the Execution class.
        ExecutionResponse(String tenantId, String id, String namespace, String flowId, Integer flowRevision, List<TaskRun> taskRunList, Map<String, Object> inputs, Map<String, Object> outputs, List<Label> labels, Map<String, Object> variables, State state, String parentId, String originalId, ExecutionTrigger trigger, boolean deleted, ExecutionMetadata metadata, Instant scheduleDate, URI url) {
            super(tenantId, id, namespace, flowId, flowRevision, taskRunList, inputs, outputs, labels, variables, state, parentId, originalId, trigger, deleted, metadata, scheduleDate);

            this.url = url;
        }

        public static ExecutionResponse fromExecution(Execution execution, URI url) {
            return new ExecutionResponse(
                execution.getTenantId(),
                execution.getId(),
                execution.getNamespace(),
                execution.getFlowId(),
                execution.getFlowRevision(),
                execution.getTaskRunList(),
                execution.getInputs(),
                execution.outputs(),
                execution.getLabels(),
                execution.getVariables(),
                execution.getState(),
                execution.getParentId(),
                execution.getOriginalId(),
                execution.getTrigger(),
                execution.isDeleted(),
                execution.getMetadata(),
                execution.getScheduleDate(),
                url
            );
        }
    }

    private List<Label> parseLabels(List<String> labels) {
        return labels == null ? null : RequestUtils.toMap(labels).entrySet().stream()
            .map(entry -> new Label(entry.getKey(), entry.getValue()))
            .toList();
    }

    protected <T> HttpResponse<T> validateFile(String executionId, URI path, String redirect) {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            throw new NoSuchElementException("Unable to find execution id '" + executionId + "'");
        }

        String flowId = execution.get().getFlowId();
        Optional<Flow> flow = flowRepository.findById(execution.get().getTenantId(), execution.get().getNamespace(), flowId);
        if (flow.isEmpty()) {
            throw new NoSuchElementException("Unable to find flow id '" + flowId + "'");
        }

        String prefix = StorageContext
            .forExecution(execution.get())
            .getExecutionStorageURI().getPath();

        if (path.getPath().startsWith(prefix)) {
            return null;
        }

        // maybe state
        StorageContext context = StorageContext.forFlow(flow.get());
        prefix = context.getStateStorePrefix(null, false, null);
        if (path.getPath().startsWith(prefix)) {
            return null;
        }

        prefix = context.getStateStorePrefix(null, true, null);
        if (path.getPath().startsWith(prefix)) {
            return null;
        }

        // maybe redirect to correct execution
        Optional<String> redirectedExecution = StorageContext.extractExecutionId(path);

        if (redirectedExecution.isPresent()) {
            return HttpResponse.redirect(URI.create((basePath != null ? basePath : "") +
                redirect.replace("{executionId}", redirectedExecution.get()))
            );
        }

        throw new IllegalArgumentException("Invalid prefix path");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/file", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Executions"}, summary = "Download file for an execution")
    public HttpResponse<StreamedFile> file(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path
    ) throws IOException, URISyntaxException {
        HttpResponse<StreamedFile> httpResponse = this.validateFile(executionId, path, "/api/v1/" + this.getTenant() + "executions/{executionId}/file?path=" + path);
        if (httpResponse != null) {
            return httpResponse;
        }

        InputStream fileHandler = storageInterface.get(tenantService.resolveTenant(), path);
        return HttpResponse.ok(new StreamedFile(fileHandler, MediaType.APPLICATION_OCTET_STREAM_TYPE)
            .attach(FilenameUtils.getName(path.toString()))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/file/metas")
    @Operation(tags = {"Executions"}, summary = "Get file meta information for an execution")
    public HttpResponse<FileMetas> filesize(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path
    ) throws IOException {
        HttpResponse<FileMetas> httpResponse = this.validateFile(executionId, path, "/api/v1/" + this.getTenant() + "executions/{executionId}/file/metas?path=" + path);
        if (httpResponse != null) {
            return httpResponse;
        }

        return HttpResponse.ok(FileMetas.builder()
            .size(storageInterface.getAttributes(tenantService.resolveTenant(), path).getSize())
            .build()
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/restart")
    @Operation(tags = {"Executions"}, summary = "Restart a new execution from an old one")
    public Execution restart(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue Integer revision
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }
        this.controlRevision(execution.get(), revision);

        Execution restart = executionService.restart(execution.get(), revision);
        executionQueue.emit(restart);
        eventPublisher.publishEvent(new CrudEvent<>(restart, execution.get(), CrudEventType.UPDATE));

        return restart;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/restart/by-ids")
    @Operation(tags = {"Executions"}, summary = "Restart a list of executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Restarted with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> restartByIds(
        @Parameter(description = "The execution id") @Body List<String> executionsId
    ) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);

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
        if (!invalids.isEmpty()) {
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
            eventPublisher.publishEvent(new CrudEvent<>(restart, execution, CrudEventType.UPDATE));
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/restart/by-query")
    @Operation(tags = {"Executions"}, summary = "Restart executions filter by query parameters")
    public HttpResponse<BulkResponse> restartByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws Exception {
        validateTimeline(startDate, endDate);

        Integer count = executionRepository
            .find(
                query,
                tenantService.resolveTenant(),
                scope,
                namespace,
                flowId,
                resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
                endDate,
                state,
                RequestUtils.toMap(labels),
                triggerExecutionId,
                childFilter
            )
            .map(throwFunction(e -> {
                Execution restart = executionService.restart(e, null);
                executionQueue.emit(restart);
                eventPublisher.publishEvent(new CrudEvent<>(restart, e, CrudEventType.UPDATE));
                return 1;
            }))
            .reduce(Integer::sum)
            .block();

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/replay")
    @Operation(tags = {"Executions"}, summary = "Create a new execution from an old one and start it from a specified task run id")
    public Execution replay(
        @Parameter(description = "the original execution id to clone") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue Integer revision
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }

        this.controlRevision(execution.get(), revision);

        Execution replay = executionService.replay(execution.get(), taskRunId, revision);
        executionQueue.emit(replay);
        eventPublisher.publishEvent(new CrudEvent<>(replay, execution.get(), CrudEventType.CREATE));

        return replay;
    }

    private void controlRevision(Execution execution, Integer revision) {
        if (revision != null) {
            Optional<Flow> flowRevision = this.flowRepository.findById(
                execution.getTenantId(),
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
    @Post(uri = "/{executionId}/state")
    @Operation(tags = {"Executions"}, summary = "Change state for a taskrun in an execution")
    public Execution changeState(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "the taskRun id and state to apply") @Body StateRequest stateRequest
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }

        Flow flow = flowRepository.findByExecution(execution.get());

        Execution replay = executionService.markAs(execution.get(), flow, stateRequest.getTaskRunId(), stateRequest.getState());
        executionQueue.emit(replay);
        eventPublisher.publishEvent(new CrudEvent<>(replay, execution.get(), CrudEventType.UPDATE));

        return replay;
    }

    @lombok.Value
    public static class StateRequest {
        String taskRunId;
        State.Type state;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/change-status")
    @Operation(tags = {"Executions"}, summary = "Change the status of an execution")
    public Execution changeStatus(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The new status of the execution") @NotNull @QueryValue State.Type status
    ) throws QueueException {
        if (!status.isTerminated()) {
            throw new IllegalArgumentException("You can only change the status of an execution to a terminal state.");
        }

        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }

        if (!execution.get().getState().isTerminated()) {
            throw new IllegalArgumentException("You can only change the status of a terminated execution.");
        }

        Execution updated = execution.get().withState(status);

        executionQueue.emit(updated);
        eventPublisher.publishEvent(new CrudEvent<>(updated, execution.get(), CrudEventType.UPDATE));

        return updated;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/change-status/by-ids")
    @Operation(tags = {"Executions"}, summary = "Change status of executions by id")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Changed status with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public HttpResponse<?> changeStatusById(
        @Parameter(description = "The execution id") @Body List<String> executionsId,
        @Parameter(description = "The new status of the executions") @NotNull @QueryValue State.Type newStatus
    ) throws QueueException {
        if (!newStatus.isTerminated()) {
            throw new IllegalArgumentException("You can only change the status of an execution to a terminal state.");
        }

        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && !execution.get().getState().isTerminated()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution not in a terminated state",
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

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(BulkErrorResponse
                .builder()
                .message("invalid bulk change execution status")
                .invalids(invalids)
                .build()
            );
        }

        for (Execution execution : executions) {
            Execution replay = execution.withState(newStatus);

            executionQueue.emit(replay);
            eventPublisher.publishEvent(new CrudEvent<>(replay, execution, CrudEventType.UPDATE));
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/change-status/by-query")
    @Operation(tags = {"Executions"}, summary = "Change executions status by query parameters")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Changed status with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public HttpResponse<?> changeStatusByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter,
        @Parameter(description = "The new status of the executions") @NotNull @QueryValue State.Type newStatus
    ) throws QueueException {
        validateTimeline(startDate, endDate);

        var ids = executionRepository
            .find(
                query,
                tenantService.resolveTenant(),
                scope,
                namespace,
                flowId,
                resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
                endDate,
                state,
                RequestUtils.toMap(labels),
                triggerExecutionId,
                childFilter
            )
            .map(Execution::getId)
            .collectList()
            .block();

        return changeStatusById(ids, newStatus);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/{executionId}/kill{?isOnKillCascade}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Kill an execution")
    @ApiResponse(responseCode = "202", description = "Execution kill was requested successfully")
    @ApiResponse(responseCode = "409", description = "if the executions is already finished")
    @ApiResponse(responseCode = "404", description = "if the executions is not found")
    public HttpResponse<?> kill(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "Specifies whether killing the execution also kill all subflow executions.") @QueryValue(defaultValue = "true") Boolean isOnKillCascade
    ) throws InternalException, QueueException {

        Optional<Execution> maybeExecution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (maybeExecution.isEmpty()) {
            return HttpResponse.notFound();
        }

        var execution = maybeExecution.get();

        // Always emit an EXECUTION_KILLED event when isOnKillCascade=true.
        if (execution.getState().isTerminated() && !isOnKillCascade) {
            throw new IllegalStateException("Execution is already finished, can't kill it");
        }

        killQueue.emit(ExecutionKilledExecution
            .builder()
            .state(ExecutionKilled.State.REQUESTED)
            .executionId(executionId)
            .isOnKillCascade(isOnKillCascade)
            .tenantId(tenantService.resolveTenant())
            .build()
        );

        return HttpResponse.accepted();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/kill/by-ids")
    @Operation(tags = {"Executions"}, summary = "Kill a list of executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Killed with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> killByIds(
        @Parameter(description = "The execution id") @Body List<String> executionsId
    ) throws QueueException {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
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

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(BulkErrorResponse
                .builder()
                .message("invalid bulk kill")
                .invalids(invalids)
                .build()
            );
        }

        executions.forEach(throwConsumer(execution -> {
            killQueue.emit(ExecutionKilledExecution
                .builder()
                .state(ExecutionKilled.State.REQUESTED)
                .executionId(execution.getId())
                .isOnKillCascade(false) // Explicitly force cascade to false.
                .tenantId(tenantService.resolveTenant())
                .build()
            );
        }));
        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/resume/validate", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Validate inputs to resume a paused execution.")
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "if the executions is not paused")
    @SingleResult
    public Publisher<ApiValidateExecutionInputsResponse>  validateInputsOnResume(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The inputs") @Nullable @Body MultipartBody inputs
    ) throws Exception {
        return Mono.<ApiValidateExecutionInputsResponse>create(sink -> {
            try {
                Execution execution = executionService.getExecutionIfPause(tenantService.resolveTenant(), executionId);
                Flow flow = flowRepository.findByExecutionWithoutAcl(execution);
                List<InputAndValue> values = this.executionService.validateForResume(execution, flow, inputs);
                sink.success(ApiValidateExecutionInputsResponse.of(execution.getFlowId(), execution.getNamespace(), values));
            } catch (Exception e) {
                sink.error(new RuntimeException(e));
            }
        }).doOnError(t -> Flux.from(inputs).subscribeOn(Schedulers.boundedElastic()).blockLast()); // need to consume the inputs in case of error
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/resume", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Resume a paused execution.")
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "if the executions is not paused")
    @SingleResult
    public Publisher<HttpResponse<?>> resume(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The inputs") @Nullable @Body MultipartBody inputs
    ) throws Exception {
        return Mono.<HttpResponse<?>>create(sink -> {
            try {
                Execution execution = executionService.getExecutionIfPause(tenantService.resolveTenant(), executionId);
                Flow flow = flowRepository.findByExecutionWithoutAcl(execution);
                Execution resumeExecution = this.executionService.resume(execution, flow, State.Type.RUNNING, inputs);
                this.executionQueue.emit(resumeExecution);
                sink.success(HttpResponse.noContent());
            } catch (IllegalStateException | NoSuchElementException e) {
                sink.error(e);
            } catch (Exception e) {
                sink.error(new RuntimeException(e));
            }
        }).doOnError(t -> Flux.from(inputs).subscribeOn(Schedulers.boundedElastic()).blockLast()); // need to consume the inputs in case of error
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/resume/by-ids")
    @Operation(tags = {"Executions"}, summary = "Resume a list of paused executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Resumed with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> resumeByIds(
        @Parameter(description = "The execution id") @Body List<String> executionsId
    ) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();
        Map<String, Flow> flows = new HashMap<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && !execution.get().getState().isPaused()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution not in state PAUSED",
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

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(BulkErrorResponse
                .builder()
                .message("invalid bulk resume")
                .invalids(invalids)
                .build()
            );
        }

        for (Execution execution : executions) {
            var flow = flows.get(execution.getFlowId() + "_" + execution.getFlowRevision()) != null ? flows.get(execution.getFlowId() + "_" + execution.getFlowRevision()) : flowRepository.findByExecutionWithoutAcl(execution);
            flows.put(execution.getFlowId() + "_" + execution.getFlowRevision(), flow);
            Execution resumeExecution = this.executionService.resume(execution, flow, State.Type.RUNNING);
            this.executionQueue.emit(resumeExecution);
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/resume/by-query")
    @Operation(tags = {"Executions"}, summary = "Resume executions filter by query parameters")
    public HttpResponse<?> resumeByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws Exception {
        validateTimeline(startDate, endDate);

        var ids = executionRepository
            .find(
                query,
                tenantService.resolveTenant(),
                scope,
                namespace,
                flowId,
                resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
                endDate,
                state,
                RequestUtils.toMap(labels),
                triggerExecutionId,
                childFilter
            )
            .map(Execution::getId)
            .collectList()
            .block();

        return resumeByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/kill/by-query")
    @Operation(tags = {"Executions"}, summary = "Kill executions filter by query parameters")
    public HttpResponse<?> killByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws QueueException {
        validateTimeline(startDate, endDate);

        var ids = executionRepository
            .find(
                query,
                tenantService.resolveTenant(),
                scope,
                namespace,
                flowId,
                resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
                endDate,
                state,
                RequestUtils.toMap(labels),
                triggerExecutionId,
                childFilter
            )
            .map(Execution::getId)
            .collectList()
            .block();

        return killByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/replay/by-query")
    @Operation(tags = {"Executions"}, summary = "Create new executions from old ones filter by query parameters. Keep the flow revision")
    public HttpResponse<?> replayByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws Exception {
        validateTimeline(startDate, endDate);

        var ids = executionRepository
            .find(
                query,
                tenantService.resolveTenant(),
                scope,
                namespace,
                flowId,
                resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
                endDate,
                state,
                RequestUtils.toMap(labels),
                triggerExecutionId,
                childFilter
            )
            .map(Execution::getId)
            .collectList()
            .block();

        return replayByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/replay/by-ids")
    @Operation(tags = {"Executions"}, summary = "Create new executions from old ones. Keep the flow revision")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Replayed with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> replayByIds(
        @Parameter(description = "The execution id") @Body List<String> executionsId
    ) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isEmpty()) {
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

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(BulkErrorResponse
                .builder()
                .message("invalid bulk replay")
                .invalids(invalids)
                .build()
            );
        }

        for (Execution execution : executions) {
            Execution replay = executionService.replay(execution, null, null);
            executionQueue.emit(replay);
            eventPublisher.publishEvent(new CrudEvent<>(replay, execution, CrudEventType.CREATE));
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    private boolean isStopFollow(Flow flow, Execution execution) {
        return conditionService.isTerminatedWithListeners(flow, execution) &&
            execution.getState().getCurrent() != State.Type.PAUSED;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = {"Executions"}, summary = "Follow an execution")
    public Flux<Event<Execution>> follow(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();

        return Flux
            .<Event<Execution>>create(emitter -> {
                // already finished execution
                Execution execution = null;
                try {
                    execution = Await.until(
                        () -> executionRepository.findById(tenantService.resolveTenant(), executionId).orElse(null),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(10)
                    );
                } catch (TimeoutException e) {
                    emitter.error(new HttpStatusException(HttpStatus.NOT_FOUND, "Unable to find the execution " + executionId));
                    return;
                }

                Flow flow;
                try {
                    flow = flowRepository.findByExecutionWithoutAcl(execution);
                } catch (IllegalStateException e)  {
                    emitter.error(new HttpStatusException(HttpStatus.NOT_FOUND, "Unable to find the flow for the execution " + executionId));
                    return;
                }

                if (this.isStopFollow(flow, execution)) {
                    emitter.next(Event.of(execution).id("end"));
                    emitter.complete();
                    return;
                }

                // emit the repository one first in order to wait the queue connections
                emitter.next(Event.of(execution).id("progress"));

                // consume new value
                Runnable receive = this.executionQueue.receive(either -> {
                    if (either.isRight()) {
                        log.error("Unable to deserialize the execution: {}", either.getRight().getMessage());
                        return;
                    }

                    Execution current = either.getLeft();
                    if (current.getId().equals(executionId)) {

                        emitter.next(Event.of(current).id("progress"));

                        if (this.isStopFollow(flow, current)) {
                            emitter.next(Event.of(current).id("end"));
                            emitter.complete();
                        }
                    }
                });

                cancel.set(receive);
            }, FluxSink.OverflowStrategy.BUFFER)
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

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/file/preview")
    @Operation(tags = {"Executions"}, summary = "Get file preview for an execution")
    public HttpResponse<?> filePreview(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path,
        @Parameter(description = "The max row returns") @QueryValue @Nullable Integer maxRows,
        @Parameter(description = "The file encoding as Java charset name. Defaults to UTF-8", example = "ISO-8859-1") @QueryValue(defaultValue = "UTF-8") String encoding
    ) throws IOException {
        this.validateFile(executionId, path, "/api/v1/" + this.getTenant() + "executions/{executionId}/file?path=" + path);

        String extension = FilenameUtils.getExtension(path.toString());
        Optional<Charset> charset;

        try {
            charset = Optional.ofNullable(encoding).map(Charset::forName);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException("Unable to preview using encoding '" + encoding + "'");
        }

        try (InputStream fileStream = storageInterface.get(tenantService.resolveTenant(), path)) {
            FileRender fileRender = FileRenderBuilder.of(
                extension,
                fileStream,
                charset,
                maxRows == null ? this.initialPreviewRows : (maxRows > this.maxPreviewRows ? this.maxPreviewRows : maxRows)
            );

            return HttpResponse.ok(fileRender);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/labels")
    @Operation(tags = {"Executions"}, summary = "Add or update labels of a terminated execution")
    @ApiResponse(responseCode = "404", description = "If the execution cannot be found")
    @ApiResponse(responseCode = "400", description = "If the execution is not terminated")
    public HttpResponse<?> setLabels(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The labels to add to the execution") @Body @NotNull @Valid List<Label> labels
    ) {
        Optional<Execution> maybeExecution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (maybeExecution.isEmpty()) {
            return HttpResponse.notFound();
        }

        Execution execution = maybeExecution.get();
        if (!execution.getState().getCurrent().isTerminated()) {
            return HttpResponse.badRequest("The execution is not terminated");
        }

        Execution newExecution = setLabels(execution, labels);

        return HttpResponse.ok(newExecution);
    }

    private Execution setLabels(Execution execution, List<Label> labels) {
        Map<String, String> newLabels = labels.stream().collect(Collectors.toMap(Label::key, Label::value));
        if (execution.getLabels() != null) {
            execution.getLabels().forEach(
                label -> {
                    // only add execution label if not updated
                    if (!newLabels.containsKey(label.key())) {
                        newLabels.put(label.key(), label.value());
                    }
                }
            );
        }

        Execution newExecution = execution
            .toBuilder()
            .labels(newLabels.entrySet().stream().map(entry -> new Label(entry.getKey(), entry.getValue())).toList())
            .build();
        eventPublisher.publishEvent(new CrudEvent<>(newExecution, execution, CrudEventType.UPDATE));

        return executionRepository.save(newExecution);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/labels/by-ids")
    @Operation(tags = {"Executions"}, summary = "Set labels on a list of executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Killed with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> setLabelsByIds(
        @Parameter(description = "The request") @Body SetLabelsByIdsRequest setLabelsByIds
    ) {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : setLabelsByIds.executionsId()) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && !execution.get().getState().isTerminated()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution is not terminated",
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

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(BulkErrorResponse
                .builder()
                .message("invalid bulk set labels")
                .invalids(invalids)
                .build()
            );
        }

        executions.forEach(execution -> setLabels(execution, setLabelsByIds.executionLabels()));
        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    public record SetLabelsByIdsRequest(List<String> executionsId, List<Label> executionLabels) {
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/labels/by-query")
    @Operation(tags = {"Executions"}, summary = "Set label on executions filter by query parameters")
    public HttpResponse<?> setLabelsByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter,
        @Parameter(description = "The labels to add to the execution") @Body @NotNull @Valid List<Label> setLabels
    ) {
        validateTimeline(startDate, endDate);

        var ids = executionRepository
            .find(
                query,
                tenantService.resolveTenant(),
                scope,
                namespace,
                flowId,
                resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
                endDate,
                state,
                RequestUtils.toMap(labels),
                triggerExecutionId,
                childFilter
            )
            .map(Execution::getId)
            .collectList()
            .block();

        return setLabelsByIds(new SetLabelsByIdsRequest(ids, setLabels));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/flow")
    @Operation(tags = {"Executions"}, summary = "Get flow information's for an execution")
    public FlowForExecution getFlowForExecutionById(
        @Parameter(description = "The execution that you want flow information's") String executionId
    ) {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow();

        return FlowForExecution.of(flowRepository.findByExecutionWithoutAcl(execution));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/flows/{namespace}/{flowId}")
    @Operation(tags = {"Executions"}, summary = "Get flow information's for an execution")
    public FlowForExecution getFlowForExecution(
        @Parameter(description = "The namespace of the flow") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The flow revision") @Nullable Integer revision
    ) {

        return FlowForExecution.of(flowRepository.findByIdWithoutAcl(tenantService.resolveTenant(), namespace, flowId, Optional.ofNullable(revision)).orElseThrow());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/namespaces")
    @Operation(tags = {"Executions"}, summary = "Get all namespaces that have executable flows")
    public List<String> listDistinctNamespace() {
        return flowRepository.findDistinctNamespaceExecutable(tenantService.resolveTenant());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/namespaces/{namespace}/flows")
    @Operation(tags = {"Executions"}, summary = "Get all flow ids for a namespace")
    public List<FlowForExecution> getFlowsByNamespace(
        @Parameter(description = "The namespace") @PathVariable String namespace
    ) {
        return flowRepository.findByNamespaceExecutable(tenantService.resolveTenant(), namespace);
    }

    public String getTenant() {
        return tenantService.resolveTenant() != null ? tenantService.resolveTenant() + "/" : "";
    }

    @Introspected
    public record ApiValidateExecutionInputsResponse(
        @Parameter(description = "The flow's ID")
        String id,
        @Parameter(description = "The namespace")
        String namespace,
        @Parameter(description = "The flow's inputs")
        List<ApiInputAndValue> inputs
    ) {

        @Introspected
        public record ApiInputAndValue(
            @Parameter(description = "The input")
            Input<?> input,
            @Parameter(description = "The value")
            Object value,
            @Parameter(description = "Specifies whether the input is enabled")
            boolean enabled,
            @Parameter(description = "The validation errors")
            List<ApiInputError> errors
        ) {
        }

        @Introspected
        public record ApiInputError(
            @Parameter(description = "The error message")
            String message
        ) {

        }

        public static ApiValidateExecutionInputsResponse of(String id, String namespace, List<InputAndValue> inputs) {
            return new ApiValidateExecutionInputsResponse(
                id,
                namespace,
                inputs.stream().map(it -> new ApiInputAndValue(
                    it.input(),
                    it.value(),
                    it.enabled(),
                    Optional.ofNullable(it.exception()).map(exception ->
                        exception.getConstraintViolations()
                            .stream()
                            .map(cv -> new ApiInputError(cv.getMessage()))
                            .toList()
                    ).orElse(List.of())
                )).toList()
            );
        }
    }
}
