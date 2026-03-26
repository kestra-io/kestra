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

    public TaskException(int exitCode, AbstractLogConsumer logConsumer) {
        this("Command failed with exit code " + exitCode, exitCode, logConsumer);
    }

    public TaskException(String message, int exitCode, AbstractLogConsumer logConsumer) {
        super(message);
        this.exitCode = exitCode;
        this.stdOutCount = logConsumer.getStdOutCount();
        this.stdErrCount = logConsumer.getStdErrCount();
        this.logConsumer = logConsumer;
    }
}
