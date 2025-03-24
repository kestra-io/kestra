package io.kestra.core.tasks.test;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin
public class NullOutputTask extends Task implements RunnableTask<NullOutputTask.Output> {

    @Override
    public Output run(RunContext runContext) throws Exception {
        return Output.builder().build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private String value;
    }
}
