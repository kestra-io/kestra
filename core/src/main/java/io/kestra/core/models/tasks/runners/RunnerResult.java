package io.kestra.core.models.tasks.runners;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RunnerResult {
    private int exitCode;
    private AbstractLogConsumer logConsumer;
}
