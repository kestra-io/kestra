package io.kestra.core.models.script;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScriptException extends Exception {
    private final int exitCode;
    private final int stdOutSize;
    private final int stdErrSize;

    public ScriptException(int exitCode, int stdOutSize, int stdErrSize) {
        this("Command failed with code " + exitCode, exitCode, stdOutSize, stdErrSize);
    }

    public ScriptException(String message, int exitCode, int stdOutSize, int stdErrSize) {
        super(message);
        this.exitCode = exitCode;
        this.stdOutSize = stdOutSize;
        this.stdErrSize = stdErrSize;
    }
}
