package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.stream.Collectors;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

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
        value = "Read a single Kestra execution: its state, duration and per-task-run breakdown (failed runs include their state history). Read-only; use this to diagnose or inspect a specific run when you already know its id."
    )
    public String readExecution(
        @P(name = "executionId", value = "The id of the execution to read") String executionId,
        @TenantId @P(name = "tenantId", value = "The tenant to run against; omit to use your current tenant", required = false) String tenantId) {
        String tenant = AgentCallContext.resolveTenant(tenantId);

        Execution execution = executionRepository.findById(tenant, executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '" + executionId + "'"));

        StringBuilder out = new StringBuilder()
            .append("Execution '").append(execution.getId()).append("' of flow ")
            .append(execution.getNamespace()).append('.').append(execution.getFlowId()).append('\n')
            .append("State: ").append(execution.getState().getCurrent());
        execution.getState().getDuration()
            .ifPresent(duration -> out.append(" (started ").append(execution.getState().getStartDate()).append(", duration ").append(duration).append(')'));
        out.append('\n');

        List<TaskRun> taskRuns = execution.getTaskRunList();
        if (taskRuns == null || taskRuns.isEmpty()) {
            out.append("No task runs.");
        } else {
            out.append("Task runs:");
            taskRuns.forEach(taskRun -> out.append('\n').append(formatTaskRun(taskRun)));
        }
        return out.toString();
    }

    private static String formatTaskRun(final TaskRun taskRun) {
        StringBuilder line = new StringBuilder()
            .append("- ").append(taskRun.getTaskId())
            .append(" [").append(taskRun.getState().getCurrent()).append(']')
            .append(" attempts=").append(taskRun.attemptNumber());
        if (taskRun.getValue() != null) {
            line.append(" value=").append(taskRun.getValue());
        }
        if (State.Type.FAILED.equals(taskRun.getState().getCurrent())) {
            line.append(formatFailedAttempts(taskRun.getAttempts()));
        }
        return line.toString();
    }

    private static String formatFailedAttempts(final List<TaskRunAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return "";
        }
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < attempts.size(); i++) {
            details.append("\n    attempt ").append(i + 1).append(": ").append(formatHistories(attempts.get(i).getState()));
        }
        return details.toString();
    }

    private static String formatHistories(final State state) {
        if (state == null || state.getHistories() == null || state.getHistories().isEmpty()) {
            return "no state history";
        }
        return state.getHistories().stream()
            .map(history -> history.getState() + "@" + history.getDate())
            .collect(Collectors.joining(" -> "));
    }
}
