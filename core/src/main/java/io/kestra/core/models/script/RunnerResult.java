package io.kestra.core.models.script;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RunnerResult {
    private int exitCode;
    private AbstractLogConsumer logConsumer;
}
