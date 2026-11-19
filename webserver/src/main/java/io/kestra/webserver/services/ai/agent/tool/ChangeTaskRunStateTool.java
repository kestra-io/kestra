package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.executor.command.ChangeTaskRunState;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ChangeTaskRunStateTool implements McpPlatformTool {
    private final ExecutionRepositoryInterface executionRepository;
    private final ExecutionCommandSubmitter commandSubmitter;

    @Inject
    public ChangeTaskRunStateTool(final ExecutionRepositoryInterface executionRepository, final ExecutionCommandSubmitter commandSubmitter) {
        this.executionRepository = executionRepository;
        this.commandSubmitter = commandSubmitter;
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
        name = "change_task_run_state", value = "Change the state of ONE task run in a terminated, non-killed Kestra execution, which re-runs the execution from that point: "
            + "marking a failed task run SUCCESS or WARNING lets the tasks after it run, and marking a task run FAILED re-applies the flow's error handling. "
            + "To force the state of the WHOLE execution without re-running anything, use change_execution_state instead. Use read_execution to find the task run id. "
            + "Blocks until the executor accepts the change, then returns { executionId, taskRunId, state }. The execution then runs asynchronously, so do NOT assume its final state from this call."
    )
    public Result changeTaskRunState(
        @P(name = "executionId", value = "The id of the terminated execution") String executionId,
        @P(name = "taskRunId", value = "The id of the task run whose state to change") String taskRunId,
        @P(name = "state", value = "The new state of the task run") TaskRunState state,
        final AgentCallContext.Context context) {
        Execution execution = executionRepository.findById(context.tenant(), executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));

        if (!execution.getState().canChangeStatus()) {
            throw new IllegalStateException(
                "Execution '%s' task run states cannot be changed: current state is '%s', expected terminated and not killed."
                    .formatted(executionId, execution.getState().getCurrent())
            );
        }
        if (execution.getTaskRunList() == null || execution.getTaskRunList().stream().noneMatch(taskRun -> taskRun.getId().equals(taskRunId))) {
            throw new IllegalArgumentException("Task run '%s' not found in execution '%s'.".formatted(taskRunId, executionId));
        }

        State.Type newState = State.Type.valueOf(state.name());
        commandSubmitter.submitAndWait(
            executionId, "change",
            (queue, operationId, actor) -> queue.emit(ChangeTaskRunState.from(execution, taskRunId, newState).withOperationId(operationId).withActor(actor))
        );

        return new Result(executionId, taskRunId, newState);
    }

    public enum TaskRunState {
        SUCCESS,
        WARNING,
        FAILED
    }

    /**
     * Acknowledgement that a task run state change was accepted by the executor.
     *
     * @param executionId the execution re-running from the changed task run
     * @param taskRunId the task run whose state was changed
     * @param state the state the task run was given
     */
    public record Result(String executionId, String taskRunId, State.Type state) {
    }
}
