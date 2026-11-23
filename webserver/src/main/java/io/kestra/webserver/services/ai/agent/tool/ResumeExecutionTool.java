package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.Resume;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.MapUtils;
import io.kestra.plugin.core.flow.PausableTask;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ResumeExecutionTool implements McpPlatformTool {
    private final ExecutionService executionService;
    private final FlowRepositoryInterface flowRepository;
    private final ExecutionCommandSubmitter commandSubmitter;

    @Inject
    public ResumeExecutionTool(
        final ExecutionService executionService,
        final FlowRepositoryInterface flowRepository,
        final ExecutionCommandSubmitter commandSubmitter) {
        this.executionService = executionService;
        this.flowRepository = flowRepository;
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
        name = "resume_execution", value = "Resume a PAUSED Kestra execution, such as one waiting on a Pause task, so it runs its next tasks. "
            + "An execution paused on an Approval task cannot be resumed here: it must be approved or rejected by a reviewer in the UI. "
            + "Pass the paused task's onResume input values in inputs; omitted inputs take their defaults, and read_execution shows which task is paused. "
            + "Blocks until the executor accepts the resume, then returns { executionId }. The execution then runs asynchronously, so do NOT assume its final state from this call."
    )
    public Result resumeExecution(
        @P(name = "executionId", value = "The id of the paused execution to resume") String executionId,
        @P(name = "inputs", value = "Optional values for the paused task's onResume inputs, keyed by input id", required = false) Map<String, Object> inputs,
        final AgentCallContext.Context context) {
        Execution execution = executionService.getExecutionIfPause(context.tenant(), executionId, true);
        Flow flow = flowRepository.findByExecutionWithoutAcl(execution);

        try {
            if (executionService.isPausedOnApproval(execution, flow)) {
                throw new ConflictException(ExecutionService.APPROVAL_RESUME_REFUSED.formatted(executionId));
            }
        } catch (InternalException | FlowProcessingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        Map<String, Object> resumeInputs = executionService.readResumeInputs(execution, flow, MapUtils.emptyOnNull(inputs)).block();
        PausableTask.Resumed resumed = resumed(context);
        commandSubmitter.submitAndWait(
            executionId, "resume",
            (queue, operationId, actor) -> queue.emit(Resume.from(execution, resumed, resumeInputs).withOperationId(operationId).withActor(actor))
        );

        return new Result(executionId);
    }

    protected PausableTask.Resumed resumed(final AgentCallContext.Context context) {
        return PausableTask.Resumed.now();
    }

    /**
     * Acknowledgement that a resume was accepted by the executor.
     *
     * @param executionId the execution that was resumed
     */
    public record Result(String executionId) {
    }
}
