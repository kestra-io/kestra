package io.kestra.plugin.core.templating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Templatize a task.",
    description = "This task's `spec` property allows you to fully templatize all task properties using Kestra's Pebble templating. This way, all task properties and their values can be dynamically rendered based on your custom inputs, variables, and outputs from other tasks."
)
@Plugin(
    examples = {
        @Example(
            code = {
                """
                    spec: |
                      type: io.kestra.plugin.fs.http.Download
                      {{ task.property }}: {{ task.value }}"""
            }
        )
    },
    aliases = "io.kestra.core.tasks.templating.TemplatedTask"
)
public class TemplatedTask extends Task implements RunnableTask<Output> {
    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofYaml();

    @NotNull
    @Schema(title = "The templated task specification")
    private Property<String> spec;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String taskSpec = runContext.render(this.spec).as(String.class).orElseThrow();
        try {
            Task task = OBJECT_MAPPER.readValue(taskSpec, Task.class);
            if (task instanceof TemplatedTask) {
                throw new IllegalArgumentException("The templated task cannot be of type 'io.kestra.plugin.core.templating.TemplatedTask'");
            }
            if (task instanceof RunnableTask<?> runnableTask) {
                return runnableTask.run(runContext);
            }
            throw new IllegalArgumentException("The templated task must be a runnable task");
        } catch (JsonProcessingException e) {
            throw new IllegalVariableEvaluationException(e);
        }
    }
}
