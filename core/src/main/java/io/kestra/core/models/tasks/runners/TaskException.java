package io.kestra.core.models.tasks.runners;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;

@Getter
@Builder
public class TaskException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int exitCode;
    private final int stdOutSize;
    private final int stdErrSize;

    public TaskException(int exitCode, int stdOutSize, int stdErrSize) {
        this("Command failed with code " + exitCode, exitCode, stdOutSize, stdErrSize);
    }

    public TaskException(String message, int exitCode, int stdOutSize, int stdErrSize) {
        super(message);
        this.exitCode = exitCode;
        this.stdOutSize = stdOutSize;
        this.stdErrSize = stdErrSize;
    }
}
