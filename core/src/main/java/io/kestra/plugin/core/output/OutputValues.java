package io.kestra.plugin.core.output;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Emit custom values from a task.",
    description = """
        Renders the provided map and returns it under `outputs.<taskId>.values`. Accepts strings, numbers, arrays, or JSON objects; templated entries are rendered with the current context.

        Use to surface intermediate data for downstream tasks or inspection in the Outputs tab."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: outputs_flow
                namespace: company.team

                tasks:
                  - id: output_values
                    type: io.kestra.plugin.core.output.OutputValues
                    values:
                      taskrun_data: "{{ task.id }} > {{ taskrun.startDate }}"
                      execution_data: "{{ flow.id }} > {{ execution.startDate }}"
                      number_value: 42
                      array_value: ["{{ task.id }}", "{{ flow.id }}", "static value"]
                      nested_object:
                        key1: "value1"
                        key2: "{{ execution.id }}"

                  - id: log_values
                    type: io.kestra.plugin.core.log.Log
                    message: |
                      Got the following outputs from the previous task:
                      {{ outputs.output_values.values.taskrun_data }}
                      {{ outputs.output_values.values.execution_data }}
                      {{ outputs.output_values.values.number_value }}
                      {{ outputs.output_values.values.array_value[1] }}
                      {{ outputs.output_values.values.nested_object.key2 }}
                """
        )
    }
)
public class OutputValues extends Task implements RunnableTask<OutputValues.Output> {
    @Schema(
        title = "The templated strings to render",
        description = "These values can be strings, numbers, arrays, or objects. Templated strings (enclosed in {{ }}) will be rendered using the current context."
    )
    private Property<Map<String, Object>> values;


    @Override
    public OutputValues.Output run(RunContext runContext) throws Exception {
        return Output.builder()
            .values(runContext.render(values).asMap(String.class, Object.class))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The generated values"
        )
        private Map<String, Object> values;
    }
}
