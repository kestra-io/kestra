package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.Replay;
import io.kestra.core.executor.command.UpdateLabels;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ExecutionReplayService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ReplayExecutionTool implements McpPlatformTool {
    private final ExecutionRepositoryInterface executionRepository;
    private final ExecutionReplayService executionReplayService;
    private final ExecutionCommandSubmitter commandSubmitter;

    @Inject
    public ReplayExecutionTool(
        final ExecutionRepositoryInterface executionRepository,
        final ExecutionReplayService executionReplayService,
        final ExecutionCommandSubmitter commandSubmitter) {
        this.executionRepository = executionRepository;
        this.executionReplayService = executionReplayService;
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
        name = "replay_execution", value = "Replay a terminated Kestra execution as a NEW execution, either from the start or from one of its task runs, optionally on another flow revision. "
            + "Unlike restart_execution, which re-runs the failed tasks of the same execution, this creates a separate execution and leaves the original untouched; every call creates another one. "
            + "Use list_executions to find the execution and read_execution to find a task run id. "
            + "Blocks until the new execution is created, then returns { executionId, replayedFromExecutionId, namespace, flowId, flowRevision }, where executionId is the new execution. "
            + "The new execution then runs asynchronously, so do NOT assume its final state from this call."
    )
    public Result replayExecution(
        @P(name = "executionId", value = "The id of the terminated execution to replay") String executionId,
        @P(name = "taskRunId", value = "Optional id of the task run to replay from; omit to replay the whole execution", required = false) String taskRunId,
        @P(name = "revision", value = "Optional flow revision to run the replay on; omit to keep the revision of the original execution", required = false) Integer revision,
        final AgentCallContext.Context context) {
        Execution execution = executionRepository.findById(context.tenant(), executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));

        if (taskRunId != null && execution.getTaskRunList().stream().noneMatch(taskRun -> taskRun.getId().equals(taskRunId))) {
            throw new IllegalArgumentException("Task run '%s' not found in execution '%s'.".formatted(taskRunId, executionId));
        }

        Flow flow;
        try {
            flow = executionReplayService.validateReplay(execution, taskRunId, revision);
        } catch (InternalException e) {
            throw new IllegalStateException("Execution '%s' cannot be replayed: %s".formatted(executionId, e.getMessage()), e);
        }

        String newExecutionId = IdUtils.create();
        List<Label> labels = new ArrayList<>(execution.getLabels());
        if (!labels.contains(new Label(Label.REPLAYED, "true"))) {
            labels.add(new Label(Label.REPLAYED, "true"));
        }
        commandSubmitter.submitAndWait(
            newExecutionId, "replay", (queue, operationId, actor) ->
            {
                queue.emit(Replay.from(execution, newExecutionId, taskRunId, revision, null).withOperationId(operationId).withActor(actor));
                queue.emit(UpdateLabels.from(execution, labels).withActor(actor));
            }
        );

        return new Result(newExecutionId, executionId, execution.getNamespace(), execution.getFlowId(), flow.getRevision());
    }

    /**
     * @param executionId the new execution created by the replay
     * @param replayedFromExecutionId the execution that was replayed
     * @param flowRevision the flow revision the new execution runs
     */
    public record Result(String executionId, String replayedFromExecutionId, String namespace, String flowId, Integer flowRevision) {
    }
}
