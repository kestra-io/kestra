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

import java.util.HashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Output one or more values.",
    description = """
    You can use this task to return some outputs and pass them to downstream tasks.
    It's helpful for parsing and returning values from a task. You can then access these outputs in your downstream tasks
    using the expression `{{ outputs.mytask_id.values.my_output_name }}` and you can see them in the Outputs tab.
    The values can be strings, numbers, arrays, or any valid JSON object.
    """
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
      {{ outputs.output_values.values.execution_data }}"""
        )
    }
)
public class OutputValues extends Task implements RunnableTask<OutputValues.Output> {
    @Schema(
        title = "The templated strings to render.",
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
            title = "The generated values."
        )
        private Map<String, Object> values;
    }
}
