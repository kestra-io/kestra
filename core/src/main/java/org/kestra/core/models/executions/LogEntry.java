package org.kestra.core.models.executions;

import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.DeletedInterface;
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

    @NotNull
    private String taskId;

    @NotNull
    private String executionId;

    @NotNull
    private String taskRunId;

    @NotNull
    private int attemptNumber;

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
}
