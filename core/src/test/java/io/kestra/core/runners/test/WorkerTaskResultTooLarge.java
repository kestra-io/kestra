package io.kestra.core.runners.test;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin
public class WorkerTaskResultTooLarge extends Task implements RunnableTask<WorkerTaskResultTooLarge.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        char[] chars = new char[1100000];
        Arrays.fill(chars, 'a');

        return Output.builder().value(new String(chars)).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        public String value;
    }
}
