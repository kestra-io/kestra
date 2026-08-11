package io.kestra.core.models.tasks.runners;

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
     * Optional post-execution detail produced by the task runner before it failed.
     * Carried here so the script task can still expose it as the {@code taskRunner}
     * output on the failure path (e.g. to render a FAILED state in the topology view).
     */
    private final transient TaskRunnerDetailResult details;

    public TaskException(int exitCode, AbstractLogConsumer logConsumer) {
        this("Command failed with exit code " + exitCode, exitCode, logConsumer, null);
    }

    public TaskException(String message, int exitCode, AbstractLogConsumer logConsumer) {
        this(message, exitCode, logConsumer, null);
    }

    public TaskException(int exitCode, AbstractLogConsumer logConsumer, TaskRunnerDetailResult details) {
        this("Command failed with exit code " + exitCode, exitCode, logConsumer, details);
    }

    public TaskException(String message, int exitCode, AbstractLogConsumer logConsumer, TaskRunnerDetailResult details) {
        super(message);
        this.exitCode = exitCode;
        this.stdOutCount = logConsumer.getStdOutCount();
        this.stdErrCount = logConsumer.getStdErrCount();
        this.logConsumer = logConsumer;
        this.details = details;
    }
}
