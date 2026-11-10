package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Pause;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class PauseExecutionTool implements McpPlatformTool {
    private final ExecutionCommandSubmitter submitter;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @Inject
    public PauseExecutionTool(final ExecutionCommandSubmitter submitter, final DispatchQueueInterface<ExecutionCommand> executionCommandQueue) {
        this.submitter = submitter;
        this.executionCommandQueue = executionCommandQueue;
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
        return McpToolAnnotations.IDEMPOTENT_UPDATE;
    }

    @Tool(
        name = "pause_execution",
        value = "Pause a RUNNING Kestra execution: tasks already running finish, and no new task starts until the execution is resumed. Fails when the execution is not running. "
            + "Blocks until the executor accepts the pause, then returns an object { executionId }."
    )
    public Result pauseExecution(
        @P(name = "executionId", value = "The id of the execution to pause") String executionId,
        final AgentCallContext.Context context) {
        Execution execution = submitter.findExecution(context.tenant(), executionId);

        if (!execution.getState().isRunning()) {
            throw new IllegalStateException(
                "Execution '%s' cannot be paused: current state is '%s', expected RUNNING.".formatted(executionId, execution.getState().getCurrent())
            );
        }

        submitter.submitAndWait(
            executionId, "pause",
            (operationId, actor) -> executionCommandQueue.emit(Pause.from(execution).withOperationId(operationId).withActor(actor))
        );

        return new Result(executionId);
    }

    /**
     * Acknowledgement that the executor accepted the pause.
     *
     * @param executionId the execution being paused
     */
    public record Result(String executionId) {
    }
}
