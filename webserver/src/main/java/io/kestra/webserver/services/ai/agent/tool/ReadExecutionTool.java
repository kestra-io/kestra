package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
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
public class ReadExecutionTool implements McpPlatformTool {
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

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "read_execution",
        value = "Read a single Kestra execution: its state, duration and per-task-run breakdown (failed runs include their state history). Read-only; use this to diagnose or inspect a specific run when you already know its id. "
            + "Returns an object { id, namespace, flowId, flowRevision, state, startDate, endDate, duration, labels, inputs, taskRuns } where `labels` maps each label key to its value, `inputs` maps each input id to the value it was given, "
            + "and each task run is { id, taskId, state, attempts, value, failedAttempts }; `failedAttempts` (only populated for FAILED runs) is an array of { attempt, stateHistory } whose `stateHistory` entries read \"STATE@date\". "
            + "Use read_execution_outputs for what the tasks returned."
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
            execution.getFlowRevision(),
            execution.getState().getCurrent().name(),
            String.valueOf(execution.getState().getStartDate()),
            execution.getState().getEndDate().map(Instant::toString).orElse(null),
            duration,
            labels(execution),
            MapUtils.emptyOnNull(execution.getInputs()),
            taskRunDetails
        );
    }

    static Map<String, String> labels(final Execution execution) {
        Map<String, String> labels = new LinkedHashMap<>();
        ListUtils.emptyOnNull(execution.getLabels()).forEach(label -> labels.put(label.key(), label.value()));
        return labels;
    }

    private static TaskRunDetail toTaskRunDetail(final TaskRun taskRun) {
        List<FailedAttempt> failedAttempts = State.Type.FAILED.equals(taskRun.getState().getCurrent())
            ? toFailedAttempts(taskRun.getAttempts())
            : List.of();
        return new TaskRunDetail(
            taskRun.getId(),
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
     * @param flowRevision the flow revision the execution runs
     * @param state the current execution state
     * @param startDate the execution start date
     * @param endDate the execution end date, or null while it runs
     * @param duration the execution duration as an ISO-8601 duration, or null when not yet available
     * @param labels each label key mapped to its value
     * @param inputs each input id mapped to the value the execution was given
     * @param taskRuns one entry per task run, empty when there are none
     */
    public record Result(String id, String namespace, String flowId, Integer flowRevision, String state, String startDate, String endDate, String duration,
        Map<String, String> labels, Map<String, Object> inputs, List<TaskRunDetail> taskRuns) {
    }

    /**
     * A single task run of an execution.
     *
     * @param id the task run id
     * @param taskId the task id
     * @param state the current task-run state
     * @param attempts the number of attempts
     * @param value the iteration value for looped tasks, or null
     * @param failedAttempts the state history of each attempt, populated only for FAILED runs
     */
    public record TaskRunDetail(String id, String taskId, String state, int attempts, String value, List<FailedAttempt> failedAttempts) {
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
