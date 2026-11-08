package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KillExecutionTool implements McpPlatformTool {
    private final ExecutionActionSubmitter submitter;
    private final BroadcastQueueInterface<ExecutionKilled> killQueue;
    private final FlowMetaStoreInterface flowMetaStore;
    private final ExecutionService executionService;
    private final ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher;

    @Inject
    public KillExecutionTool(
        final ExecutionActionSubmitter submitter,
        final BroadcastQueueInterface<ExecutionKilled> killQueue,
        final FlowMetaStoreInterface flowMetaStore,
        final ExecutionService executionService,
        final ApplicationEventPublisher<CrudEvent<Execution>> eventPublisher) {
        this.submitter = submitter;
        this.killQueue = killQueue;
        this.flowMetaStore = flowMetaStore;
        this.executionService = executionService;
        this.eventPublisher = eventPublisher;
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
        return McpToolAnnotations.IDEMPOTENT_DESTRUCTIVE;
    }

    @Tool(
        name = "kill_execution",
        value = "Kill a running, paused or queued Kestra execution, stopping its running tasks; by default its subflow executions are killed too. "
            + "Fails when the execution has already ended and has nothing left to kill. Blocks until the executor accepts the kill, then returns an object { executionId }; "
            + "the execution then moves to KILLING and KILLED asynchronously, so check it with read_execution rather than assuming it is already killed."
    )
    public Result killExecution(
        @P(name = "executionId", value = "The id of the execution to kill") String executionId,
        @P(name = "cascade", value = "Whether to also kill the subflow executions it started; defaults to true", required = false) Boolean cascade,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        boolean isOnKillCascade = cascade == null || cascade;
        Execution execution = submitter.findExecution(tenant, executionId);

        if (execution.getState().isTerminated() && !hasWorkToKill(execution, isOnKillCascade)) {
            throw new IllegalStateException(
                "Execution '%s' cannot be killed: it is already in state '%s' and has no running task or subflow execution left."
                    .formatted(executionId, execution.getState().getCurrent())
            );
        }

        eventPublisher.publishEvent(CrudEvent.of(execution, execution.withState(State.Type.KILLING)));
        submitter.submitAndWait(
            executionId, "kill", (operationId, actor) -> killQueue.emit(
                ExecutionKilledExecution.builder()
                    .state(ExecutionKilled.State.REQUESTED)
                    .executionId(executionId)
                    .isOnKillCascade(isOnKillCascade)
                    .tenantId(tenant)
                    .operationId(operationId)
                    .actor(actor)
                    .build()
            )
        );

        return new Result(executionId);
    }

    private boolean hasWorkToKill(final Execution execution, final boolean isOnKillCascade) {
        return flowMetaStore.findByExecutionForRuntime(execution)
            .map(flow -> executionService.hasWorkToKill(flow, execution, isOnKillCascade))
            .orElse(isOnKillCascade);
    }

    /**
     * Acknowledgement that the executor accepted the kill.
     *
     * @param executionId the execution being killed
     */
    public record Result(String executionId) {
    }
}
