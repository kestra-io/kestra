package io.kestra.core.models.tasks.runners;

import lombok.Getter;

import java.io.Serial;

@Getter
public class TaskException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int exitCode;
    private final int stdOutCount;
    private final int stdErrCount;

    private transient AbstractLogConsumer logConsumer;

    /**
     * This constructor will certainly be removed in 0.21 as we keep it only because all task runners must be impacted.
     * @deprecated use {@link #TaskException(int, AbstractLogConsumer)} instead.
     */
    @Deprecated(forRemoval = true, since = "0.20.0")
    public TaskException(int exitCode, int stdOutCount, int stdErrCount) {
        this("Command failed with exit code " + exitCode, exitCode, stdOutCount, stdErrCount);
    }

    public TaskException(int exitCode, AbstractLogConsumer logConsumer) {
        this("Command failed with exit code " + exitCode, exitCode, logConsumer);
    }

    /**
     * This constructor will certainly be removed in 0.21 as we keep it only because all task runners must be impacted.
     * @deprecated use {@link #TaskException(String, int, AbstractLogConsumer)} instead.
     */
    @Deprecated(forRemoval = true, since = "0.20.0")
    public TaskException(String message, int exitCode, int stdOutCount, int stdErrCount) {
        super(message);
        this.exitCode = exitCode;
        this.stdOutCount = stdOutCount;
        this.stdErrCount = stdErrCount;
    }

    public TaskException(String message, int exitCode, AbstractLogConsumer logConsumer) {
        super(message);
        this.exitCode = exitCode;
        this.stdOutCount = logConsumer.getStdOutCount();
        this.stdErrCount = logConsumer.getStdErrCount();
        this.logConsumer = logConsumer;
    }
}
