package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.UpdateStatus;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ChangeExecutionStateTool implements McpPlatformTool {
    private final ExecutionActionSubmitter submitter;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @Inject
    public ChangeExecutionStateTool(final ExecutionActionSubmitter submitter, final DispatchQueueInterface<ExecutionCommand> executionCommandQueue) {
        this.submitter = submitter;
        this.executionCommandQueue = executionCommandQueue;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.MUTATE;
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
        name = "change_execution_state",
        value = "Overwrite the final state of an ended Kestra execution, e.g. mark a FAILED run as SUCCESS once the failure was handled by hand. No task is re-run. "
            + "Only executions that ended in a state other than KILLED can be changed. Blocks until the executor applies the change, then returns an object { executionId, state }."
    )
    public Result changeExecutionState(
        @P(name = "executionId", value = "The id of the execution whose state to change") String executionId,
        @P(name = "state", value = "The state to set") TargetState state,
        final AgentCallContext.Context context) {
        Execution execution = submitter.findExecution(context.tenant(), executionId);

        if (!execution.getState().canChangeStatus()) {
            throw new IllegalStateException(
                "The state of execution '%s' cannot be changed: current state is '%s', expected an ended execution that was not killed."
                    .formatted(executionId, execution.getState().getCurrent())
            );
        }

        State.Type target = State.Type.valueOf(state.name());
        submitter.submitAndWait(
            executionId, "change the state of",
            (operationId, actor) -> executionCommandQueue.emit(UpdateStatus.from(execution, target).withOperationId(operationId).withActor(actor))
        );

        return new Result(executionId, target.name());
    }

    /** The final states an ended execution can be moved to. */
    public enum TargetState {
        SUCCESS,
        WARNING,
        FAILED,
        CANCELLED,
        SKIPPED
    }

    /**
     * @param executionId the execution whose state was changed
     * @param state the state it now has
     */
    public record Result(String executionId, String state) {
    }
}
