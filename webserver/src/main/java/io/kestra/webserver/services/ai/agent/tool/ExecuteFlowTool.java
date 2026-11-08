package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.services.ExecutionStreamingService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.webserver.services.ExecutionCreationService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.AgentConfiguration;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.sse.Event;
import io.opentelemetry.context.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class ExecuteFlowTool implements McpPlatformTool {
    private final ExecutionCreationService executionCreationService;
    private final FlowInputOutput flowInputOutput;
    private final ExecutionActionSubmitter submitter;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final ExecutionRepositoryInterface executionRepository;
    private final ExecutionStreamingService streamingService;
    private final ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;
    private final AgentConfiguration agentConfiguration;

    @Inject
    public ExecuteFlowTool(
        final ExecutionCreationService executionCreationService,
        final FlowInputOutput flowInputOutput,
        final ExecutionActionSubmitter submitter,
        final DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        final ExecutionRepositoryInterface executionRepository,
        final ExecutionStreamingService streamingService,
        final ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher,
        final AgentConfiguration agentConfiguration) {
        this.executionCreationService = executionCreationService;
        this.flowInputOutput = flowInputOutput;
        this.submitter = submitter;
        this.executionCommandQueue = executionCommandQueue;
        this.executionRepository = executionRepository;
        this.streamingService = streamingService;
        this.eventPublisher = eventPublisher;
        this.agentConfiguration = agentConfiguration;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.ACT;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.CONFIRM;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.DESTRUCTIVE;
    }

    @Tool(
        name = "execute_flow",
        value = "Start a new execution of a Kestra flow, which runs its tasks and any side effects they have. Uses the latest published revision unless `revision` is given. "
            + "Inputs are validated against the flow's declared inputs; FILE inputs are not supported. "
            + "Returns an object { executionId, namespace, flowId, state, terminated, url }. Without `wait` it returns as soon as the execution is created. "
            + "With `wait` it returns once the execution ends, or after a bounded wait with `terminated` false while the execution keeps running; never start the flow again to retry, follow the returned execution with read_execution."
    )
    public Result executeFlow(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        @P(name = "inputs", value = "The flow inputs, as a map of input id to value", required = false) Map<String, Object> inputs,
        @P(name = "labels", value = "Labels to set on the execution, as a map of key to value", required = false) Map<String, String> labels,
        @P(name = "revision", value = "The flow revision to execute; defaults to the latest published revision", required = false) Integer revision,
        @P(name = "scheduleDate", value = "Start the execution at this ISO-8601 date-time instead of now, e.g. 2026-07-05T14:30:00Z", required = false) String scheduleDate,
        @P(name = "wait", value = "Whether to wait for the execution to end before returning; defaults to false", required = false) Boolean wait,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        Instant scheduledAt = parseScheduleDate(scheduleDate);
        ExecutionCreationService.ExecutableFlow executable = executionCreationService.findExecutableFlow(tenant, namespace, flowId, Optional.ofNullable(revision), null);

        List<Label> requestedLabels = new ArrayList<>(executionCreationService.requestLabels(
            MapUtils.emptyOnNull(labels).entrySet().stream().map(entry -> new Label(entry.getKey(), entry.getValue())).toList()
        ));
        requestedLabels.addAll(callerLabels(context));

        String executionId = IdUtils.create();
        Map<String, Object> executionInputs = flowInputOutput.readExecutionInputs(
            executable.runtimeFlow(), executionId, executable.contributedLabels(requestedLabels), MapUtils.emptyOnNull(inputs)
        );
        Create command = executionCreationService.createCommand(executable, executionId, requestedLabels, executionInputs, scheduledAt, null, null, Context.current());

        submitter.submitAndWait(executionId, "start", (operationId, _) -> executionCommandQueue.emit(command.withOperationId(operationId)));
        Execution created = executionRepository.findByIdWithoutAcl(tenant, executionId)
            .orElseThrow(() -> new IllegalStateException("Execution '%s' was accepted but could not be found.".formatted(executionId)));
        eventPublisher.publishEvent(CrudEvent.create(created));

        boolean failedOnCreate = command.stateType() != null && command.stateType().isFailed();
        Execution execution = Boolean.TRUE.equals(wait) && !failedOnCreate ? awaitEnd(executable.flow(), created) : created;

        return new Result(
            execution.getId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getState().getCurrent().name(),
            execution.getState().isTerminated(),
            executionCreationService.executionUrl(command.executionFullId()).toString()
        );
    }

    /**
     * Labels the caller adds to every execution it starts, on top of the ones it asks for.
     */
    protected List<Label> callerLabels(final AgentCallContext.Context context) {
        return List.of();
    }

    private Execution awaitEnd(final Flow flow, final Execution created) {
        String executionId = created.getId();
        String subscriberId = UUID.randomUUID().toString();
        return Flux.<Event<Execution>> create(emitter -> streamingService.registerSubscriber(executionId, subscriberId, emitter, flow))
            .last()
            .map(Event::getData)
            .timeout(agentConfiguration.executeFlowMaxWait())
            .onErrorResume(TimeoutException.class, e -> Mono.empty())
            .doFinally(_ -> streamingService.unregisterSubscriber(executionId, subscriberId))
            .blockOptional()
            .or(() -> executionRepository.findByIdWithoutAcl(created.getTenantId(), executionId))
            .orElse(created);
    }

    private static Instant parseScheduleDate(final String scheduleDate) {
        if (scheduleDate == null || scheduleDate.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(scheduleDate).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid scheduleDate '%s': expected an ISO-8601 date-time such as 2026-07-05T14:30:00Z.".formatted(scheduleDate), e);
        }
    }

    /**
     * @param executionId the id of the new execution
     * @param namespace the flow's namespace
     * @param flowId the flow's id
     * @param state the execution's state when the call returned
     * @param terminated whether the execution had ended when the call returned
     * @param url the link to the execution in the Kestra UI
     */
    public record Result(String executionId, String namespace, String flowId, String state, boolean terminated, String url) {
    }
}
