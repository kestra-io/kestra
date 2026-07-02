package io.kestra.mcp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.Label;
import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.services.ExecutionStreamingService;
import io.kestra.plugin.core.trigger.McpToolTrigger;
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

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    private final Cache<ToolHandlerCacheKey, McpServerFeatures.AsyncToolSpecification> asyncToolSpecificationCache;

    private static final McpSchema.CallToolResult FLOW_ERROR_CALL_TOOL_RESULT = McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("Failed to execute flow")
        .build();

    public McpToolService(
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        FlowRepositoryInterface flowRepositoryInterface,
        FlowToolSchemaMapper flowToolSchemaMapper,
        ExecutionStreamingService streamingService, ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher,
        McpConfig mcpConfig,
        FlowInputOutput flowInputOutput
        ) {
        this.executionCommandQueue = executionCommandQueue;
        this.flowRepositoryInterface = flowRepositoryInterface;
        this.flowToolSchemaMapper = flowToolSchemaMapper;
        this.streamingService = streamingService;
        this.eventPublisher = eventPublisher;
        this.mcpConfig = mcpConfig;
        this.flowInputOutput = flowInputOutput;
        asyncToolSpecificationCache = Caffeine.newBuilder()
            .maximumSize(mcpConfig.toolCacheConfig().maximumSize())
            .expireAfterAccess(mcpConfig.toolCacheConfig().expireAfterAccess())
            .build();
    }


    public List<McpServerFeatures.AsyncToolSpecification> listToolSpecsForServer(String tenantId, String serverId, McpServer.ServerType serverType) {
        return fetchFlowWithMcpToolTrigger(tenantId, serverId, serverType).stream().flatMap(flow -> flow.getTriggers().stream()
                .filter(isMcpTriggerTypeAndEnabledPredicate())
                .filter(trigger -> serverId.equals(
                    Objects.requireNonNullElse(((McpToolTrigger) trigger).getMcpServer(), McpToolTrigger.DEFAULT_SERVER_ID)
                ))
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
        McpToolTrigger toolTrigger
    ) {
        final List<String> defaultsInputs = flow.resolvableInputs()
            .stream().map(Input::getId).toList();

        return (exchange, request) -> {
            Map<String, Object> input = request.arguments().entrySet().stream()
                .filter(entry -> defaultsInputs.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Map<String, Object> additionalInputs = request.arguments().entrySet().stream()
                .filter(entry -> !defaultsInputs.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            KestraMcpTransportContext context = (KestraMcpTransportContext) exchange.transportContext();

            Execution execution = toolTrigger.evaluate(flow, input, additionalInputs, Label.from(Map.of(
                Label.FROM, "mcp",
                Label.MCP_SERVER_ID, context.getServerId(),
                Label.MCP_SESSION_ID, context.getSessionId()
            )));

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
                .map(executionResult -> McpSchema.CallToolResult.builder()
                    .structuredContent(executionResult.getOutputs() != null && executionResult.getState().isSuccess() ? executionResult.getOutputs() : Map.of())
                    .isError(!executionResult.getState().isSuccess())
                    .build())
                .onErrorReturn(Exception.class, FLOW_ERROR_CALL_TOOL_RESULT);
        };
    }

    List<String> collectInputValidationErrors(Flow flow, Execution execution, Map<String, Object> input) {
        return flowInputOutput.resolveInputs(flow.getInputs(), flow, execution, input).stream()
            .filter(resolved -> resolved.exceptions() != null && !resolved.exceptions().isEmpty())
            .flatMap(resolved -> resolved.exceptions().stream())
            .map(Throwable::getMessage)
            .toList();
    }

    private static McpSchema.CallToolResult invalidInputResult(List<String> validationErrors) {
        return McpSchema.CallToolResult.builder()
            .isError(true)
            .addTextContent("Invalid input provided to the tool:" + System.lineSeparator()
                + String.join(System.lineSeparator(), validationErrors))
            .build();
    }

    private Mono<Execution> runFlowForMcpTask(
        Flow flow,
        Execution execution
    ) {
        try {
            executionCommandQueue.emit(Create.of(new ExecutionId(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId(), execution.getFlowRevision()))
                .withKind(execution.getKind())
                .withTrigger(execution.getTrigger())
                .withLabels(execution.getLabels())
                .withInputs(execution.getInputs()));
            eventPublisher.publishEvent(new CrudEvent<>(execution, null, CrudEventType.CREATE));


            String subscriberId = UUID.randomUUID().toString();
            return Flux.<Event<Execution>>create(emitter -> streamingService.registerSubscriber(
                    execution.getId(),
                    subscriberId,
                    emitter,
                    flow
                ))
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
            .filter(flow -> !flow.isDisabled() &&
                flow.getTriggers().stream().anyMatch(trigger ->
                    trigger.getClass().equals(McpToolTrigger.class) && serverId.equals(
                        Objects.requireNonNullElse(((McpToolTrigger) trigger).getMcpServer(), McpToolTrigger.DEFAULT_SERVER_ID)
                    )
                )
            ).toList();
    }

    private static Predicate<AbstractTrigger> isMcpTriggerTypeAndEnabledPredicate() {
        return trigger -> trigger.getClass().equals(McpToolTrigger.class) && !trigger.isDisabled();
    }

    private record ToolHandlerCacheKey(
        FlowId flowid,
        String toolTriggerId
    ) {
        private ToolHandlerCacheKey(Flow flow, McpToolTrigger toolTrigger) {
            this(FlowId.of(flow.getTenantId(), flow.getNamespace(), flow.getId(), flow.getRevision()), toolTrigger.getId());
        }
    }
}
