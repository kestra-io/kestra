package io.kestra.core.models.tasks.runners;

import jakarta.annotation.Nullable;
import java.io.Serial;

import lombok.Getter;

@Getter
public class TaskException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int exitCode;
    private final int stdOutCount;
    private final int stdErrCount;

    private transient AbstractLogConsumer logConsumer;

    /**
     * Optional task-runner detail captured before the failure was raised, so the failure
     * branch can still report runner-specific post-execution info (job id, status, etc.)
     * to the topology UI through {@code outputs.taskRunner}.
     */
    @Nullable
    private final TaskRunnerDetailResult details;

    public TaskException(int exitCode, AbstractLogConsumer logConsumer) {
        this("Command failed with exit code " + exitCode, exitCode, logConsumer, null);
    }

    public TaskException(String message, int exitCode, AbstractLogConsumer logConsumer) {
        this(message, exitCode, logConsumer, null);
    }

    public TaskException(int exitCode, AbstractLogConsumer logConsumer, @Nullable TaskRunnerDetailResult details) {
        this("Command failed with exit code " + exitCode, exitCode, logConsumer, details);
    }

    public TaskException(String message, int exitCode, AbstractLogConsumer logConsumer, @Nullable TaskRunnerDetailResult details) {
        super(message);
        this.exitCode = exitCode;
        this.stdOutCount = logConsumer.getStdOutCount();
        this.stdErrCount = logConsumer.getStdErrCount();
        this.logConsumer = logConsumer;
        this.details = details;
    }
}
