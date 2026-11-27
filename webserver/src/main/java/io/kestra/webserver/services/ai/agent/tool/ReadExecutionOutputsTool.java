package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExecutionOutputService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ReadExecutionOutputsTool implements McpPlatformTool {
    static final int MAX_TASK_RUNS = 50;
    static final int MAX_OUTPUT_CHARS = 4_000;

    private final ExecutionRepositoryInterface executionRepository;
    private final ExecutionOutputService executionOutputService;
    private final TaskOutputRepositoryInterface taskOutputRepository;
    private final TaskOutputService taskOutputService;

    @Inject
    public ReadExecutionOutputsTool(
        final ExecutionRepositoryInterface executionRepository,
        final ExecutionOutputService executionOutputService,
        final TaskOutputRepositoryInterface taskOutputRepository,
        final TaskOutputService taskOutputService) {
        this.executionRepository = executionRepository;
        this.executionOutputService = executionOutputService;
        this.taskOutputRepository = taskOutputRepository;
        this.taskOutputService = taskOutputService;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "read_execution_outputs",
        value = "Read the outputs of a Kestra execution: the flow outputs, set once the execution ends, and what each task run returned. Read-only; use it to see what a run produced. "
            + "Returns an object { executionId, flowOutputs, taskRuns, truncated } where `taskRuns` is an array of { taskRunId, taskId, value, outputs } for the task runs that have outputs, at most " + MAX_TASK_RUNS + ", and `value` is the iteration value of a looped task or null. "
            + "An output larger than " + MAX_OUTPUT_CHARS + " characters is replaced by the first " + MAX_OUTPUT_CHARS + " characters of its JSON, as a string; `truncated` is true when any output was shortened or task runs were left out. "
            + "Files appear as kestra:// URIs."
    )
    public Result readExecutionOutputs(
        @P(name = "executionId", value = "The id of the execution whose outputs to read") String executionId,
        @P(name = "taskId", value = "Only return the outputs of the task runs of this task", required = false) String taskId,
        final AgentCallContext.Context context) {
        Execution execution = executionRepository.findById(context.tenant(), executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));

        List<TaskRun> taskRuns = taskOutputRepository.findByExecution(execution).stream()
            .flatMap(taskOutput -> execution.findTaskRunByTaskRunIdIfPresent(taskOutput.taskRunId()).stream())
            .filter(taskRun -> taskId == null || taskId.isBlank() || taskId.equals(taskRun.getTaskId()))
            .toList();

        Truncation truncation = new Truncation(taskRuns.size() > MAX_TASK_RUNS);
        Object flowOutputs = truncation.limit(readExecutionOutputs(execution));
        List<TaskRunOutputs> taskRunOutputs = taskRuns.stream()
            .limit(MAX_TASK_RUNS)
            .map(taskRun -> new TaskRunOutputs(taskRun.getId(), taskRun.getTaskId(), taskRun.getValue(), truncation.limit(readTaskRunOutputs(taskRun))))
            .toList();

        return new Result(executionId, flowOutputs, taskRunOutputs, truncation.truncated);
    }

    private Map<String, Object> readExecutionOutputs(final Execution execution) {
        try {
            return executionOutputService.getOutputs(execution);
        } catch (InternalException e) {
            throw new IllegalStateException("Unable to read the outputs of execution '%s'.".formatted(execution.getId()), e);
        }
    }

    private Map<String, Object> readTaskRunOutputs(final TaskRun taskRun) {
        try {
            return taskOutputService.getOutputs(taskRun);
        } catch (InternalException e) {
            throw new IllegalStateException("Unable to read the outputs of task run '%s'.".formatted(taskRun.getId()), e);
        }
    }

    private static final class Truncation {
        private boolean truncated;

        private Truncation(final boolean truncated) {
            this.truncated = truncated;
        }

        private Object limit(final Map<String, Object> outputs) {
            if (outputs == null) {
                return null;
            }
            String json;
            try {
                json = JacksonMapper.ofJson().writeValueAsString(outputs);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to serialize the outputs.", e);
            }
            if (json.length() <= MAX_OUTPUT_CHARS) {
                return outputs;
            }
            truncated = true;
            return json.substring(0, MAX_OUTPUT_CHARS);
        }
    }

    /**
     * @param executionId the execution read
     * @param flowOutputs the flow outputs, a JSON prefix string when too large, or null before the execution ends or when the flow declares none
     * @param taskRuns the outputs of each task run that has some
     * @param truncated whether an output was shortened or task runs were left out
     */
    public record Result(String executionId, Object flowOutputs, List<TaskRunOutputs> taskRuns, boolean truncated) {
    }

    /**
     * @param taskRunId the task run id
     * @param taskId the task id
     * @param value the iteration value of a looped task, or null
     * @param outputs what the task run returned, or a JSON prefix string when too large
     */
    public record TaskRunOutputs(String taskRunId, String taskId, String value, Object outputs) {
    }
}
