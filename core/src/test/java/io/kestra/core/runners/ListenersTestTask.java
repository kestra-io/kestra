package io.kestra.core.runners;

import lombok.*;
import lombok.experimental.SuperBuilder;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.serializers.JacksonMapper;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class ListenersTestTask extends Task implements RunnableTask<ListenersTestTask.Output> {
    @SuppressWarnings("unchecked")
    @Override
    public ListenersTestTask.Output run(RunContext runContext) throws Exception {
        Execution execution = JacksonMapper.toMap((Map<String, Object>) runContext.getVariables().get("execution"), Execution.class);

        return Output.builder()
            .value(execution.toString())
            .build();
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        private String value;
    }
}
