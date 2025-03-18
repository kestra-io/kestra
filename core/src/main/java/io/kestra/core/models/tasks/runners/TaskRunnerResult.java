package io.kestra.core.models.tasks.runners;

import io.kestra.core.models.tasks.Output;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;

@AllArgsConstructor
@Getter
@SuperBuilder
@NoArgsConstructor
public class TaskRunnerResult<T extends TaskRunnerDetailResult> implements Output {
    private int exitCode;

    private AbstractLogConsumer logConsumer;

    @Nullable
    private T details;

    public TaskRunnerResult(int exitCode, AbstractLogConsumer logConsumer) {
        this.exitCode = exitCode;
        this.logConsumer = logConsumer;
    }
}
