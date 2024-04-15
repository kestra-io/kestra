package io.kestra.core.tasks.outputs;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Task that allows output of multiples values.",
    description = "This task is mostly useful for debugging purpose.\n\n" +
        "It allows you to see inputs or output variables or to debug some templated functions."
)
@Plugin(
    examples = {
        @Example(
            code = """
                values:
                    taskInfo: \"{{ task.id }} > {{ taskrun.startDate }}\"
                """
        )
    }
)
public class OutputValues extends Task implements RunnableTask<OutputValues.Output> {
    @Schema(
        title = "The templated strings to render."
    )
    private HashMap<String, String> values;


    @Override
    public OutputValues.Output run(RunContext runContext) throws Exception {
        return OutputValues.Output.builder()
            .values(runContext.renderMap(values))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The generated values."
        )
        private Map<String, String> values;
    }
}
