package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.debug.Breakpoint;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.*;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.flows.input.InputAndValue;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.storage.FileMetas;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.contexts.configuration.KestraConfiguration;
import io.kestra.core.runners.configuration.LocalFilesConfiguration;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.services.*;
import io.kestra.core.services.ExecutionStreamingService;
import io.kestra.core.storages.*;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.trace.propagation.ExecutionTextMapSetter;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.Logs;
import io.kestra.plugin.core.trigger.AbstractWebhookTrigger;
import io.kestra.plugin.core.trigger.WebhookContext;
import io.kestra.plugin.core.trigger.WebhookResponse;
import io.kestra.webserver.configuration.AsyncOperationsConfiguration;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.models.api.ApiAsyncOperationResponse;
import io.kestra.webserver.models.api.ApiExecution;
import io.kestra.webserver.models.api.ApiLightExecution;
import io.kestra.webserver.responses.BulkErrorResponse;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.AsyncOperationWaiter;
import io.kestra.webserver.services.ExecutionDependenciesStreamingService;
import io.kestra.webserver.services.MicronautHttpService;
import io.kestra.webserver.utils.CSVUtils;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.QueryFilterUtils;
import io.kestra.webserver.utils.RequestUtils;
import io.kestra.webserver.utils.filepreview.FileRender;
import io.kestra.webserver.utils.filepreview.FileRenderBuilder;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
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
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import static io.kestra.core.models.Label.CORRELATION_ID;
import static io.kestra.core.models.Label.SYSTEM_PREFIX;
import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Slf4j
@Controller("/api/v1/{tenant}/executions")
public class ExecutionController {
    @Nullable
    @Value("${micronaut.server.context-path}")
    protected String basePath;

    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    private FlowService flowService;

    @Inject
    private NamespaceService namespaceService;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private GraphService graphService;

    @Inject
    private FlowInputOutput flowInputOutput;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    protected ExecutionService executionService;

    @Inject
    private ExecutionStreamingService streamingService;

    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private ExecutionDependenciesStreamingService executionDependenciesStreamingService;

    @Inject
    protected DispatchQueueInterface<Execution> executionQueue;

    @Inject
    protected BroadcastQueueInterface<ExecutionKilled> killQueue;

    @Inject
    protected DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @Inject
    private ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private TenantService tenantService;

    @Inject
    private Optional<OpenTelemetry> openTelemetry;

    @Inject
    private LocalPathFactory localPathFactory;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Inject
    private SecureVariableRendererFactory secureVariableRendererFactory;

    @Inject
    private ObjectMapper objectMapper;

    @Inject
    private ServerConfig serverConfig;

    @Inject
    private KestraConfiguration kestraConfiguration;

    @Inject
    private LocalFilesConfiguration localFilesConfiguration;

    @Inject
    private WebhookService webhookService;

    @Inject
    private AsyncOperationWaiter asyncOperationWaiter;

    @Inject
    private AsyncOperationsConfiguration asyncOperationsConfiguration;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = { "Executions" }, summary = "Search for executions")
    public PagedResults<ApiLightExecution> searchExecutions(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(
            description = "The sort of current page", examples = {
                @ExampleObject(name = "Sort by start date in ascending order", value = "state.startDate:asc"),
                @ExampleObject(name = "Sort by namespace in descending order", value = "namespace:desc"),
            }
        ) @Nullable @QueryValue List<String> sort,
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters

    ) {
        var executions = executionRepository.find(
            PageableUtils.from(page, size, sort, executionRepository.sortMapping()),
            tenantService.resolveTenant(),
            QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters)
        );
        var apiExecution = executions.stream()
            .map(execution -> ApiLightExecution.of(execution))
            .toList();
        return PagedResults.of(new ArrayListTotal<>(apiExecution, executions.getTotal()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/graph")
    @Operation(tags = { "Executions" }, summary = "Generate a graph for an execution")
    public FlowGraph getExecutionFlowGraph(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The subflow tasks to display") @Nullable @QueryValue List<String> subflows) throws Exception {
        return executionRepository
            .findById(tenantService.resolveTenant(), executionId)
            .map(throwFunction(execution ->
            {
                Optional<FlowWithSource> flow = flowRepository.findByIdWithSourceWithoutAcl(
                    execution.getTenantId(),
                    execution.getNamespace(),
                    execution.getFlowId(),
                    Optional.of(execution.getFlowRevision())
                );

                return flow
                    .map(
                        throwFunction(
                            value -> graphService.flowGraph(value, subflows, execution).forExecution()
                        )
                    )
                    .orElse(null);
            }))
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/eval", consumes = MediaType.TEXT_PLAIN)
    @Operation(tags = { "Executions" }, summary = "Evaluate a variable expression for this execution")
    public EvalResult evalExpression(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "The Pebble expression that should be evaluated") @Body String expression) {
        Execution execution = executionRepository
            .findById(tenantService.resolveTenant(), executionId)
            .orElseThrow(() -> new NoSuchElementException("Unable to find execution '" + executionId + "'"));

        Flow flow = flowRepository
            .findByExecution(execution);

        try {
            return EvalResult.builder()
                .result(runContextRender(flow, execution, expression))
                .build();
        } catch (IllegalVariableEvaluationException e) {
            return EvalResult.builder()
                .error(e.getMessage())
                .stackTrace(ExceptionUtils.getStackTrace(e))
                .build();
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/eval/{taskRunId}", consumes = MediaType.TEXT_PLAIN)
    @Operation(tags = { "Executions" }, summary = "Evaluate a variable expression for this taskrun")
    public EvalResult evalTaskRunExpression(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @PathVariable String taskRunId,
        @RequestBody(description = "The Pebble expression that should be evaluated") @Body String expression) throws InternalException {
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

    private String runContextRender(Flow flow, Execution execution, String expression) throws IllegalVariableEvaluationException {
        return runContextFactory.of(
            flow,
            execution,
            false
        ).render(expression);
    }

    private String runContextRender(Flow flow, Task task, Execution execution, TaskRun taskRun, String expression) throws IllegalVariableEvaluationException {
        return runContextFactory.of(
            flow,
            task,
            execution,
            taskRun,
            false,
            secureVariableRendererFactory.createOrGet()
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
    @Operation(tags = { "Executions" }, summary = "Get an execution")
    public ApiExecution getExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId) {
        return executionRepository
            .findById(tenantService.resolveTenant(), executionId)
            .map(ApiExecution::of)
            .orElse(null);
    }

    @Delete(uri = "/{executionId}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Executions" }, summary = "Delete an execution")
    @ApiResponse(responseCode = "204", description = "On success")
    public HttpResponse<Void> deleteExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "Whether to delete execution logs", required = false) @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics", required = false) @QueryValue(defaultValue = "true") Boolean deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage", required = false) @QueryValue(defaultValue = "true") Boolean deleteStorage) throws IOException {
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
    @Operation(tags = { "Executions" }, summary = "Delete a list of executions")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = BulkResponse.class)) })
    @ApiResponse(responseCode = "422", description = "Deleted with errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> deleteExecutionsByIds(
        @RequestBody(description = "The execution id") @Body List<String> executionsId,
        @Parameter(description = "Whether to delete non-terminated executions") @Nullable @QueryValue(defaultValue = "false") Boolean includeNonTerminated,
        @Parameter(description = "Whether to delete execution logs", required = false) @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics", required = false) @QueryValue(defaultValue = "true") Boolean deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage", required = false) @QueryValue(defaultValue = "true") Boolean deleteStorage) throws IOException {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && (execution.get().getState().isTerminated() || includeNonTerminated)) {
                executions.add(execution.get());
            } else {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            }
        }
        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest()
                .body(
                    BulkErrorResponse
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
    @Operation(tags = { "Executions" }, summary = "Delete executions filter by query parameters")
    public HttpResponse<?> deleteExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters,

        @Parameter(description = "Whether to delete non-terminated executions") @Nullable @QueryValue(defaultValue = "false") Boolean includeNonTerminated,
        @Parameter(description = "Whether to delete execution logs", required = false) @QueryValue(defaultValue = "true") Boolean deleteLogs,
        @Parameter(description = "Whether to delete execution metrics", required = false) @QueryValue(defaultValue = "true") Boolean deleteMetrics,
        @Parameter(description = "Whether to delete execution files in the internal storage", required = false) @QueryValue(defaultValue = "true") Boolean deleteStorage) throws IOException {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

        return deleteExecutionsByIds(ids, includeNonTerminated, deleteLogs, deleteMetrics, deleteStorage);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = { "Executions" }, summary = "Search for executions for a flow")
    public PagedResults<ApiLightExecution> searchExecutionsByFlowId(
        @Parameter(description = "The flow namespace") @QueryValue String namespace,
        @Parameter(description = "The flow id") @QueryValue String flowId,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size) {
        var executions = executionRepository.findByFlowId(tenantService.resolveTenant(), namespace, flowId, PageableUtils.from(page, size));
        var apiExecution = executions.stream()
            .map(execution -> ApiLightExecution.of(execution))
            .toList();
        return PagedResults.of(new ArrayListTotal<>(apiExecution, executions.getTotal()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/webhook/{namespace}/{id}/{key}{/path}", consumes = { MediaType.ALL })
    @Operation(tags = { "Executions" }, summary = "Trigger a new execution by POST webhook trigger")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = WebhookResponse.class)) })
    @SingleResult
    public Mono<HttpResponse<?>> triggerExecutionByPostWebhook(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
        @Parameter(description = "Optional additional path segments") @Nullable @PathVariable String path,
        HttpRequest<String> request) throws IllegalVariableEvaluationException {
        return this.webhook(namespace, id, key, path, request);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/webhook/{namespace}/{id}/{key}{/path}", consumes = { MediaType.ALL })
    @Operation(tags = { "Executions" }, summary = "Trigger a new execution by GET webhook trigger")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = WebhookResponse.class)) })
    @SingleResult
    public Mono<HttpResponse<?>> triggerExecutionByGetWebhook(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
        @Parameter(description = "Optional additional path segments") @Nullable @PathVariable String path,
        HttpRequest<String> request) throws IllegalVariableEvaluationException {
        return this.webhook(namespace, id, key, path, request);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/webhook/{namespace}/{id}/{key}{/path}", consumes = { MediaType.ALL })
    @Operation(tags = { "Executions" }, summary = "Trigger a new execution by PUT webhook trigger")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = WebhookResponse.class)) })
    @SingleResult
    public Mono<HttpResponse<?>> triggerExecutionByPutWebhook(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The webhook trigger uid") @PathVariable String key,
        @Parameter(description = "Optional additional path segments") @Nullable @PathVariable String path,
        HttpRequest<String> request) throws IllegalVariableEvaluationException {
        return this.webhook(namespace, id, key, path, request);
    }

    private Mono<HttpResponse<?>> webhook(
        String namespace,
        String id,
        String key,
        String path,
        HttpRequest<String> request) throws IllegalVariableEvaluationException {
        Optional<Flow> find = flowRepository.findById(tenantService.resolveTenant(), namespace, id);
        return webhook(find, key, path, request);
    }

    protected Mono<HttpResponse<?>> webhook(
        Optional<Flow> maybeFlow,
        String key,
        String path,
        HttpRequest<String> request) throws IllegalVariableEvaluationException {
        if (maybeFlow.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Flow not found");
        }

        var flow = maybeFlow.get();
        if (flow.isDisabled()) {
            throw new ConflictException("Cannot execute flow: flow is disabled.");
        }
        if (flow instanceof FlowWithException fwe) {
            throw new ConflictException("Cannot execute flow: flow is invalid: " + fwe.getException());
        }

        Optional<AbstractWebhookTrigger> maybeWebhook = (flow.getTriggers() == null ? new ArrayList<AbstractTrigger>()
            : flow
                .getTriggers())
            .stream()
            .filter(o -> o instanceof AbstractWebhookTrigger)
            .map(o -> (AbstractWebhookTrigger) o)
            .filter(w ->
            {
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

        if (maybeWebhook.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Webhook not found");
        }

        final AbstractWebhookTrigger webhook = maybeWebhook.get();

        // Webhook context
        var webhookContext = new WebhookContext(
            MicronautHttpService.from(request),
            path,
            flow,
            webhook,
            webhookService
        );

        // Call evaluate and create a failed execution if exception occurs
        try {
            return webhook.evaluate(webhookContext).map(MicronautHttpService::to);
        } catch (Exception e) {
            Execution failedExecution = Execution.builder()
                .id(IdUtils.create())
                .tenantId(flow.getTenantId())
                .namespace(flow.getNamespace())
                .flowId(flow.getId())
                .flowRevision(flow.getRevision())
                .labels(LabelService.labelsExcludingSystem(flow.getLabels()))
                .state(new State().withState(State.Type.FAILED))
                .trigger(ExecutionTrigger.of(webhook, Map.of()))
                .build();

            Logger logger = webhookContext.webhookService().runContext(flow, failedExecution).logger();
            logger.error("[trigger: {}] Webhook evaluate Failed with error '{}'", webhookContext.trigger(), e.getMessage());

            try {
                this.executionQueue.emit(failedExecution);
            } catch (QueueException ex) {
                log.error("Unable to emit the execution", ex);
            }

            return Mono.just(HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{id}/validate", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = { "Executions" }, summary = "Validate the creation of a new execution for a flow")
    @ApiResponse(responseCode = "409", description = "if the flow is disabled")
    @SingleResult
    public Publisher<ApiValidateExecutionInputsResponse> validateNewExecutionInputs(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @RequestBody(description = "The inputs") @Nullable @Body MultipartBody inputs,
        @Parameter(description = "The labels as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The flow revision or latest if null") @QueryValue Optional<Integer> revision) {
        Flow flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), namespace, id, revision);
        List<Label> parsedLabels = parseLabels(labels);
        Execution execution = Execution.newExecution(flow, parsedLabels);
        return flowInputOutput
            .validateExecutionInputs(flow.getInputs(), flow, execution, inputs)
            .map(values ->
            {
                Map<String, Object> inputsAsMap = values.stream().collect(HashMap::new, (m, v) -> m.put(v.input().getId(), v.value()), HashMap::putAll);
                List<Check> checks = flowService.getFailedChecks(flow, inputsAsMap);
                return ApiValidateExecutionInputsResponse.of(id, namespace, checks, values);
            });
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{id}", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(
        tags = { "Executions" },
        summary = "Create a new execution for a flow",
        extensions = @Extension(
            name = "x-sdk-customization",
            properties = {
                @ExtensionProperty(name = "x-multipart", value = "true")
            }
        )
    )
    @ApiResponse(responseCode = "409", description = "if the flow is disabled")
    @ApiResponse(responseCode = "200", description = "On execution created", content = { @Content(schema = @Schema(implementation = ExecutionResponse.class)) })
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
        @Parameter(description = "Specific execution kind") @QueryValue Optional<ExecutionKind> kind) {
        Flow flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), namespace, id, revision);
        List<Label> parsedLabels = parseLabels(labels);
        final Execution current = Execution.newExecution(flow, null, parsedLabels, scheduleDate).toBuilder()
            .kind(kind.orElse(null))
            .breakpoints(breakpoints.map(s -> Arrays.stream(s.split(",")).map(Breakpoint::of).toList()).orElse(null))
            .build();

        return flowInputOutput.readExecutionInputs(flow, current, inputs)
            .flatMap(executionInputs ->
            {
                List<Check> failed = flowService.getFailedChecks(flow, executionInputs);
                Check.Behavior behavior = Check.resolveBehavior(failed);
                if (Check.Behavior.BLOCK_EXECUTION.equals(behavior)) {
                    return Mono.error(
                        new IllegalArgumentException(
                            "Flow execution blocked: one or more condition checks evaluated to false."
                                + "\nFailed checks: " + failed.stream().map(Check::getMessage).collect(
                                    Collectors.joining(", ")
                                )
                        )
                    );
                }

                final Execution executionWithInputs = Optional.of(current.withInputs(executionInputs))
                    .map(exec ->
                    {
                        if (Check.Behavior.FAIL_EXECUTION.equals(behavior)) {
                            Logs.logExecution(current, log, Level.WARN, "Flow execution failed because one or more condition checks evaluated to false.");
                            return exec.withState(State.Type.FAILED);
                        } else {
                            return exec;
                        }
                    })
                    .get();

                try {
                    // inject the traceparent into the execution
                    openTelemetry
                        .map(OpenTelemetry::getPropagators)
                        .map(ContextPropagators::getTextMapPropagator)
                        .ifPresent(propagator -> propagator.inject(Context.current(), executionWithInputs, ExecutionTextMapSetter.INSTANCE));

                    executionQueue.emit(executionWithInputs);
                    eventPublisher.publishEvent(CrudEvent.create(executionWithInputs));

                    if (!wait || executionWithInputs.getState().isFailed()) {
                        return Mono.just(
                            ExecutionResponse.fromExecution(
                                executionWithInputs,
                                executionUrl(executionWithInputs)
                            )
                        );
                    }

                    String subscriberId = UUID.randomUUID().toString();
                    // Use Flux to wait for completion using the streaming service
                    return Flux.<Event<Execution>> create(emitter ->
                    {
                        streamingService.registerSubscriber(
                            executionWithInputs.getId(),
                            subscriberId,
                            emitter,
                            flow
                        );
                    })
                        .last()
                        .map(Event::getData)
                        .map(
                            execution -> ExecutionResponse.fromExecution(
                                execution,
                                executionUrl(execution)
                            )
                        )
                        .timeout(Duration.ofHours(1)) // avoid idle SSE sockets by setting a between-item timeout
                        .doFinally(signalType -> streamingService.unregisterSubscriber(executionWithInputs.getId(), subscriberId));
                } catch (QueueException e) {
                    return Mono.error(e);
                }
            });
    }

    private URI executionUrl(Execution execution) {
        String baseUrl = Optional.ofNullable(kestraConfiguration.url()).map(url -> url.endsWith("/") ? url.substring(0, url.length() - 1) : url).orElse("");
        return URI.create(
            baseUrl + "/ui" + (execution.getTenantId() != null ? "/" + execution.getTenantId() : "")
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
        ExecutionResponse(String tenantId, String id, String namespace, String flowId, Integer flowRevision, List<TaskRun> taskRunList, Map<String, Object> inputs, Map<String, Object> outputs,
            List<Label> labels, Map<String, Object> variables, State state, String parentId, String originalId, ExecutionTrigger trigger, boolean deleted, ExecutionMetadata metadata,
            Instant scheduleDate, String traceParent, List<TaskFixture> fixtures, ExecutionKind kind, List<Breakpoint> breakpoints, LoopRun loopRun, URI url) {
            super(
                tenantId, id, namespace, flowId, flowRevision, taskRunList, inputs, outputs, labels, variables, state, parentId, originalId, trigger, deleted, metadata, scheduleDate,
                traceParent, fixtures, kind, breakpoints, loopRun
            );

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
                execution.getLoopRun(),
                url
            );
        }
    }

    protected List<Label> parseLabels(List<String> labels) {
        List<Label> parsedLabels = labels == null ? new ArrayList<>()
            : RequestUtils.toMap(labels).entrySet().stream()
                .map(entry -> new Label(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // check for system labels: none can be passed at execution creation time except system.correlationId and system.from
        Optional<Label> first = parsedLabels.stream().filter(label -> !label.key().equals(CORRELATION_ID) && !label.key().equals(Label.FROM) && label.key().startsWith(SYSTEM_PREFIX))
            .findFirst();
        if (first.isPresent()) {
            throw new IllegalArgumentException("System labels can only be set by Kestra itself, offending label: " + first.get().key() + "=" + first.get().value());
        }

        // from can be passed by the UI so we only add it if it didn't exist anymore
        // if we want to be more restrictive, we may want to restrict it to only have the `ui` value
        if (parsedLabels.stream().noneMatch(l -> l.key().equals(Label.FROM))) {
            parsedLabels.add(new Label(Label.FROM, "api"));
        }

        return parsedLabels;
    }

    protected <T> HttpResponse<T> validateFile(Execution execution, URI path, String redirect) {
        if (LocalPath.FILE_SCHEME.equals(path.getScheme())) {
            if (!localFilesConfiguration.enablePreview()) {
                throw new SecurityException("Local file preview is disabled");
            }
            return null;
        }

        if (Namespace.NAMESPACE_FILE_SCHEME.equals(path.getScheme())) {
            // if there is an authority, it means the namespace file is for another namespace, so we check it
            if (path.getAuthority() != null) {
                namespaceService.checkAllowedNamespace(execution.getTenantId(), path.getAuthority(), execution.getTenantId(), execution.getNamespace());
            }
            return null;
        }

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
            return HttpResponse.redirect(
                URI.create(
                    (basePath != null ? basePath : "") +
                        redirect.replace("{executionId}", redirectedExecution.get())
                )
            );
        }

        throw new IllegalArgumentException("Invalid prefix path");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/file", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = { "Executions" }, summary = "Download file for an execution")
    public HttpResponse<StreamedFile> downloadFileFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path) throws IOException, URISyntaxException {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            throw new NoSuchElementException("Unable to find execution id '" + executionId + "'");
        }

        HttpResponse<StreamedFile> httpResponse = this.validateFile(execution.get(), path, "/api/v1/" + this.getTenant() + "executions/{executionId}/file?path=" + path);
        if (httpResponse != null) {
            return httpResponse;
        }

        InputStream fileHandler = switch (path.getScheme()) {
            case StorageContext.KESTRA_SCHEME ->
                storageInterface.get(execution.get().getTenantId(), execution.get().getNamespace(), path);
            case LocalPath.FILE_SCHEME -> localPathFactory.createLocalPath().get(path);
            case Namespace.NAMESPACE_FILE_SCHEME -> {
                URI uri = nsFileToInternalStorageURI(path, execution.get());
                yield storageInterface.get(execution.get().getTenantId(), execution.get().getNamespace(), uri);
            }
            default -> throw new IllegalArgumentException("Scheme not supported: " + path.getScheme());
        };
        return HttpResponse.ok(
            new StreamedFile(fileHandler, MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .attach(FilenameUtils.getName(path.toString()))
        );
    }

    private URI nsFileToInternalStorageURI(URI path, Execution execution) throws IOException {
        Namespace namespace = namespaceFactory.of(execution.getTenantId(), execution.getNamespace(), storageInterface);
        return namespace.get(Path.of(path.getPath())).uri();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/file/metas")
    @Operation(tags = { "Executions" }, summary = "Get file meta information for an execution")
    public HttpResponse<FileMetas> getFileMetadatasFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path) throws IOException {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            throw new NoSuchElementException("Unable to find execution id '" + executionId + "'");
        }

        HttpResponse<FileMetas> httpResponse = this.validateFile(execution.get(), path, "/api/v1/" + this.getTenant() + "executions/{executionId}/file/metas?path=" + path);
        if (httpResponse != null) {
            return httpResponse;
        }

        long size = switch (path.getScheme()) {
            case StorageContext.KESTRA_SCHEME ->
                storageInterface.getAttributes(execution.get().getTenantId(), execution.get().getNamespace(), path).getSize();
            case LocalPath.FILE_SCHEME -> localPathFactory.createLocalPath().getAttributes(path).size();
            case Namespace.NAMESPACE_FILE_SCHEME -> {
                URI uri = nsFileToInternalStorageURI(path, execution.get());
                yield storageInterface.getAttributes(execution.get().getTenantId(), execution.get().getNamespace(), uri).getSize();
            }
            default -> throw new IllegalArgumentException("Scheme not supported: " + path.getScheme());
        };

        return HttpResponse.ok(
            FileMetas.builder()
                .size(size)
                .build()
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/restart")
    @Operation(tags = { "Executions" }, summary = "Restart a new execution from an old one")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the execution cannot be restarted")
    public Mono<HttpResponse<Execution>> restartExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue Integer revision) throws Exception {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);
        this.controlRevision(execution, revision);

        if (!(execution.getState().canBeRestarted())) {
            throw new ConflictException(
                "Cannot restart execution: current state is '" + execution.getState().getCurrent() + "', expected terminated or paused."
            );
        }

        return awaitBlockingAction(
            executionId, "Restart",
            operationId -> executionCommandQueue.emit(Restart.from(execution, revision).withOperationId(operationId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/restart/by-ids")
    @Operation(tags = { "Executions" }, summary = "Restart a list of executions asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> restartExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);

            if (execution.isPresent() && !execution.get().getState().canBeRestarted()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "Execution '" + execution.get().getId() + "' must be terminated or paused to be restarted, " +
                            "current state is '" + execution.get().getState().getCurrent() + "' !",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }
        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk restart")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(
            executions,
            (execution, opId) -> executionCommandQueue.emit(Restart.from(execution, null).withOperationId(opId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/restart/by-query")
    @Operation(tags = { "Executions" }, summary = "Restart executions filter by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public HttpResponse<?> restartExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters) throws Exception {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));
        return restartExecutionsByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/replay")
    @Operation(tags = { "Executions" }, summary = "Create a new execution from an old one and start it from a specified task run id")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the execution cannot be replayed")
    public HttpResponse<Execution> replayExecution(
        @Parameter(description = "the original execution id to clone") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue Integer revision,
        @Parameter(description = "Set a list of breakpoints at specific tasks 'id.value', separated by a coma.") @QueryValue Optional<String> breakpoints) throws Exception {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);

        this.controlRevision(execution, revision);

        Flow flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), execution.getNamespace(), execution.getFlowId(), Optional.ofNullable(revision));

        return blockingReplay(execution, flow, taskRunId, revision, breakpoints);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/replay-with-inputs", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(
        tags = { "Executions" },
        summary = "Create a new execution from an old one and start it from a specified task run id",
        extensions = @Extension(
            name = "x-sdk-customization",
            properties = {
                @ExtensionProperty(name = "x-multipart", value = "true")
            }
        )
    )
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the execution cannot be replayed")
    public Mono<HttpResponse<Execution>> replayExecutionWithinputs(
        @Parameter(description = "the original execution id to clone") @PathVariable String executionId,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The flow revision to use for new execution") @Nullable @QueryValue Integer revision,
        @Parameter(description = "Set a list of breakpoints at specific tasks 'id.value', separated by a coma.") @QueryValue Optional<String> breakpoints,
        @RequestBody(
            description = "The inputs (multipart map)",
            content = @Content(
                mediaType = MediaType.MULTIPART_FORM_DATA,
                schema = @Schema(
                    type = "object",
                    additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
                    additionalPropertiesSchema = Object.class
                )
            )
        ) @Body MultipartBody inputs) {
        Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (execution.isEmpty()) {
            return null;
        }
        Execution current = execution.get();

        this.controlRevision(current, revision);

        Flow flow = flowService.getFlowIfExecutableOrThrow(tenantService.resolveTenant(), current.getNamespace(), current.getFlowId(), Optional.ofNullable(revision));

        return flowInputOutput.readExecutionInputs(flow, current, inputs)
            .flatMap(newInputs -> Mono.fromCallable(() -> blockingReplay(current.withInputs(newInputs), flow, taskRunId, revision, breakpoints)));

    }

    private HttpResponse<Execution> blockingReplay(Execution execution, Flow flow, @Nullable String taskRunId, @Nullable Integer revision, Optional<String> breakpoints) throws Exception {
        if (taskRunId != null) {
            if (execution.getTaskRunList().stream().noneMatch(tr -> tr.getId().equals(taskRunId))) {
                throw new IllegalArgumentException("Task run id '" + taskRunId + "' not found in execution '" + execution.getId() + "'");
            }
        }

        var replayedExecution = executionService.replay(execution, flow, taskRunId, revision, breakpoints, true);

        AsyncOperationProcessedEvent processed;
        try {
            processed = asyncOperationWaiter.submitAndWait(
                execution.getId(),
                operationId ->
                {
                    try {
                        // emit the replayed execution (new run, no operationId tagging)
                        executionQueue.emit(replayedExecution);

                        // update parent exec with replayed label; tag with operationId for completion signal
                        List<Label> newLabels = new ArrayList<>(execution.getLabels());
                        if (!newLabels.contains(new Label(Label.REPLAYED, "true"))) {
                            newLabels.add(new Label(Label.REPLAYED, "true"));
                        }
                        executionCommandQueue.emit(UpdateLabels.from(execution, newLabels).withOperationId(operationId));
                    } catch (QueueException e) {
                        throw new RuntimeException(e);
                    }
                },
                asyncOperationsConfiguration.waitTimeout()
            );
        } catch (TimeoutException e) {
            throw new HttpStatusException(HttpStatus.GATEWAY_TIMEOUT, "Operation timed out waiting for state transition");
        }

        if (processed.outcome() == AsyncOperationProcessedEvent.Outcome.FAILED) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "Replay failed: " + processed.error());
        }

        return HttpResponse.ok(replayedExecution);
    }

    private void innerReplayBatch(Execution execution, Flow flow, @Nullable String taskRunId, @Nullable Integer revision, Optional<String> breakpoints, String operationId) throws Exception {
        if (taskRunId != null) {
            if (execution.getTaskRunList().stream().noneMatch(tr -> tr.getId().equals(taskRunId))) {
                throw new IllegalArgumentException("Task run id '" + taskRunId + "' not found in execution '" + execution.getId() + "'");
            }
        }

        var replayedExecution = executionService.replay(execution, flow, taskRunId, revision, breakpoints, true);
        executionQueue.emit(replayedExecution);

        // update parent exec with replayed label; tag with operationId for completion tracking
        List<Label> newLabels = new ArrayList<>(execution.getLabels());
        if (!newLabels.contains(new Label(Label.REPLAYED, "true"))) {
            newLabels.add(new Label(Label.REPLAYED, "true"));
        }
        executionCommandQueue.emit(UpdateLabels.from(execution, newLabels).withOperationId(operationId));
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
                throw new NoSuchElementException(
                    "Unable to find revision " + revision +
                        " on flow " + execution.getNamespace() + "." + execution.getFlowId()
                );
            }
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/state")
    @Operation(tags = { "Executions" }, summary = "Change state for a taskrun in an execution")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the task run state cannot be changed")
    public Mono<HttpResponse<Execution>> updateTaskRunState(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "the taskRun id and state to apply") @Body StateRequest stateRequest) throws Exception {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);

        if (!execution.getState().canChangeStatus()) {
            throw new ConflictException("Cannot change task run state: execution must be terminated and not killed.");
        }

        return awaitBlockingAction(
            executionId, "Change task run state",
            operationId -> executionCommandQueue.emit(ChangeTaskRunState.from(execution, stateRequest.taskRunId(), stateRequest.state()).withOperationId(operationId))
        );
    }

    public record StateRequest(
        @NotNull String taskRunId,
        @NotNull State.Type state) {
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/change-status")
    @Operation(tags = { "Executions" }, summary = "Change the state of an execution")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the execution state cannot be changed")
    public Mono<HttpResponse<Execution>> updateExecutionStatus(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The new state of the execution") @NotNull @QueryValue State.Type status) throws QueueException {
        if (!status.isTerminated()) {
            throw new IllegalArgumentException("You can only change the state of an execution to a terminal state.");
        }

        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);

        if (!execution.getState().canChangeStatus()) {
            throw new ConflictException("Cannot change execution state: execution must be terminated and not killed.");
        }

        return awaitBlockingAction(
            executionId, "Change status",
            operationId -> executionCommandQueue.emit(UpdateStatus.from(execution, status).withOperationId(operationId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/change-status/by-ids")
    @Operation(tags = { "Executions" }, summary = "Change executions state by id asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public HttpResponse<?> updateExecutionsStatusByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId,
        @Parameter(description = "The new state of the executions") @NotNull @QueryValue State.Type newStatus) throws QueueException {
        if (!newStatus.isTerminated()) {
            throw new IllegalArgumentException("You can only change the state of an execution to a terminal state.");
        }

        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && !execution.get().getState().canChangeStatus()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not in a terminated state or is killed",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk change executions state")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(
            executions,
            (execution, opId) -> executionCommandQueue.emit(UpdateStatus.from(execution, newStatus).withOperationId(opId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/change-status/by-query")
    @Operation(tags = { "Executions" }, summary = "Change executions state by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public HttpResponse<?> updateExecutionsStatusByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters,

        @Parameter(description = "The new state of the executions") @NotNull @QueryValue State.Type newStatus) throws QueueException {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

        return updateExecutionsStatusByIds(ids, newStatus);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/{executionId}/kill{?isOnKillCascade}", produces = MediaType.TEXT_JSON)
    @Operation(tags = { "Executions" }, summary = "Kill an execution")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the executions is already finished")
    @ApiResponse(responseCode = "404", description = "if the executions is not found")
    public Mono<HttpResponse<?>> killExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "Specifies whether killing the execution also kill all subflow executions.") @QueryValue(defaultValue = "true") Boolean isOnKillCascade)
        throws QueueException {

        Optional<Execution> maybeExecution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (maybeExecution.isEmpty()) {
            return Mono.just(HttpResponse.notFound());
        }

        var execution = maybeExecution.get();

        return killExecution(execution, isOnKillCascade);
    }

    protected Mono<HttpResponse<?>> killExecution(Execution execution, Boolean isOnKillCascade) {
        // Always emit an EXECUTION_KILLED event when isOnKillCascade=true.
        if (execution.getState().isTerminated() && !isOnKillCascade) {
            throw new ConflictException("Cannot kill execution: execution is already terminated.");
        }

        eventPublisher.publishEvent(CrudEvent.of(execution, execution.withState(State.Type.KILLING)));

        return awaitBlockingAction(
            execution.getId(), "Kill",
            operationId -> killQueue.emit(
                ExecutionKilledExecution
                    .builder()
                    .state(ExecutionKilled.State.REQUESTED)
                    .executionId(execution.getId())
                    .isOnKillCascade(isOnKillCascade)
                    .tenantId(tenantService.resolveTenant())
                    .operationId(operationId)
                    .build()
            )
        ).map(r -> (HttpResponse<?>) r);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/kill/by-ids")
    @Operation(tags = { "Executions" }, summary = "Kill a list of executions asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> killExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId) throws QueueException {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && execution.get().getState().isTerminated()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution already finished",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (!validateExecutionACL(execution.get())) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "user don't have the authorisation to kill this execution",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk kill")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(executions, (execution, opId) ->
        {
            eventPublisher.publishEvent(CrudEvent.of(execution, execution.withState(State.Type.KILLING)));
            killQueue.emit(
                ExecutionKilledExecution
                    .builder()
                    .state(ExecutionKilled.State.REQUESTED)
                    .executionId(execution.getId())
                    .isOnKillCascade(false) // Explicitly force cascade to false.
                    .tenantId(tenantService.resolveTenant())
                    .operationId(opId)
                    .build()
            );
        });
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/resume/validate", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(tags = { "Executions" }, summary = "Validate inputs to resume a paused execution.")
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "if the executions is not paused")
    @SingleResult
    public Publisher<ApiValidateExecutionInputsResponse> validateResumeExecutionInputs(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "The inputs") @Nullable @Body MultipartBody inputs) {
        Execution execution = executionService.getExecutionIfPause(tenantService.resolveTenant(), executionId, true);
        Flow flow = flowRepository.findByExecutionWithoutAcl(execution);

        return executionService.validateForResume(execution, flow, inputs)
            .map(values -> ApiValidateExecutionInputsResponse.of(execution.getFlowId(), execution.getNamespace(), List.of(), values))
            // need to consume the inputs in case of error
            .doOnError(t -> Flux.from(inputs).subscribeOn(Schedulers.boundedElastic()).blockLast());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/resume", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(
        tags = { "Executions" }, summary = "Resume a paused execution.",
        extensions = @Extension(
            name = "x-sdk-customization",
            properties = {
                @ExtensionProperty(name = "x-multipart", value = "true")
            }
        )
    )
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the executions is not paused")
    @SingleResult
    public Publisher<HttpResponse<?>> resumeExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "The inputs") @Nullable @Body MultipartBody inputs) throws InternalException {
        Execution execution = executionService.getExecutionIfPause(tenantService.resolveTenant(), executionId, true);
        Flow flow = flowRepository.findByExecutionWithoutAcl(execution);
        return resumeFoundExecution(inputs, execution, flow);
    }

    protected Mono<HttpResponse<?>> resumeFoundExecution(MultipartBody inputs, Execution execution, Flow flow) {
        io.kestra.plugin.core.flow.Pause.Resumed resumed = createResumed();

        return this.executionService.readInputs(execution, flow, inputs)
            .flatMap(
                resumeInputs -> awaitBlockingAction(
                    execution.getId(), "Resume",
                    operationId -> executionCommandQueue.emit(Resume.from(execution, resumed, resumeInputs).withOperationId(operationId))
                )
            )
            .map(r -> (HttpResponse<?>) r);
    }

    protected io.kestra.plugin.core.flow.Pause.Resumed createResumed() {
        return io.kestra.plugin.core.flow.Pause.Resumed.now();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/resume-from-breakpoint")
    @Operation(tags = { "Executions" }, summary = "Resume an execution from a breakpoint (in the 'BREAKPOINT' state).")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "If the executions is not in the 'BREAKPOINT' state or has no breakpoint")
    public Mono<HttpResponse<Execution>> resumeExecutionFromBreakpoint(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "\"Set a list of breakpoints at specific tasks 'id.value', separated by a coma.") @QueryValue Optional<String> breakpoints) throws Exception {
        Execution execution = executionService.getExecution(tenantService.resolveTenant(), executionId, true);
        if (!execution.getState().isBreakpoint()) {
            throw new ConflictException("Cannot resume execution: execution is not suspended.");
        }
        if (ListUtils.isEmpty(execution.getBreakpoints())) {
            throw new ConflictException("Cannot resume execution: no breakpoint defined.");
        }

        return awaitBlockingAction(
            executionId, "Resume from breakpoint",
            operationId -> executionCommandQueue.emit(ResumeFromBreakpoint.from(execution, breakpoints).withOperationId(operationId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/resume/by-ids")
    @Operation(tags = { "Executions" }, summary = "Resume a list of paused executions asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> resumeExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && !execution.get().getState().isPaused()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not in state PAUSED",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (!validateExecutionACL(execution.get())) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "user don't have the authorisation to resume this execution",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk resume")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(
            executions,
            (execution, opId) -> executionCommandQueue.emit(Resume.from(execution, createResumed()).withOperationId(opId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/resume/by-query")
    @Operation(tags = { "Executions" }, summary = "Resume executions filter by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public HttpResponse<?> resumeExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters) throws Exception {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

        return resumeExecutionsByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/pause")
    @Operation(tags = { "Executions" }, summary = "Pause a running execution.")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the executions is not running")
    public Mono<HttpResponse<Execution>> pauseExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId) throws Exception {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);
        if (!execution.getState().isRunning()) {
            throw new ConflictException("Cannot pause execution: execution is not running.");
        }

        return awaitBlockingAction(
            executionId, "Pause",
            operationId -> executionCommandQueue.emit(Pause.from(execution).withOperationId(operationId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/pause/by-ids")
    @Operation(tags = { "Executions" }, summary = "Pause a list of running executions asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> pauseExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && !execution.get().getState().isRunning()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not in state RUNNING",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk pause")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(
            executions,
            (execution, opId) -> executionCommandQueue.emit(Pause.from(execution).withOperationId(opId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/pause/by-query")
    @Operation(tags = { "Executions" }, summary = "Pause executions filter by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public HttpResponse<?> pauseExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters) throws Exception {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

        return pauseExecutionsByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/kill/by-query")
    @Operation(tags = { "Executions" }, summary = "Kill executions filter by query parameters")
    public HttpResponse<?> killExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters) throws QueueException {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

        return killExecutionsByIds(ids);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/replay/by-query")
    @Operation(tags = { "Executions" }, summary = "Create new executions from old ones filter by query parameters asynchronously. Keep the flow revision")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public HttpResponse<?> replayExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters,

        @Parameter(description = "If latest revision should be used") @Nullable @QueryValue(defaultValue = "false") Boolean latestRevision) throws Exception {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

        return replayExecutionsByIds(ids, latestRevision);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/replay/by-ids")
    @Operation(tags = { "Executions" }, summary = "Create new executions from old ones asynchronously. Keep the flow revision")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> replayExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId,
        @Parameter(description = "If latest revision should be used") @Nullable @QueryValue(defaultValue = "false") Boolean latestRevision) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk replay")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(executions, (execution, opId) ->
        {
            Flow flow = flowRepository.findById(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), Optional.empty()).orElseThrow();
            try {
                innerReplayBatch(execution, flow, null, latestRevision ? flow.getRevision() : null, Optional.empty(), opId);
            } catch (QueueException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(
        tags = { "Executions" },
        summary = "Follow an execution",
        extensions = @Extension(
            name = "x-sdk-customization",
            properties = {
                @ExtensionProperty(name = "x-replace-follow-execution", value = "true"),
                @ExtensionProperty(name = "x-skipped", value = "true")
            }
        )
    )
    public Flux<Event<Execution>> followExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId) {
        String subscriberId = UUID.randomUUID().toString();
        return Flux.<Event<Execution>> create(emitter ->
        {
            // Send initial event
            emitter.next(Event.of(Execution.builder().id(executionId).build()).id("start"));

            // Check if execution exists
            try {
                Execution execution = Await.await()
                    .atMost(Duration.ofSeconds(10))
                    .pollInterval(Duration.ofMillis(500))
                    .until(
                        () -> executionRepository.findById(tenantService.resolveTenant(), executionId).orElse(null),
                        Objects::nonNull
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

                // Fetch again the execution to avoid race when execution is ended before we are subscribed
                Execution finalExecution = execution;
                execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseGet(() ->
                {
                    log.error("Execution not found but we previously found it, this is a bug, executionId: '{}'", executionId);
                    // return the old execution fallback
                    return finalExecution;
                });
                if (streamingService.isStopFollow(flow, execution)) {
                    emitter.next(Event.of(execution).id("end"));
                    emitter.complete();
                }

                if (execution.getState().isBreakpoint()) {
                    emitter.next(Event.of(execution).id("progress"));
                }
            } catch (IllegalStateException e) {
                log.error(e.getMessage(), e);
                emitter.error(
                    new HttpStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unable to find flow for execution " + executionId
                    )
                );
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                emitter.error(
                    new HttpStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unable to find execution " + executionId
                    )
                );
            }
        }, FluxSink.OverflowStrategy.BUFFER)
            .timeout(Duration.ofHours(1)) // avoid idle SSE sockets by setting a between-item timeout
            .doFinally(ignored -> streamingService.unregisterSubscriber(executionId, subscriberId));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/file/preview")
    @Operation(tags = { "Executions" }, summary = "Get file preview for an execution")
    public HttpResponse<?> previewFileFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The internal storage uri") @QueryValue URI path,
        @Parameter(description = "The max row returns") @QueryValue @Nullable Integer maxRows,
        @Parameter(description = "The file encoding as Java charset name. Defaults to UTF-8", example = "ISO-8859-1") @QueryValue(defaultValue = "UTF-8") String encoding) throws IOException {
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

        InputStream fileStream = switch (path.getScheme()) {
            case StorageContext.KESTRA_SCHEME ->
                storageInterface.get(execution.get().getTenantId(), execution.get().getNamespace(), path);
            case LocalPath.FILE_SCHEME -> localPathFactory.createLocalPath().get(path);
            case Namespace.NAMESPACE_FILE_SCHEME -> {
                URI uri = nsFileToInternalStorageURI(path, execution.get());
                yield storageInterface.get(execution.get().getTenantId(), execution.get().getNamespace(), uri);
            }
            default -> throw new IllegalArgumentException("Scheme not supported: " + path.getScheme());
        };

        try (fileStream) {
            FileRender fileRender = FileRenderBuilder.of(
                extension,
                fileStream,
                charset,
                maxRows == null ? getPreviewInitialRows() : (maxRows > getPreviewMaxRows() ? getPreviewMaxRows() : maxRows)
            );

            return HttpResponse.ok(fileRender);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/labels")
    @Operation(tags = { "Executions" }, summary = "Add or update labels of a terminated execution")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "404", description = "If the execution cannot be found")
    @ApiResponse(responseCode = "400", description = "If the execution is not terminated")
    @ApiResponse(responseCode = "409", description = "If labels cannot be applied")
    public Mono<HttpResponse<?>> setLabelsOnTerminatedExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @RequestBody(description = "The labels to add to the execution") @Body @NotNull @Valid List<Label> labels) throws QueueException {
        Optional<Execution> maybeExecution = executionRepository.findById(tenantService.resolveTenant(), executionId);
        if (maybeExecution.isEmpty()) {
            return Mono.just(HttpResponse.notFound());
        }

        Execution execution = maybeExecution.get();
        if (!execution.getState().getCurrent().isTerminated()) {
            return Mono.just(HttpResponse.badRequest("The execution is not terminated"));
        }

        List<Label> mergedLabels = mergeSystemLabels(execution, labels);

        return awaitBlockingAction(
            executionId, "Set labels",
            operationId -> executionCommandQueue.emit(UpdateLabels.from(execution, mergedLabels).withOperationId(operationId))
        )
            .map(r -> (HttpResponse<?>) r);
    }

    private List<Label> mergeSystemLabels(Execution execution, List<Label> labels) {
        // check for system labels: none can be passed at runtime
        // as all existing labels will be passed here, we compare existing system label with the new one and fail if they are different

        List<Label> existingSystemLabels = ListUtils.emptyOnNull(execution.getLabels()).stream().filter(label -> label.key().startsWith(SYSTEM_PREFIX)).toList();
        Optional<Label> first = labels.stream().filter(label -> label.key().startsWith(SYSTEM_PREFIX)).filter(label -> !existingSystemLabels.contains(label)).findAny();
        if (first.isPresent()) {
            throw new IllegalArgumentException("System labels can only be set by Kestra itself, offending label: " + first.get().key() + "=" + first.get().value());
        }

        List<Label> newLabels = new ArrayList<>(labels);
        existingSystemLabels.forEach(
            label ->
            {
                // only add system labels
                if (!newLabels.contains(label)) {
                    newLabels.add(label);
                }
            }
        );
        return newLabels;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/labels/by-ids")
    @Operation(tags = { "Executions" }, summary = "Set labels on a list of executions asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> setLabelsOnTerminatedExecutionsByIds(
        @RequestBody(description = "The request containing a list of labels and a list of executions") @Body SetLabelsByIdsRequest setLabelsByIds) throws QueueException {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : setLabelsByIds.executionsId()) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);
            if (execution.isPresent() && !execution.get().getState().isTerminated()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution is not terminated",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }

        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk set labels")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(executions, (execution, opId) ->
        {
            List<Label> deduplicated = Label.deduplicate(ListUtils.concat(execution.getLabels(), setLabelsByIds.executionLabels()));
            List<Label> merged = mergeSystemLabels(execution, deduplicated);
            executionCommandQueue.emit(UpdateLabels.from(execution, merged).withOperationId(opId));
        });
    }

    public record SetLabelsByIdsRequest(@NotNull List<String> executionsId, @NotNull List<Label> executionLabels) {
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/labels/by-query")
    @Operation(tags = { "Executions" }, summary = "Set label on executions filter by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public HttpResponse<?> setLabelsOnTerminatedExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters,

        @RequestBody(description = "The labels to add to the execution") @Body @NotNull @Valid List<Label> setLabels) throws QueueException {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

        return setLabelsOnTerminatedExecutionsByIds(new SetLabelsByIdsRequest(ids, setLabels));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/unqueue")
    @Operation(tags = { "Executions" }, summary = "Unqueue an execution")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the execution cannot be unqueued")
    public Mono<HttpResponse<Execution>> unqueueExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The new state of the execution") @Nullable @QueryValue State.Type state) throws Exception {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);

        if (execution.getState().getCurrent() != State.Type.QUEUED) {
            throw new ConflictException("Cannot unqueue execution: only QUEUED executions can be unqueued.");
        }

        return awaitBlockingAction(
            executionId, "Unqueue",
            operationId -> executionCommandQueue.emit(Unqueue.from(execution, state).withOperationId(operationId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unqueue/by-ids")
    @Operation(tags = { "Executions" }, summary = "Unqueue a list of executions asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> unqueueExecutionsByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId,
        @Parameter(description = "The new state of the unqueued executions") @Nullable @QueryValue State.Type state) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);

            if (execution.isPresent() && execution.get().getState().getCurrent() != State.Type.QUEUED) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not in state QUEUED",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }
        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk unqueue")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(
            executions,
            (execution, opId) -> executionCommandQueue.emit(Unqueue.from(execution, state).withOperationId(opId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unqueue/by-query")
    @Operation(tags = { "Executions" }, summary = "Unqueue executions filter by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public HttpResponse<?> unqueueExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters,

        @Parameter(description = "The new state of the unqueued executions") @Nullable @QueryValue State.Type newState) throws Exception {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

        return unqueueExecutionsByIds(ids, newState);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{executionId}/force-run")
    @Operation(tags = { "Executions" }, summary = "Force run an execution")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = Execution.class)) })
    @ApiResponse(responseCode = "409", description = "if the execution cannot be force-run")
    public Mono<HttpResponse<Execution>> forceRunExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId) throws Exception {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);

        if (execution.getState().isTerminated()) {
            throw new ConflictException("Cannot force run execution: only non-terminated executions can be force run.");
        }

        return awaitBlockingAction(
            executionId, "Force run",
            operationId -> executionCommandQueue.emit(ForceRun.from(execution).withOperationId(operationId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/force-run/by-ids")
    @Operation(tags = { "Executions" }, summary = "Force run a list of executions asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    @ApiResponse(responseCode = "400", description = "Validation errors", content = { @Content(schema = @Schema(implementation = BulkErrorResponse.class)) })
    public MutableHttpResponse<?> forceRunByIds(
        @RequestBody(description = "The list of executions id") @Body List<String> executionsId) throws Exception {
        List<Execution> executions = new ArrayList<>();
        Set<ManualConstraintViolation<String>> invalids = new HashSet<>();

        for (String executionId : executionsId) {
            Optional<Execution> execution = executionRepository.findById(tenantService.resolveTenant(), executionId);

            if (execution.isPresent() && execution.get().getState().isTerminated()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution in a terminated state",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (execution.isEmpty()) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "execution not found",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else if (!validateExecutionACL(execution.get())) {
                invalids.add(
                    ManualConstraintViolation.of(
                        "user don't have the authorisation to force run this execution",
                        executionId,
                        String.class,
                        "execution",
                        executionId
                    )
                );
            } else {
                executions.add(execution.get());
            }
        }
        if (!invalids.isEmpty()) {
            return HttpResponse.badRequest(
                BulkErrorResponse
                    .builder()
                    .message("invalid bulk force run")
                    .invalids(invalids)
                    .build()
            );
        }

        return submitBatchAction(
            executions,
            (execution, opId) -> executionCommandQueue.emit(ForceRun.from(execution).withOperationId(opId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/force-run/by-query")
    @Operation(tags = { "Executions" }, summary = "Force run executions filter by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public HttpResponse<?> forceRunExecutionsByQuery(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters) throws Exception {
        var ids = getExecutionIds(QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters));

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
    @Operation(tags = { "Executions" }, summary = "Get flow information's for an execution")
    public FlowForExecution getFlowFromExecutionById(
        @Parameter(description = "The execution that you want flow information") String executionId) {
        Execution execution = executionRepository.findById(tenantService.resolveTenant(), executionId)
            .orElseThrow(() -> new io.kestra.core.exceptions.NotFoundException("Execution %s not found when fetching flow".formatted(executionId)));

        return FlowForExecution.of(flowRepository.findByExecutionWithoutAcl(execution));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/flows/{namespace}/{flowId}")
    @Operation(tags = { "Executions" }, summary = "Get flow information's for an execution")
    public FlowForExecution getFlowFromExecution(
        @Parameter(description = "The namespace of the flow") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The flow revision") @Nullable Integer revision) {

        return FlowForExecution.of(flowRepository.findByIdWithoutAcl(tenantService.resolveTenant(), namespace, flowId, Optional.ofNullable(revision)).orElseThrow());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/namespaces")
    @Operation(tags = { "Executions" }, summary = "Get all namespaces that have executable flows")
    public List<String> listExecutableDistinctNamespaces() {
        return flowRepository.findDistinctNamespaceExecutable(tenantService.resolveTenant());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/namespaces/{namespace}/flows")
    @Operation(
        tags = { "Executions" },
        summary = "Get all flow ids for a namespace. Data returned are FlowForExecution containing minimal information about a Flow for when you are allowed to executing but not reading."
    )
    public List<FlowForExecution> listFlowExecutionsByNamespace(
        @Parameter(description = "The namespace") @PathVariable String namespace) {
        return flowRepository.findByNamespaceExecutable(tenantService.resolveTenant(), namespace);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/follow-dependencies", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(
        tags = { "Executions" },
        summary = "Follow all execution dependencies executions",
        extensions = @Extension(
            name = "x-sdk-customization",
            properties = {
                @ExtensionProperty(name = "x-replace-follow-dependencies-execution", value = "true"),
                @ExtensionProperty(name = "x-skipped", value = "true")
            }
        )
    )
    public Flux<Event<ExecutionStatusEvent>> followDependenciesExecutions(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "If true, list only destination dependencies, otherwise list also source dependencies") @QueryValue(defaultValue = "false") boolean destinationOnly,
        @Parameter(description = "If true, expand all dependencies recursively") @QueryValue(defaultValue = "false") boolean expandAll) {
        String subscriberId = UUID.randomUUID().toString();

        // NOTE: ideally, we should load the execution inside the Flux.
        //  But as we need the correlationId to unsubscribe, we have no choice but to do it eagerly.
        //  This should not be an issue as long as it executes on an IO thread.

        // Check if execution exists
        Execution current = Await.await()
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(500))
            .until(
                () -> executionRepository.findById(tenantService.resolveTenant(), executionId).orElse(null),
                Objects::nonNull
            );

        String correlationId = current.getLabels().stream().filter(label -> label.key().equals(CORRELATION_ID)).findAny().map(label -> label.value()).orElseThrow();

        return Flux.<Event<ExecutionStatusEvent>> create(emitter ->
        {
            // Send initial event
            emitter.next(Event.of(ExecutionStatusEvent.of(Execution.builder().id(executionId).build())).id("start"));

            try {
                Stream<FlowTopology> flowTopologyStream = flowService.findDependencies(current.getTenantId(), current.getNamespace(), current.getFlowId(), destinationOnly, expandAll);
                FlowTopologyGraph graph = flowTopologyService.graph(
                    flowTopologyStream,
                    (flowNode -> flowNode)
                );
                List<FlowNode> dependencies = new ArrayList<>(graph.getNodes()); // we need a modifiable collection

                // precompute flows for all nodes
                Map<String, Flow> flows = new HashMap<>();
                dependencies.forEach(
                    node -> flows.put(
                        FlowId.uidWithoutRevision(node.getTenantId(), node.getNamespace(), node.getId()),
                        flowRepository.findByIdWithoutAcl(node.getTenantId(), node.getNamespace(), node.getId(), Optional.empty()).orElseThrow()
                    )
                );

                // check if there are already terminated executions so we could end them immediately
                List<Execution> terminatedExecutions = executionRepository
                    .find(null, current.getTenantId(), null, null, null, null, null, null, Map.of(CORRELATION_ID, correlationId), null, null)
                    .mapNotNull(exec ->
                    {
                        if (
                            dependencies.stream()
                                .anyMatch(node -> node.getTenantId().equals(exec.getTenantId()) && node.getNamespace().equals(exec.getNamespace()) && node.getId().equals(exec.getFlowId()))
                        ) {
                            if (streamingService.isStopFollow(flows.get(FlowId.uidWithoutRevision(current)), current)) {
                                emitter.next(Event.of(ExecutionStatusEvent.of(exec)).id("end"));
                                return exec;
                            } else {
                                emitter.next(Event.of(ExecutionStatusEvent.of(exec)).id("progress"));
                            }
                        }
                        return null;
                    })
                    .collectList()
                    .blockOptional()
                    .orElse(Collections.emptyList());
                terminatedExecutions.forEach(
                    exec -> dependencies
                        .removeIf(node -> node.getTenantId().equals(exec.getTenantId()) && node.getNamespace().equals(exec.getNamespace()) && node.getId().equals(exec.getFlowId()))
                );

                // end the flux is all nodes are already terminated
                if (dependencies.isEmpty()) {
                    emitter.next(Event.of(ExecutionStatusEvent.of(Execution.builder().id(executionId).build())).id("end-all"));
                    emitter.complete();
                    return;
                }

                // subscribe to all executions with the same correlationId to track dependencies
                // NOTE: there is a small risk that between the time we check for already terminated executions and the time we start listening,
                //  some exec would be terminated, and we miss there update which would retain the SSE connection forever.
                //  We set a timeout for that.
                executionDependenciesStreamingService
                    .registerSubscriber(correlationId, subscriberId, new ExecutionDependenciesStreamingService.Subscriber(correlationId, dependencies, flows, emitter));
            } catch (IllegalStateException e) {
                emitter.error(
                    new HttpStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unable to find flow for execution " + executionId
                    )
                );
            }
        }, FluxSink.OverflowStrategy.BUFFER)
            .timeout(Duration.ofHours(1)) // avoid idle SSE sockets by setting a between-item timeout
            .doFinally(ignored -> executionDependenciesStreamingService.unregisterSubscriber(correlationId, subscriberId));
    }

    public String getTenant() {
        return tenantService.resolveTenant() != null ? tenantService.resolveTenant() + "/" : "";
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/latest")
    @Operation(tags = { "Executions" }, summary = "Get the latest execution for given flows")
    public List<LastExecutionResponse> getLatestExecutions(
        @Parameter(description = "The flow filters") @Body List<ExecutionRepositoryInterface.FlowFilter> flowFilters) {
        return executionRepository.lastExecutions(
            tenantService.resolveTenant(),
            flowFilters
        ).stream().map(LastExecutionResponse::ofExecution).toList();
    }

    @Get(uri = "/export/by-query/csv", produces = MediaType.TEXT_CSV)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Executions" }, summary = "Export all executions as a streamed CSV file")
    @SuppressWarnings("unchecked")
    public MutableHttpResponse<Flux<String>> exportExecutions(
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[timeRange][EQUALS]=PT168H`, `filters[scope][EQUALS]=USER`, `filters[state][IN]=FAILED,CANCELLED`, `filters[labels][NOT_EQUALS][foo]=bar`, `filters[namespace][CONTAINS]=test`",
            in = ParameterIn.QUERY
        ) @QueryFilterFormat List<QueryFilter> filters) {

        return HttpResponse.ok(
            CSVUtils.toCSVFlux(
                executionRepository.findAsync(this.tenantService.resolveTenant(), QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters))
                    .map(log -> objectMapper.convertValue(log, JacksonMapper.MAP_TYPE_REFERENCE))
            )
        )
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=executions.csv");
    }

    public record LastExecutionResponse(
        @NotNull @Parameter(description = "The execution's ID") String id,
        @NotNull @Parameter(description = "The flow's ID") String flowId,
        @NotNull @Parameter(description = "The namespace") String namespace,
        @NotNull @Parameter(description = "The start date") Instant startDate,
        @NotNull @Parameter(description = "The status") State.Type status) {

        public static LastExecutionResponse ofExecution(Execution execution) {
            return new LastExecutionResponse(
                execution.getId(),
                execution.getFlowId(),
                execution.getNamespace(),
                execution.getState().getStartDate(),
                execution.getState().getCurrent()
            );
        }
    }

    public record ApiValidateExecutionInputsResponse(
        @NotNull @Parameter(description = "The flow's ID") String id,
        @NotNull @Parameter(description = "The namespace") String namespace,
        @NotNull @Parameter(description = "The flow's inputs") List<ApiInputAndValue> inputs,
        @NotNull List<ApiCheckFailure> checks) {

        public record ApiInputAndValue(
            @NotNull @Parameter(description = "The input") Input<?> input,
            @Parameter(description = "The value") Object value,
            @Parameter(description = "Specifies whether the input is enabled") boolean enabled,
            @Parameter(description = "Specifies whether the input value is the default") boolean isDefault,
            @NotNull @Parameter(description = "The validation errors") List<ApiInputError> errors) {
        }

        public record ApiInputError(
            @NotNull @Parameter(description = "The error message") String message) {
        }

        public record ApiCheckFailure(
            @NotNull @Parameter(description = "The message") String message,
            @NotNull @Parameter(description = "The message style") Check.Style style,
            @NotNull @Parameter(description = "The behavior") Check.Behavior behavior) {
        }

        public static ApiValidateExecutionInputsResponse of(
            String id,
            String namespace,
            List<Check> checks,
            List<InputAndValue> inputs) {
            return new ApiValidateExecutionInputsResponse(
                id,
                namespace,
                inputs.stream().map(
                    it -> new ApiInputAndValue(
                        it.input(),
                        it.value(),
                        it.enabled(),
                        it.isDefault(),
                        // Map the Set<InputOutputValidationException> to ApiInputError
                        Optional.ofNullable(it.exceptions())
                            .map(
                                exSet -> exSet.stream()
                                    .map(e -> new ApiInputError(e.getMessage()))
                                    .toList()
                            )
                            .orElse(List.of())
                    )
                ).toList(),
                checks.stream()
                    .map(
                        check -> new ApiCheckFailure(
                            check.getMessage(),
                            check.getStyle(),
                            check.getBehavior()
                        )
                    )
                    .toList()
            );
        }
    }

    /**
     * For override purpose.
     *
     * @param execution
     * @return true if the user has the authorization, false else.
     */
    protected boolean validateExecutionACL(Execution execution) {
        return true;
    }

    /**
     * Executes a single async operation on an execution.
     */
    private Mono<HttpResponse<Execution>> awaitBlockingAction(
        String executionId,
        String actionName,
        ThrowingConsumer<String> emit) {
        String tenantId = tenantService.resolveTenant();
        return asyncOperationWaiter.submit(
            executionId,
            operationId ->
            {
                try {
                    emit.accept(operationId);
                } catch (QueueException e) {
                    throw new RuntimeException(e);
                }
            },
            asyncOperationsConfiguration.waitTimeout()
        )
            .onErrorMap(
                TimeoutException.class, e -> new HttpStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Operation timed out waiting for state transition"
                )
            )
            .map(processed ->
            {
                if (processed.outcome() == AsyncOperationProcessedEvent.Outcome.FAILED) {
                    throw new HttpStatusException(
                        HttpStatus.CONFLICT,
                        "Failed to execute action '%s' on execution %s (operation_id=%s). Cause: %s".formatted(actionName, executionId, processed.operationId(), processed.error())
                    );
                }
                return executionRepository.findById(tenantId, executionId)
                    .orElseThrow(
                        () -> new NoSuchElementException(
                            "Execution disappeared after " + actionName.toLowerCase() + ": " + executionId
                        )
                    );
            })
            .map(HttpResponse::ok);
    }

    /**
     * Submits a batch of async operations sharing a single operationId and returns
     * an accepted response with the operationId and the total number of items.
     */
    private MutableHttpResponse<ApiAsyncOperationResponse> submitBatchAction(
        List<Execution> executions,
        ThrowingBiConsumer<Execution, String> emit) throws QueueException {
        String operationId = IdUtils.create();
        for (Execution execution : executions) {
            emit.accept(execution, operationId);
        }
        return HttpResponse.accepted()
            .body(new ApiAsyncOperationResponse(operationId, executions.size()));
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws QueueException;
    }

    @FunctionalInterface
    private interface ThrowingBiConsumer<T, U> {
        void accept(T first, U second) throws QueueException;
    }

    private int getPreviewInitialRows() {
        return Optional.ofNullable(serverConfig.preview())
            .map(ServerConfig.Preview::initialRows)
            .orElse(100);
    }

    private int getPreviewMaxRows() {
        return Optional.ofNullable(serverConfig.preview())
            .map(ServerConfig.Preview::maxRows)
            .orElse(5000);
    }
}
