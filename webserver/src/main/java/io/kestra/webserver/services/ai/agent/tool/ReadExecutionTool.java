package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool returning a compact, human-readable summary of a single execution:
 * overall state and duration plus one line per task run (with state histories for failed runs).
 */
@Singleton
public class ReadExecutionTool implements AiPlatformTool {
    private final ExecutionRepositoryInterface executionRepository;

    @Inject
    public ReadExecutionTool(final ExecutionRepositoryInterface executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Tool(
        name = "read-execution",
        value = "Read a single Kestra execution: its state, duration and per-task-run breakdown (failed runs include their state history). Read-only; use this to diagnose or inspect a specific run when you already know its id. "
            + "Returns an object { id, namespace, flowId, state, startDate, duration, taskRuns } where each task run is { taskId, state, attempts, value, failedAttempts } and `failedAttempts` (only populated for FAILED runs) is an array of { attempt, stateHistory } whose `stateHistory` entries read \"STATE@date\"."
    )
    public Result readExecution(
        @P(name = "executionId", value = "The id of the execution to read") String executionId,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();

        Execution execution = executionRepository.findById(tenant, executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));

        String duration = execution.getState().getDuration().map(Duration::toString).orElse(null);
        List<TaskRun> taskRuns = execution.getTaskRunList();
        List<TaskRunDetail> taskRunDetails = taskRuns == null ? List.of()
            : taskRuns.stream().map(ReadExecutionTool::toTaskRunDetail).toList();

        return new Result(
            execution.getId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getState().getCurrent().name(),
            String.valueOf(execution.getState().getStartDate()),
            duration,
            taskRunDetails
        );
    }

    private static TaskRunDetail toTaskRunDetail(final TaskRun taskRun) {
        List<FailedAttempt> failedAttempts = State.Type.FAILED.equals(taskRun.getState().getCurrent())
            ? toFailedAttempts(taskRun.getAttempts())
            : List.of();
        return new TaskRunDetail(
            taskRun.getTaskId(),
            taskRun.getState().getCurrent().name(),
            taskRun.attemptNumber(),
            taskRun.getValue(),
            failedAttempts
        );
    }

    private static List<FailedAttempt> toFailedAttempts(final List<TaskRunAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return List.of();
        }
        List<FailedAttempt> details = new ArrayList<>(attempts.size());
        for (int i = 0; i < attempts.size(); i++) {
            details.add(new FailedAttempt(i + 1, stateHistory(attempts.get(i).getState())));
        }
        return details;
    }

    private static List<String> stateHistory(final State state) {
        if (state == null || state.getHistories() == null || state.getHistories().isEmpty()) {
            return List.of();
        }
        return state.getHistories().stream()
            .map(history -> history.getState() + "@" + history.getDate())
            .toList();
    }

    /**
     * A single execution with its per-task-run breakdown.
     *
     * @param id the execution id
     * @param namespace the flow's namespace
     * @param flowId the flow's id
     * @param state the current execution state
     * @param startDate the execution start date
     * @param duration the execution duration as an ISO-8601 duration, or null when not yet available
     * @param taskRuns one entry per task run, empty when there are none
     */
    public record Result(String id, String namespace, String flowId, String state, String startDate, String duration, List<TaskRunDetail> taskRuns) {
    }

    /**
     * A single task run of an execution.
     *
     * @param taskId the task id
     * @param state the current task-run state
     * @param attempts the number of attempts
     * @param value the iteration value for looped tasks, or null
     * @param failedAttempts the state history of each attempt, populated only for FAILED runs
     */
    public record TaskRunDetail(String taskId, String state, int attempts, String value, List<FailedAttempt> failedAttempts) {
    }

    /**
     * The state history of a single failed attempt.
     *
     * @param attempt the 1-based attempt number
     * @param stateHistory the ordered "STATE@date" transitions, empty when unavailable
     */
    public record FailedAttempt(int attempt, List<String> stateHistory) {
    }
}
