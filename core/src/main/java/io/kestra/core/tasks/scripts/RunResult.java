package io.kestra.core.tasks.scripts;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Deprecated
@AllArgsConstructor
@Getter
public class RunResult {
    int exitCode;
    AbstractLogThread stdOut;
    AbstractLogThread stdErr;
}
