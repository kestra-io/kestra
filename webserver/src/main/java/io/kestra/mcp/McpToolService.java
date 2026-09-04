package io.kestra.mcp;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.FlowMetaStores;
import io.kestra.core.runners.ProcessedFlow;
import io.kestra.core.scheduler.events.UnscheduledTriggerFired;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.services.ExecutionOutputService;
import io.kestra.core.services.ExecutionStreamingService;
import io.kestra.core.services.LabelService;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.kestra.webserver.services.TriggerStateService;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.sse.Event;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class McpToolService {
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final FlowRepositoryInterface flowRepositoryInterface;
    private final FlowToolSchemaMapper flowToolSchemaMapper;
    private final ExecutionStreamingService streamingService;
    private final ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;
    private final McpConfig mcpConfig;
    private final FlowInputOutput flowInputOutput;
    private final FlowMetaStoreInterface flowMetaStore;
    private final ExecutionOutputService executionOutputService;
    private final TriggerStateService triggerStateService;
    private final TriggerEventQueue triggerEventQueue;
    private final Cache<ToolHandlerCacheKey, McpServerFeatures.AsyncToolSpecification> asyncToolSpecificationCache;

    private static final McpSchema.CallToolResult FLOW_ERROR_CALL_TOOL_RESULT = McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("Failed to execute flow")
        .build();

    private static final McpSchema.CallToolResult DISABLED_TRIGGER_CALL_TOOL_RESULT = McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("The trigger exposing this tool is disabled")
        .build();

    public McpToolService(
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        FlowRepositoryInterface flowRepositoryInterface,
        FlowToolSchemaMapper flowToolSchemaMapper,
        ExecutionStreamingService streamingService, ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher,
        McpConfig mcpConfig,
        FlowInputOutput flowInputOutput,
        FlowMetaStoreInterface flowMetaStore,
        ExecutionOutputService executionOutputService,
        TriggerStateService triggerStateService,
        TriggerEventQueue triggerEventQueue) {
        this.executionCommandQueue = executionCommandQueue;
        this.flowRepositoryInterface = flowRepositoryInterface;
        this.flowToolSchemaMapper = flowToolSchemaMapper;
        this.streamingService = streamingService;
        this.eventPublisher = eventPublisher;
        this.mcpConfig = mcpConfig;
        this.flowInputOutput = flowInputOutput;
        this.flowMetaStore = flowMetaStore;
        this.executionOutputService = executionOutputService;
        this.triggerStateService = triggerStateService;
        this.triggerEventQueue = triggerEventQueue;
        asyncToolSpecificationCache = Caffeine.newBuilder()
            .maximumSize(mcpConfig.toolCacheConfig().maximumSize())
            .expireAfterAccess(mcpConfig.toolCacheConfig().expireAfterAccess())
            .build();
    }

    public List<McpServerFeatures.AsyncToolSpecification> listToolSpecsForServer(String tenantId, String serverId, McpServer.ServerType serverType) {
        return fetchFlowWithMcpToolTrigger(tenantId, serverId, serverType).stream().flatMap(
            flow -> flow.getTriggers().stream()
                .filter(isMcpTriggerTypeAndEnabledPredicate())
                .filter(
                    trigger -> serverId.equals(
                        Objects.requireNonNullElse(((McpToolTrigger) trigger).getMcpServer(), McpToolTrigger.DEFAULT_SERVER_ID)
                    )
                )
                .filter(trigger -> !isDisabledInTriggerState(flow, trigger))
                .map(trigger -> getAsyncToolSpecification(flow, (McpToolTrigger) trigger))
        ).toList();
    }

    private McpServerFeatures.AsyncToolSpecification getAsyncToolSpecification(Flow flow, McpToolTrigger toolTrigger) {
        ToolHandlerCacheKey toolHandlerCacheKey = new ToolHandlerCacheKey(flow, toolTrigger);
        return asyncToolSpecificationCache.get(
            toolHandlerCacheKey,
            (_) -> buildAsyncToolSpecification(flow, toolTrigger)
        );
    }

    private McpServerFeatures.AsyncToolSpecification buildAsyncToolSpecification(Flow flow, McpToolTrigger toolTrigger) {
        log.debug("Building AsyncToolSpecification for flowid: {}/{}/{}, trigger: {}", flow.getTenantId(), flow.getNamespace(), flow.getId(), toolTrigger.getId());
        return new McpServerFeatures.AsyncToolSpecification(
            flowToolSchemaMapper.buildTool(flow, toolTrigger),
            buildCallHandler(flow, toolTrigger)
        );
    }

    private BiFunction<McpAsyncServerExchange, McpSchema.CallToolRequest, Mono<McpSchema.CallToolResult>> buildCallHandler(
        Flow flow,
        McpToolTrigger toolTrigger) {
        final List<String> defaultsInputs = flow.resolvableInputs()
            .stream().map(Input::getId).toList();

        return (exchange, request) ->
        {
            // Re-checked on every call: the tool specification is cached and its list is only refreshed on a
            // flow change, so a trigger state disabled in between would otherwise leave the tool callable.
            if (isDisabledInTriggerState(flow, toolTrigger)) {
                return Mono.just(DISABLED_TRIGGER_CALL_TOOL_RESULT);
            }

            Map<String, Object> input = request.arguments().entrySet().stream()
                .filter(entry -> defaultsInputs.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Map<String, Object> additionalInputs = request.arguments().entrySet().stream()
                .filter(entry -> !defaultsInputs.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            KestraMcpTransportContext context = (KestraMcpTransportContext) exchange.transportContext();

            Execution execution = toolTrigger.evaluate(
                flow, input, additionalInputs, Label.from(
                    Map.of(
                        Label.FROM, Label.FromLabel.MCP.value,
                        Label.MCP_SERVER_ID, context.getServerId(),
                        Label.MCP_SESSION_ID, context.getSessionId()
                    )
                )
            );

            List<String> validationErrors = collectInputValidationErrors(flow, execution, input);
            if (!validationErrors.isEmpty()) {
                log.debug(
                    "Rejecting MCP tool '{}' call for flow {}/{}/{} (execution {}): {} invalid input(s): {}",
                    toolTrigger.getToolName(), flow.getTenantId(), flow.getNamespace(), flow.getId(),
                    execution.getId(), validationErrors.size(), validationErrors
                );
                return Mono.just(invalidInputResult(validationErrors));
            }

            return runFlowForMcpTask(flow, execution)
                .flatMap(executionResult ->
                {
                    try {
                        Map<String, Object> outputs = executionResult.getState().isSuccess() ? executionOutputService.getOutputs(executionResult) : null;
                        return Mono.just(
                            McpSchema.CallToolResult.builder()
                                .structuredContent(outputs != null ? outputs : Map.of())
                                .isError(!executionResult.getState().isSuccess())
                                .build()
                        );
                    } catch (InternalException e) {
                        return Mono.error(e);
                    }
                })
                .onErrorReturn(Exception.class, FLOW_ERROR_CALL_TOOL_RESULT);
        };
    }

    List<String> collectInputValidationErrors(Flow flow, Execution execution, Map<String, Object> input) {
        // An input renders against the execution's labels, which the trigger deliberately limits to what it
        // contributes, so validate against the merge the executor will build, on the flow it will build it
        // from. Otherwise an input defaulting to {{ labels.something }} is rejected here and accepted there.
        // a flow governance blocks resolves to a FlowWithException, which carries no input: the authored ones
        // are validated instead, and the block itself fails the execution once it is created
        ProcessedFlow processedFlow = FlowMetaStores.findForRuntimeOrRaw(flowMetaStore, flow);
        Flow resolvedFlow = processedFlow.flow() instanceof FlowWithException ? flow : processedFlow.flow();
        Execution forValidation = execution.withLabels(
            LabelService.forExecution(
                resolvedFlow,
                LabelService.withoutPinned(execution.getLabels(), processedFlow.pinnedLabelKeys()),
                execution.getId()
            )
        );

        return flowInputOutput.resolveInputs(resolvedFlow.getInputs(), resolvedFlow, forValidation, input).stream()
            .filter(resolved -> resolved.exceptions() != null && !resolved.exceptions().isEmpty())
            .flatMap(resolved -> resolved.exceptions().stream())
            .map(Throwable::getMessage)
            .toList();
    }

    private static McpSchema.CallToolResult invalidInputResult(List<String> validationErrors) {
        return McpSchema.CallToolResult.builder()
            .isError(true)
            .addTextContent(
                "Invalid input provided to the tool:" + System.lineSeparator()
                    + String.join(System.lineSeparator(), validationErrors)
            )
            .build();
    }

    private Mono<Execution> runFlowForMcpTask(
        Flow flow,
        Execution execution) {
        try {
            executionCommandQueue.emit(
                Create.of(new ExecutionId(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId(), execution.getFlowRevision()))
                    .withKind(execution.getKind())
                    .withTrigger(execution.getTrigger())
                    .withLabels(execution.getLabels())
                    .withInputs(execution.getInputs())
            );
            triggerEventQueue.send(UnscheduledTriggerFired.of(execution));
            eventPublisher.publishEvent(CrudEvent.create(execution));

            String subscriberId = UUID.randomUUID().toString();
            return Flux.<Event<Execution>> create(
                emitter -> streamingService.registerSubscriber(
                    execution.getId(),
                    subscriberId,
                    emitter,
                    flow
                )
            )
                .timeout(mcpConfig.toolExecutionTimeout())
                .last()
                .map(Event::getData)
                .doFinally(signalType -> streamingService.unregisterSubscriber(execution.getId(), subscriberId));
        } catch (QueueException e) {
            return Mono.error(e);
        }
    }

    private List<Flow> fetchFlowWithMcpToolTrigger(String tenantId, String serverId, McpServer.ServerType serverType) {
        var flows = McpServer.ServerType.PUBLIC.equals(serverType)
            ? flowRepositoryInterface.findWithNoAcl(Pageable.unpaged(), tenantId, McpToolTrigger.class)
            : flowRepositoryInterface.find(Pageable.unpaged(), tenantId, McpToolTrigger.class);

        return flows.stream()
            .filter(
                flow -> !flow.isDisabled() &&
                    flow.getTriggers().stream().anyMatch(
                        trigger -> trigger.getClass().equals(McpToolTrigger.class) && serverId.equals(
                            Objects.requireNonNullElse(((McpToolTrigger) trigger).getMcpServer(), McpToolTrigger.DEFAULT_SERVER_ID)
                        )
                    )
            ).toList();
    }

    private static Predicate<AbstractTrigger> isMcpTriggerTypeAndEnabledPredicate() {
        return trigger -> trigger.getClass().equals(McpToolTrigger.class) && !trigger.isDisabled();
    }

    private boolean isDisabledInTriggerState(Flow flow, AbstractTrigger trigger) {
        return triggerStateService.isDisabledByState(TriggerId.of(flow, trigger));
    }

    private record ToolHandlerCacheKey(
        FlowId flowid,
        String toolTriggerId) {
        private ToolHandlerCacheKey(Flow flow, McpToolTrigger toolTrigger) {
            this(FlowId.of(flow.getTenantId(), flow.getNamespace(), flow.getId(), flow.getRevision()), toolTrigger.getId());
        }
    }
}
