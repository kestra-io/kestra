package io.kestra.plugin.core.templating;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.flows.PluginDefault;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.ListUtils;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

        The plugin defaults declared for the rendered task type are applied to it, as they are for any task declared directly in the flow.

        Useful for highly dynamic task definitions driven by inputs or previous outputs."""
)
@Plugin(
    examples = {
        @Example(
            code = """
                spec: |
                  type: io.kestra.plugin.core.http.Download
                  {{ task.property }}: {{ task.value }}
                """
        )
    },
    aliases = "io.kestra.core.tasks.templating.TemplatedTask"
)
public class TemplatedTask extends Task implements RunnableTask<Output> {
    private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofYaml();

    @NotNull
    @Schema(title = "The templated task specification")
    private Property<String> spec;

    /**
     * The plugin defaults resolved for the flow this task belongs to, set while the flow is parsed.
     *
     * <p>
     * The templated plugin is only known once {@code spec} has been rendered, which happens on a worker, long
     * after the flow was parsed and where the defaults can no longer be resolved. They are therefore resolved
     * with the rest of the flow's defaults and carried here — see
     * {@link PluginDefaultService#applyResolvedDefaults(Map, List)}.
     * </p>
     */
    @Hidden
    @Setter // we have no other option here as we need to update the task inside the flow when parsing it
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<PluginDefault> resolvedPluginDefaults;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String taskSpec = runContext.render(this.spec).as(String.class).orElseThrow();
        try {
            Task task = parseTask(runContext, taskSpec);
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

    /**
     * Builds the templated task from its rendered spec: plugin defaults are applied to it as they would have been
     * to a task declared directly in the flow, then the task is validated so that a missing required property is
     * reported as such instead of failing the plugin at runtime.
     */
    private Task parseTask(RunContext runContext, String taskSpec) throws JsonProcessingException {
        Map<String, Object> spec = OBJECT_MAPPER.readValue(taskSpec, JacksonMapper.MAP_TYPE_REFERENCE);

        // the task built here is a task of this flow: it inherits this task's id unless the spec sets its own,
        // as the id is otherwise required and would fail validation.
        if (this.id != null) {
            spec.putIfAbsent("id", this.id);
        }

        if (!ListUtils.isEmpty(this.resolvedPluginDefaults)) {
            spec = ((DefaultRunContext) runContext).getApplicationContext()
                .getBean(PluginDefaultService.class)
                .applyResolvedDefaults(spec, this.resolvedPluginDefaults);
        }

        Task task;
        try {
            task = OBJECT_MAPPER.convertValue(spec, Task.class);
        } catch (IllegalArgumentException e) {
            // convertValue wraps deserialization failures; unwrap so they are reported as spec errors
            if (e.getCause() instanceof JsonProcessingException cause) {
                throw cause;
            }
            throw e;
        }

        runContext.validate(task);

        return task;
    }
}
