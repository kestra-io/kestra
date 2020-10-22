package org.kestra.core.models.executions;

import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.DeletedInterface;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

@Value
@Builder
public class LogEntry implements DeletedInterface {
    @NotNull
    private String namespace;

    @NotNull
    private String flowId;

    private String taskId;

    private String executionId;

    private String taskRunId;

    private Integer attemptNumber;

    private String triggerId;

    private Instant timestamp;

    private Level level;

    private String thread;

    private String message;

    @NotNull
    private boolean deleted = false;

    public static List<Level> findLevelsByMin(Level minLevel) {
        if (minLevel == null) {
            return Arrays.asList(Level.values());
        }

        return Arrays.stream(Level.values())
            .filter(level -> level.toInt() >= minLevel.toInt())
            .collect(Collectors.toList());
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
}
