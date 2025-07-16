package io.kestra.webserver.controllers.api;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.debug.Breakpoint;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
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
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.runners.pebble.functions.SecretFunction;
import io.kestra.core.services.*;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.trace.propagation.ExecutionTextMapSetter;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.Pause;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.BulkErrorResponse;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.ExecutionStreamingService;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.RequestUtils;
import io.kestra.webserver.utils.filepreview.FileRender;
import io.kestra.webserver.utils.filepreview.FileRenderBuilder;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.convert.format.Format;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.NotFoundException;
import io.micronaut.http.server.multipart.MultipartBody;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
import java.util.stream.Collectors;

import static io.kestra.core.models.Label.CORRELATION_ID;
import static io.kestra.core.models.Label.SYSTEM_PREFIX;
import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
@Validated
@Controller("/api/v1/{tenant}/executions")
public class ExecutionController {
    @Nullable
    @Value("${micronaut.server.context-path}")
    protected String basePath;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    @Nullable
    private VariableRenderer.VariableConfiguration variableConfiguration;

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
    private ConcurrencyLimitService concurrencyLimitService;

    @Inject
    private ExecutionStreamingService streamingService;

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

    @Inject
    private Optional<OpenTelemetry> openTelemetry;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = {"Executions"}, summary = "Search for executions")
    public PagedResults<Execution> searchExecutions(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,
        //Deprecated params
        @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the executions to include", deprecated = true) @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter", deprecated = true) @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime", deprecated = true) @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime", deprecated = true) @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", deprecated = true, examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter", deprecated = true) @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'", deprecated = true) @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id", deprecated = true) @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter", deprecated = true) @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter

    ) {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            startDate,
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId);

        return PagedResults.of(executionRepository.find(
            PageableUtils.from(page, size, sort, executionRepository.sortMapping()),
            tenantService.resolveTenant(),
            filters
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
    public FlowGraph getExecutionFlowGraph(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The subflow tasks to display") @Nullable @QueryValue List<String> subflows
    ) throws Exception {
        return executionRepository
            .findById(tenantService.resolveTenant(), executionId)
            .map(throwFunction(execution -> {
                Optional<FlowWithSource> flow = flowRepository.findByIdWithSourceWithoutAcl(
                    execution.getTenantId(),
                    execution.getNamespace(),
                    execution.getFlowId(),
                    Optional.of(execution.getFlowRevision())
                );

                return flow
                    .map(throwFunction(value ->
                        graphService.flowGraph(value, subflows, execution).forExecution()
                    ))
                    .orElse(null);
            }))
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/eval/{taskRunId}", consumes = MediaType.TEXT_PLAIN)
    @Operation(tags = {"Executions"}, summary = "Evaluate a variable expression for this taskrun")
    public EvalResult evalTaskRunExpression(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @PathVariable String taskRunId,
        @RequestBody(description = "The Pebble expression that should be evaluated") @Body String expression
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

    private String runContextRender(Flow flow, Task task, Execution execution, TaskRun taskRun, String expression) throws IllegalVariableEvaluationException {
        return runContextFactory.of(
            flow,
            task,
            execution,
            taskRun,
            false,
            new VariableRenderer(applicationContext, variableConfiguration, List.of(SecretFunction.NAME))
        ).render(expression);
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
    public Execution getExecution(
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
    public HttpResponse<Void> deleteExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "Whether to delete execution logs") @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics") @QueryValue(defaultValue = "true") Boolean deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage") @QueryValue(defaultValue = "true") Boolean deleteStorage
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
    public MutableHttpResponse<?> deleteExecutionsByIds(
        @RequestBody(description = "The execution id") @Body List<String> executionsId,
        @Parameter(description = "Whether to delete non-terminated executions") @Nullable @QueryValue(defaultValue = "false") Boolean includeNonTerminated,
        @Parameter(description = "Whether to delete execution logs") @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics") @QueryValue(defaultValue = "true") Boolean deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage") @QueryValue(defaultValue = "true") Boolean deleteStorage
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
    public HttpResponse<?> deleteExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter,

        @Parameter(description = "Whether to delete non-terminated executions") @Nullable @QueryValue(defaultValue = "false") Boolean includeNonTerminated,
        @Parameter(description = "Whether to delete execution logs") @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics") @QueryValue(defaultValue = "true") Boolean deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage") @QueryValue(defaultValue = "true") Boolean deleteStorage
    ) throws IOException {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return deleteExecutionsByIds(ids, includeNonTerminated, deleteLogs, deleteMetrics, deleteStorage);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Executions"}, summary = "Search for executions for a flow")
    public PagedResults<Execution> searchExecutionsByFlowId(
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
    public HttpResponse<Execution> triggerExecutionByPostWebhook(
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
    public HttpResponse<Execution> triggerExecutionByGetWebhook(
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
    public HttpResponse<Execution> triggerExecutionByPutWebhook(
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
            result = result.withLabels(LabelService.labelsExcludingSystem(flow));
        }

        // we check conditions here as it's easier as the execution is created we have the body and headers available for the runContext
        var conditionContext = conditionService.conditionContext(runContextFactory.of(flow, result), flow, result);
        if (!conditionService.isValid(flow, webhook.get(), conditionContext)) {
            return HttpResponse.noContent();
        }

        try {
            // inject the traceparent into the execution
            Optional<TextMapPropagator> propagator = openTelemetry
                .map(OpenTelemetry::getPropagators)
                .map(ContextPropagators::getTextMapPropagator);

            if (propagator.isPresent()) {
                propagator.get().inject(Context.current(), result, ExecutionTextMapSetter.INSTANCE);
            }

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
    public Publisher<ExecutionResponse> triggerExecution(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @Nullable @PathVariable String id,
        @RequestBody(description = "The inputs") @Nullable @Body MultipartBody inputs,
        @Parameter(description = "The labels as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "If the server will wait the end of the execution") @QueryValue(defaultValue = "false") Boolean wait,
        @Parameter(description = "The flow revision or latest if null") @QueryValue Optional<Integer> revision
    ) throws IOException {
        return this.createExecution(namespace, id, inputs, labels, wait, revision, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{id}/validate", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Validate the creation of a new execution for a flow")
    @ApiResponse(responseCode = "409", description = "if the flow is disabled")
    @SingleResult
    public Publisher<ApiValidateExecutionInputsResponse> validateNewExecutionInputs(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @RequestBody(description = "The inputs") @Nullable @Body MultipartBody inputs,
        @Parameter(description = "The labels as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The flow revision or latest if null") @QueryValue Optional<Integer> revision
    ) {
        Flow flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), namespace, id, revision);
        List<Label> parsedLabels = parseLabels(labels);
        Execution execution = Execution.newExecution(flow, parsedLabels);
        return flowInputOutput
            .validateExecutionInputs(flow.getInputs(), flow, execution, inputs)
            .map(values -> ApiValidateExecutionInputsResponse.of(id, namespace, values));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{id}", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Create a new execution for a flow")
    @ApiResponse(responseCode = "409", description = "if the flow is disabled")
    @SingleResult
    public Publisher<ExecutionResponse> createExecution(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @RequestBody(description = "The inputs") @Nullable @Body MultipartBody inputs,
        @Parameter(description = "The labels as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "If the server will wait the end of the execution") @QueryValue(defaultValue = "false") Boolean wait,
        @Parameter(description = "The flow revision or latest if null") @QueryValue Optional<Integer> revision,
        @Parameter(description = "Schedule the flow on a specific date") @QueryValue Optional<ZonedDateTime> scheduleDate,
        @Parameter(description = "Set a list of breakpoints at specific tasks 'id.value', separated by a coma.") @QueryValue Optional<String> breakpoints,
        @Parameter(description = "Specific execution kind") @QueryValue Optional<ExecutionKind> kind
    ) {
        Flow flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), namespace, id, revision);
        List<Label> parsedLabels = parseLabels(labels);
        final Execution current = Execution.newExecution(flow, null, parsedLabels, scheduleDate).toBuilder()
            .kind(kind.orElse(null))
            .breakpoints(breakpoints.map(s -> Arrays.stream(s.split(",")).map(Breakpoint::of).toList()).orElse(null))
            .build();

        return flowInputOutput.readExecutionInputs(flow, current, inputs)
            .flatMap(executionInputs -> {
                Execution executionWithInputs = current.withInputs(executionInputs);
                try {
                    // inject the traceparent into the execution
                    openTelemetry
                        .map(OpenTelemetry::getPropagators)
                        .map(ContextPropagators::getTextMapPropagator)
                        .ifPresent(propagator -> propagator.inject(Context.current(), executionWithInputs, ExecutionTextMapSetter.INSTANCE));

                    executionQueue.emit(executionWithInputs);
                    eventPublisher.publishEvent(new CrudEvent<>(executionWithInputs, CrudEventType.CREATE));

                    if (!wait) {
                        return Mono.just(ExecutionResponse.fromExecution(
                            executionWithInputs,
                            executionUrl(executionWithInputs)
                        ));
                    }

                    String subscriberId = UUID.randomUUID().toString();
                    // Use Flux to wait for completion using the streaming service
                    return Flux.<Event<Execution>>create(emitter -> {
                            streamingService.registerSubscriber(
                                executionWithInputs.getId(),
                                subscriberId,
                                emitter,
                                flow
                            );
                        })
                        .last()
                        .map(Event::getData)
                        .map(execution -> ExecutionResponse.fromExecution(
                            execution,
                            executionUrl(execution)
                        ))
                        .doFinally(signalType -> streamingService.unregisterSubscriber(executionWithInputs.getId(), subscriberId));
                } catch (QueueException e) {
                    return Mono.error(e);
                }
            });
    }

    private URI executionUrl(Execution execution) {
        String baseUrl = kestraUrl.map(url -> url.endsWith("/") ? url.substring(0, url.length() - 1) : url).orElse("");
        return URI.create(baseUrl + "/ui" + (execution.getTenantId() != null ? "/" + execution.getTenantId() : "")
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
        ExecutionResponse(String tenantId, String id, String namespace, String flowId, Integer flowRevision, List<TaskRun> taskRunList, Map<String, Object> inputs, Map<String, Object> outputs, List<Label> labels, Map<String, Object> variables, State state, String parentId, String originalId, ExecutionTrigger trigger, boolean deleted, ExecutionMetadata metadata, Instant scheduleDate, String traceParent, List<TaskFixture> fixtures, ExecutionKind kind, List<Breakpoint> breakpoints, URI url) {
            super(tenantId, id, namespace, flowId, flowRevision, taskRunList, inputs, outputs, labels, variables, state, parentId, originalId, trigger, deleted, metadata, scheduleDate, traceParent, fixtures,kind, breakpoints);

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
                execution.getOutputs(),
                execution.getLabels(),
                execution.getVariables(),
                execution.getState(),
                execution.getParentId(),
                execution.getOriginalId(),
                execution.getTrigger(),
                execution.isDeleted(),
                execution.getMetadata(),
                execution.getScheduleDate(),
                execution.getTraceParent(),
                execution.getFixtures(),
                execution.getKind(),
                execution.getBreakpoints(),
                url
            );
        }
    }

    protected List<Label> parseLabels(List<String> labels) {
        List<Label> parsedLabels = labels == null ? Collections.emptyList() : RequestUtils.toMap(labels).entrySet().stream()
            .map(entry -> new Label(entry.getKey(), entry.getValue()))
            .toList();

        // check for system labels: none can be passed at execution creation time except system.correlationId
        Optional<Label> first = parsedLabels.stream().filter(label -> !label.key().equals(CORRELATION_ID) && label.key().startsWith(SYSTEM_PREFIX)).findFirst();
        if (first.isPresent()) {
            throw new IllegalArgumentException("System labels can only be set by Kestra itself, offending label: " + first.get().key() + "=" + first.get().value());
        }
        return parsedLabels;
    }

    protected <T> HttpResponse<T> validateFile(Execution execution, URI path, String redirect) {
        String prefix = StorageContext
            .forExecution(execution)
            .getExecutionStorageURI().getPath();

        if (path.getPath().startsWith(prefix)) {
            return null;
        }

        // IMPORTANT NOTE: we load the flow here, this will trigger RBAC checks for FLOW permission!
        // This MUST NOT be done before as a user with only execution permission should be able to access flow files.
        String flowId = execution.getFlowId();
        Optional<Flow> flow = flowRepository.findById(execution.getTenantId(), execution.getNamespace(), flowId);
        if (flow.isEmpty()) {
            throw new NoSuchElementException("Unable to find flow id '" + flowId + "'");
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
    public HttpResponse<StreamedFile> downloadFileFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path
    ) throws IOException, URISyntaxException {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            throw new NoSuchElementException("Unable to find execution id '" + executionId + "'");
        }

        HttpResponse<StreamedFile> httpResponse = this.validateFile(execution.get(), path, "/api/v1/" + this.getTenant() + "executions/{executionId}/file?path=" + path);
        if (httpResponse != null) {
            return httpResponse;
        }

        InputStream fileHandler = storageInterface.get(execution.get().getTenantId(), execution.get().getNamespace(), path);
        return HttpResponse.ok(new StreamedFile(fileHandler, MediaType.APPLICATION_OCTET_STREAM_TYPE)
            .attach(FilenameUtils.getName(path.toString()))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/file/metas")
    @Operation(tags = {"Executions"}, summary = "Get file meta information for an execution")
    public HttpResponse<FileMetas> getFileMetadatasFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path
    ) throws IOException {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            throw new NoSuchElementException("Unable to find execution id '" + executionId + "'");
        }

        HttpResponse<FileMetas> httpResponse = this.validateFile(execution.get(), path, "/api/v1/" + this.getTenant() + "executions/{executionId}/file/metas?path=" + path);
        if (httpResponse != null) {
            return httpResponse;
        }

        return HttpResponse.ok(FileMetas.builder()
            .size(storageInterface.getAttributes(execution.get().getTenantId(), execution.get().getNamespace(), path).getSize())
            .build()
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/restart")
    @Operation(tags = {"Executions"}, summary = "Restart a new execution from an old one")
    public Execution restartExecution(
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
    public MutableHttpResponse<?> restartExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId
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
    public HttpResponse<?> restartExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws Exception {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);
        return restartExecutionsByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/replay")
    @Operation(tags = {"Executions"}, summary = "Create a new execution from an old one and start it from a specified task run id")
    public Execution replayExecution(
        @Parameter(description = "the original execution id to clone") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue Integer revision,
        @Parameter(description = "Set a list of breakpoints at specific tasks 'id.value', separated by a coma.") @QueryValue Optional<String> breakpoints
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }

        this.controlRevision(execution.get(), revision);

        return innerReplay(execution.get(), taskRunId, revision, breakpoints);
    }

    private Execution innerReplay(Execution execution, @Nullable String taskRunId, @Nullable Integer revision, Optional<String> breakpoints) throws Exception {
        Execution replay = executionService.replay(execution, taskRunId, revision)
            .withBreakpoints(breakpoints.map(s -> Arrays.stream(s.split(",")).map(Breakpoint::of).toList()).orElse(null));
        executionQueue.emit(replay);
        eventPublisher.publishEvent(new CrudEvent<>(replay, execution, CrudEventType.CREATE));

        // update parent exec with replayed label
        List<Label> newLabels = new ArrayList<>(execution.getLabels());
        if (!newLabels.contains(new Label(Label.REPLAYED, "true"))) {
            newLabels.add(new Label(Label.REPLAYED, "true"));
        }
        Execution newExecution = execution.withLabels(newLabels);
        eventPublisher.publishEvent(new CrudEvent<>(newExecution, execution, CrudEventType.UPDATE));
        executionRepository.save(newExecution);

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
    public Execution updateTaskRunState(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "the taskRun id and state to apply") @Body StateRequest stateRequest
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }

        Flow flow = flowRepository.findByExecution(execution.get());

        Execution replay = executionService.markAs(execution.get(), flow, stateRequest.getTaskRunId(), stateRequest.getState());
        List<Label> newLabels = new ArrayList<>(replay.getLabels());
        if (!newLabels.contains(new Label(Label.RESTARTED, "true"))) {
            newLabels.add(new Label(Label.RESTARTED, "true"));
        }
        replay = replay.withLabels(newLabels);
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
    @Operation(tags = {"Executions"}, summary = "Change the state of an execution")
    public Execution updateExecutionStatus(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The new state of the execution") @NotNull @QueryValue State.Type status
    ) throws QueueException {
        if (!status.isTerminated()) {
            throw new IllegalArgumentException("You can only change the state of an execution to a terminal state.");
        }

        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }

        if (!execution.get().getState().isTerminated()) {
            throw new IllegalArgumentException("You can only change the state of a terminated execution.");
        }

        Execution updated = execution.get().withState(status);

        executionQueue.emit(updated);
        eventPublisher.publishEvent(new CrudEvent<>(updated, execution.get(), CrudEventType.UPDATE));

        return updated;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/change-status/by-ids")
    @Operation(tags = {"Executions"}, summary = "Change executions state by id")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Changed state with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public HttpResponse<?> updateExecutionsStatusByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId,
        @Parameter(description = "The new state of the executions") @NotNull @QueryValue State.Type newStatus
    ) throws QueueException {
        if (!newStatus.isTerminated()) {
            throw new IllegalArgumentException("You can only change the state of an execution to a terminal state.");
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
                .message("invalid bulk change executions state")
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
    @Operation(tags = {"Executions"}, summary = "Change executions state by query parameters")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Changed state with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public HttpResponse<?> updateExecutionsStatusByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter,
        @Parameter(description = "The new state of the executions") @NotNull @QueryValue State.Type newStatus
    ) throws QueueException {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return updateExecutionsStatusByIds(ids, newStatus);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/{executionId}/kill{?isOnKillCascade}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Executions"}, summary = "Kill an execution")
    @ApiResponse(responseCode = "202", description = "Execution kill was requested successfully")
    @ApiResponse(responseCode = "409", description = "if the executions is already finished")
    @ApiResponse(responseCode = "404", description = "if the executions is not found")
    public HttpResponse<?> killExecution(
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
    public MutableHttpResponse<?> killExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId
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
    public Publisher<ApiValidateExecutionInputsResponse> validateResumeExecutionInputs(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "The inputs") @Nullable @Body MultipartBody inputs
    ) {
        Execution execution = executionService.getExecutionIfPause(tenantService.resolveTenant(), executionId, true);
        Flow flow = flowRepository.findByExecutionWithoutAcl(execution);

        return executionService.validateForResume(execution, flow, inputs)
            .map(values -> ApiValidateExecutionInputsResponse.of(execution.getFlowId(), execution.getNamespace(), values))
            // need to consume the inputs in case of error
            .doOnError(t -> Flux.from(inputs).subscribeOn(Schedulers.boundedElastic()).blockLast());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/resume", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = {"Executions"}, summary = "Resume a paused execution.")
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "if the executions is not paused")
    @SingleResult
    public Publisher<HttpResponse<?>> resumeExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "The inputs") @Nullable @Body MultipartBody inputs
    ) throws Exception {
        Execution execution = executionService.getExecutionIfPause(tenantService.resolveTenant(), executionId, true);
        Flow flow = flowRepository.findByExecutionWithoutAcl(execution);
        Pause.Resumed resumed = createResumed();

        return this.executionService.resume(execution, flow, State.Type.RUNNING, inputs, resumed)
            .<HttpResponse<?>>handle((resumeExecution, sink) -> {
                try {
                    this.executionQueue.emit(resumeExecution);
                    sink.next(HttpResponse.noContent());
                } catch (QueueException e) {
                    sink.error(e);
                }
            })
            // need to consume the inputs in case of error
            .doOnError(t -> Flux.from(inputs).subscribeOn(Schedulers.boundedElastic()).blockLast());
    }

    protected Pause.Resumed createResumed() {
        return Pause.Resumed.now();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/resume-from-breakpoint")
    @Operation(tags = {"Executions"}, summary = "Resume an execution from a breakpoint (in the 'BREAKPOINT' state).")
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "If the executions is not in the 'BREAKPOINT' state or has no breakpoint")
    public void resumeExecutionFromBreakpoint(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "\"Set a list of breakpoints at specific tasks 'id.value', separated by a coma.") @QueryValue Optional<String> breakpoints
    ) throws Exception {
        Execution execution = executionService.getExecution(tenantService.resolveTenant(), executionId, true);
        if (!execution.getState().isBreakpoint()) {
            throw new IllegalStateException("Execution is not suspended");
        }
        if (ListUtils.isEmpty(execution.getBreakpoints())) {
            throw new IllegalStateException("Execution has no breakpoint");
        }

        // continue the execution: SUSPENDED taskrun will go back to CREATED, so the executor will send them to the WORKER
        List<TaskRun> newTaskRuns = execution.getTaskRunList().stream().map(
            taskRun -> {
                if (taskRun.getState().isBreakpoint()) {
                    return taskRun.withState(State.Type.CREATED);
                }
                return taskRun;
            }
        ).toList();
        Execution newExecution = execution.withState(State.Type.RUNNING)
            .withTaskRunList(newTaskRuns)
            .withBreakpoints(breakpoints.map(s -> Arrays.stream(s.split(",")).map(Breakpoint::of).toList()).orElse(null));

        executionQueue.emit(newExecution);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/resume/by-ids")
    @Operation(tags = {"Executions"}, summary = "Resume a list of paused executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Resumed with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> resumeExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId
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
            Execution resumeExecution = this.executionService.resume(execution, flow, State.Type.RUNNING, createResumed());
            this.executionQueue.emit(resumeExecution);
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/resume/by-query")
    @Operation(tags = {"Executions"}, summary = "Resume executions filter by query parameters")
    public HttpResponse<?> resumeExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws Exception {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return resumeExecutionsByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/pause")
    @Operation(tags = {"Executions"}, summary = "Pause a running execution.")
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "if the executions is not running")
    public void pauseExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) throws Exception {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);

        Execution pausedExecution = this.executionService.pause(execution);
        this.executionQueue.emit(pausedExecution);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/pause/by-ids")
    @Operation(tags = {"Executions"}, summary = "Pause a list of running executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Paused with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> pauseExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId
    ) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && !execution.get().getState().isRunning()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution not in state RUNNING",
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
                .message("invalid bulk pause")
                .invalids(invalids)
                .build()
            );
        }

        for (Execution execution : executions) {
            Execution pausedExecution = this.executionService.pause(execution);
            this.executionQueue.emit(pausedExecution);
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/pause/by-query")
    @Operation(tags = {"Executions"}, summary = "Pause executions filter by query parameters")
    public HttpResponse<?> pauseExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws Exception {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return pauseExecutionsByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/kill/by-query")
    @Operation(tags = {"Executions"}, summary = "Kill executions filter by query parameters")
    public HttpResponse<?> killExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws QueueException {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return killExecutionsByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/replay/by-query")
    @Operation(tags = {"Executions"}, summary = "Create new executions from old ones filter by query parameters. Keep the flow revision")
    public HttpResponse<?> replayExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter,

        @Parameter(description = "If latest revision should be used") @Nullable @QueryValue(defaultValue = "false") Boolean latestRevision
    ) throws Exception {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return replayExecutionsByIds(ids, latestRevision);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/replay/by-ids")
    @Operation(tags = {"Executions"}, summary = "Create new executions from old ones. Keep the flow revision")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Replayed with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> replayExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId,
        @Parameter(description = "If latest revision should be used") @Nullable @QueryValue(defaultValue = "false") Boolean latestRevision
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
            if (latestRevision) {
                Flow flow = flowRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), Optional.empty()).orElseThrow();
                innerReplay(execution, null, flow.getRevision(), Optional.empty());
            } else {
                innerReplay(execution, null, null, Optional.empty());
            }
        }
        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = {"Executions"}, summary = "Follow an execution")
    public Flux<Event<Execution>> followExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) {
        String subscriberId = UUID.randomUUID().toString();
        return Flux.<Event<Execution>>create(emitter -> {
                // Send initial event
                emitter.next(Event.of(Execution.builder().id(executionId).build()).id("start"));

                // Check if execution exists
                try {
                    Execution execution = Await.until(
                        () -> executionRepository.findById(tenantService.resolveTenant(), executionId).orElse(null),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(10)
                    );

                    Flow flow = flowRepository.findByExecutionWithoutAcl(execution);

                    // If execution is already complete, just send final state
                    if (streamingService.isStopFollow(flow, execution)) {
                        emitter.next(Event.of(execution).id("end"));
                        emitter.complete();
                        return;
                    }

                    // Send current state
                    emitter.next(Event.of(execution).id("progress"));

                    // Register for updates
                    streamingService.registerSubscriber(executionId, subscriberId, emitter, flow);
                } catch (TimeoutException e) {
                    emitter.error(new HttpStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find execution " + executionId));
                } catch (IllegalStateException e) {
                    emitter.error(new HttpStatusException(HttpStatus.NOT_FOUND,
                        "Unable to find flow for execution " + executionId));
                }
            }, FluxSink.OverflowStrategy.BUFFER)
            .doFinally(ignored -> streamingService.unregisterSubscriber(executionId, subscriberId));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/file/preview")
    @Operation(tags = {"Executions"}, summary = "Get file preview for an execution")
    public HttpResponse<?> previewFileFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path,
        @Parameter(description = "The max row returns") @QueryValue @Nullable Integer maxRows,
        @Parameter(description = "The file encoding as Java charset name. Defaults to UTF-8", example = "ISO-8859-1") @QueryValue(defaultValue = "UTF-8") String encoding
    ) throws IOException {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            throw new NoSuchElementException("Unable to find execution id '" + executionId + "'");
        }

        this.validateFile(execution.get(), path, "/api/v1/" + this.getTenant() + "executions/{executionId}/file?path=" + path);

        String extension = FilenameUtils.getExtension(path.toString());
        Optional<Charset> charset;

        try {
            charset = Optional.ofNullable(encoding).map(Charset::forName);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException("Unable to preview using encoding '" + encoding + "'");
        }

        try (InputStream fileStream = storageInterface.get(execution.get().getTenantId(), execution.get().getNamespace(), path)) {
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
    public HttpResponse<?> setLabelsOnTerminatedExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "The labels to add to the execution") @Body @NotNull @Valid List<Label> labels
    ) {
        Optional<Execution> maybeExecution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (maybeExecution.isEmpty()) {
            return HttpResponse.notFound();
        }

        Execution execution = maybeExecution.get();
        if (!execution.getState().getCurrent().isTerminated()) {
            return HttpResponse.badRequest("The execution is not terminated");
        }

        Execution newExecution = setLabelsOnTerminatedExecution(execution, labels);

        return HttpResponse.ok(newExecution);
    }

    private Execution setLabelsOnTerminatedExecution(Execution execution, List<Label> labels) {
        // check for system labels: none can be passed at runtime
        // as all existing labels will be passed here, we compare existing system label with the new one and fail if they are different

        List<Label> existingSystemLabels = ListUtils.emptyOnNull(execution.getLabels()).stream().filter(label -> label.key().startsWith(SYSTEM_PREFIX)).toList();
        Optional<Label> first = labels.stream().filter(label -> label.key().startsWith(SYSTEM_PREFIX)).filter(label -> !existingSystemLabels.contains(label)).findAny();
        if (first.isPresent()) {
            throw new IllegalArgumentException("System labels can only be set by Kestra itself, offending label: " + first.get().key() + "=" + first.get().value());
        }

        Map<String, String> newLabels = labels.stream().collect(Collectors.toMap(Label::key, Label::value));
        existingSystemLabels.forEach(
            label -> {
                // only add system labels
                if (!newLabels.containsKey(label.key())) {
                    newLabels.put(label.key(), label.value());
                }
            }
        );

        Execution newExecution = execution
            .withLabels(newLabels.entrySet().stream().map(entry -> new Label(entry.getKey(), entry.getValue())).filter(label -> !label.key().isEmpty() || !label.value().isEmpty()).toList());
        eventPublisher.publishEvent(new CrudEvent<>(newExecution, execution, CrudEventType.UPDATE));

        return executionRepository.save(newExecution);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/labels/by-ids")
    @Operation(tags = {"Executions"}, summary = "Set labels on a list of executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Killed with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> setLabelsOnTerminatedExecutionsByIds(
        @RequestBody(description = "The request containing a list of labels and a list of executions") @Body SetLabelsByIdsRequest setLabelsByIds
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

        executions.forEach(execution -> setLabelsOnTerminatedExecution(execution, ListUtils.concat(execution.getLabels(), setLabelsByIds.executionLabels())));
        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    public record SetLabelsByIdsRequest(List<String> executionsId, List<Label> executionLabels) {
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/labels/by-query")
    @Operation(tags = {"Executions"}, summary = "Set label on executions filter by query parameters")
    public HttpResponse<?> setLabelsOnTerminatedExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter,

        @RequestBody(description = "The labels to add to the execution") @Body @NotNull @Valid List<Label> setLabels
    ) {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return setLabelsOnTerminatedExecutionsByIds(new SetLabelsByIdsRequest(ids, setLabels));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/unqueue")
    @Operation(tags = {"Executions"}, summary = "Unqueue an execution")
    public Execution unqueueExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The new state of the execution") @Nullable @QueryValue State.Type state
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }

        Execution restart = concurrencyLimitService.unqueue(execution.get(), state);
        executionQueue.emit(restart);
        eventPublisher.publishEvent(new CrudEvent<>(restart, execution.get(), CrudEventType.UPDATE));

        return restart;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unqueue/by-ids")
    @Operation(tags = {"Executions"}, summary = "Unqueue a list of executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Unqueued with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> unqueueExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId,
        @Parameter(description = "The new state of the unqueued executions") @Nullable @QueryValue State.Type state
    ) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);

            if (execution.isPresent() && execution.get().getState().getCurrent() != State.Type.QUEUED) {
                invalids.add(ManualConstraintViolation.of(
                    "execution not in state QUEUED",
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
                .message("invalid bulk unqueue")
                .invalids(invalids)
                .build()
            );
        }
        for (Execution execution : executions) {
            Execution restart = concurrencyLimitService.unqueue(execution, state);
            executionQueue.emit(restart);
            eventPublisher.publishEvent(new CrudEvent<>(restart, execution, CrudEventType.UPDATE));
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unqueue/by-query")
    @Operation(tags = {"Executions"}, summary = "Unqueue executions filter by query parameters")
    public HttpResponse<?> unqueueExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter,
        @Parameter(description = "The new state of the unqueued executions") @Nullable @QueryValue State.Type newState
    ) throws Exception {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return unqueueExecutionsByIds(ids, newState);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/force-run")
    @Operation(tags = {"Executions"}, summary = "Force run an execution")
    public Execution forceRunExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId
    ) throws Exception {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }

        Execution restart = executionService.forceRun(execution.get());
        executionQueue.emit(restart);
        eventPublisher.publishEvent(new CrudEvent<>(restart, execution.get(), CrudEventType.UPDATE));

        return restart;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/force-run/by-ids")
    @Operation(tags = {"Executions"}, summary = "Force run a list of executions")
    @ApiResponse(responseCode = "200", description = "On success", content = {@Content(schema = @Schema(implementation = BulkResponse.class))})
    @ApiResponse(responseCode = "422", description = "Force run with errors", content = {@Content(schema = @Schema(implementation = BulkErrorResponse.class))})
    public MutableHttpResponse<?> forceRunByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId
    ) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);

            if (execution.isPresent() && execution.get().getState().isTerminated()) {
                invalids.add(ManualConstraintViolation.of(
                    "execution in a terminated state",
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
                .message("invalid bulk force run")
                .invalids(invalids)
                .build()
            );
        }
        for (Execution execution : executions) {
            Execution forceRun = executionService.forceRun(execution);
            executionQueue.emit(forceRun);
            eventPublisher.publishEvent(new CrudEvent<>(forceRun, execution, CrudEventType.UPDATE));
        }

        return HttpResponse.ok(BulkResponse.builder().count(executions.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/force-run/by-query")
    @Operation(tags = {"Executions"}, summary = "Force run executions filter by query parameters")
    public HttpResponse<?> forceRunExecutionsByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "The scope of the executions to include") @Nullable @QueryValue(value = "scope") List<FlowScope> scope,
        @Deprecated @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Deprecated @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Deprecated @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Deprecated @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Deprecated @Parameter(description = "A time range filter relative to the current time", examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Deprecated @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Deprecated @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Deprecated @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Deprecated @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) throws Exception {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            resolveAbsoluteDateTime(startDate, timeRange, ZonedDateTime.now()),
            endDate,
            scope,
            labels,
            timeRange,
            childFilter,
            state,
            null,
            triggerExecutionId
        );

        var ids = getExecutionIds(filters);

        return forceRunByIds(ids);
    }

    private List<String> getExecutionIds(List<QueryFilter> filters) {
        return executionRepository
            .find(
                Pageable.UNPAGED,
                tenantService.resolveTenant(),
                filters
            ).map(Execution::getId);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/flow")
    @Operation(tags = {"Executions"}, summary = "Get flow information's for an execution")
    public FlowForExecution getFlowFromExecutionById(
        @Parameter(description = "The execution that you want flow information's") String executionId
    ) {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow();

        return FlowForExecution.of(flowRepository.findByExecutionWithoutAcl(execution));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/flows/{namespace}/{flowId}")
    @Operation(tags = {"Executions"}, summary = "Get flow information's for an execution")
    public FlowForExecution getFlowFromExecution(
        @Parameter(description = "The namespace of the flow") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The flow revision") @Nullable Integer revision
    ) {

        return FlowForExecution.of(flowRepository.findByIdWithoutAcl(tenantService.resolveTenant(), namespace, flowId, Optional.ofNullable(revision)).orElseThrow());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/namespaces")
    @Operation(tags = {"Executions"}, summary = "Get all namespaces that have executable flows")
    public List<String> listExecutableDistinctNamespaces() {
        return flowRepository.findDistinctNamespaceExecutable(tenantService.resolveTenant());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/namespaces/{namespace}/flows")
    @Operation(tags = {"Executions"}, summary = "Get all flow ids for a namespace. Data returned are FlowForExecution containing minimal information about a Flow for when you are allowed to executing but not reading.")
    public List<FlowForExecution> listFlowExecutionsByNamespace(
        @Parameter(description = "The namespace") @PathVariable String namespace
    ) {
        return flowRepository.findByNamespaceExecutable(tenantService.resolveTenant(), namespace);
    }

    public String getTenant() {
        return tenantService.resolveTenant() != null ? tenantService.resolveTenant() + "/" : "";
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/latest")
    @Operation(tags = {"Executions"}, summary = "Get the latest execution for given flows")
    public List<LastExecutionResponse> getLatestExecutions(
        @Parameter(description = "The flow filters") @Body List<ExecutionRepositoryInterface.FlowFilter> flowFilters
    ) {
        return executionRepository.lastExecutions(
            tenantService.resolveTenant(),
            flowFilters
        ).stream().map(LastExecutionResponse::ofExecution).toList();
    }

    @Introspected
    public record LastExecutionResponse(
        @Parameter(description = "The flow's ID") String flowId,
        @Parameter(description = "The namespace") String namespace,
        @Parameter(description = "The start date") Instant startDate,
        @Parameter(description = "The status") State.Type status
    ) {

        public static LastExecutionResponse ofExecution(Execution execution) {
            return new LastExecutionResponse(
                execution.getFlowId(),
                execution.getNamespace(),
                execution.getState().getStartDate(),
                execution.getState().getCurrent()
            );
        }
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
