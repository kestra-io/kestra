package io.kestra.plugin.core.templating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
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
    title = "Render and run a task from a templated spec.",
    description = """
        Renders a YAML task definition from `spec` using Pebble and executes it. The rendered task must be a RunnableTask and cannot itself be `TemplatedTask`.

        Useful for highly dynamic task definitions driven by inputs or previous outputs."""
)
@Plugin(
    examples = {
        @Example(
            code = """
                id: templated_task
                namespace: company.team
                variables:
                  property: uri
                  value: https://kestra.io
                tasks:
                  - id: templated_task
                    type: io.kestra.plugin.core.templating.TemplatedTask
                    spec: |
                      type: io.kestra.plugin.core.http.Download
                      {{ vars.property }}: {{ vars.value }}
                """
        )
    }
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
                // we set the context classloader to the classloader of the resolved plugin class,
                // so that ServiceLoader lookups inside the task resolve against the correct classloader.
                ClassLoader previous = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(runnableTask.getClass().getClassLoader());
                try {
                    return runnableTask.run(runContext);
                } finally {
                    Thread.currentThread().setContextClassLoader(previous);
                }
            }
            throw new IllegalArgumentException("The templated task must be a runnable task");
        } catch (JsonProcessingException e) {
            throw new IllegalVariableEvaluationException(e);
        }
    }
}
