package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import org.slf4j.event.Level;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Value
@Builder(toBuilder = true)
public class LogEntry implements DeletedInterface {
    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @Nullable
    String taskId;

    @Nullable
    String executionId;

    @Nullable
    String taskRunId;

    @Nullable
    @JsonInclude
    Integer attemptNumber;

    @Nullable
    String triggerId;

    Instant timestamp;

    Level level;

    String thread;

    String message;

    @NotNull
    @Builder.Default
    boolean deleted = false;

    public static List<String> findLevelsByMin(Level minLevel) {
        if (minLevel == null) {
            return Arrays.stream(Level.values()).map(Enum::name).collect(Collectors.toList());
        }

        return Arrays.stream(Level.values())
            .filter(level -> level.toInt() >= minLevel.toInt())
            .map(Enum::name)
            .collect(Collectors.toList());
    }

    public static LogEntry of(Execution execution) {
        return LogEntry.builder()
            .namespace(execution.getNamespace())
            .flowId(execution.getFlowId())
            .executionId(execution.getId())
            .build();
    }

    public static LogEntry of(TaskRun taskRun) {
        return LogEntry.builder()
            .namespace(taskRun.getNamespace())
            .flowId(taskRun.getFlowId())
            .taskId(taskRun.getTaskId())
            .executionId(taskRun.getExecutionId())
            .taskRunId(taskRun.getId())
            .attemptNumber(taskRun.attemptNumber())
            .build();
    }

    public static LogEntry of(Flow flow, AbstractTrigger abstractTrigger) {
        return LogEntry.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(abstractTrigger.getId())
            .build();
    }

    public static String toPrettyString(LogEntry logEntry) {
        return logEntry.getTimestamp().toString() + " " + logEntry.getLevel() + " " + logEntry.getMessage();
    }
}
