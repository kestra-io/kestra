package io.kestra.core.tasks.states;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor

public abstract class AbstractState extends Task {
    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {};

    @Schema(
        title = "The name of the state file."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @Builder.Default
    protected String name = "default";

    @Schema(
        title = "Share state for the current namespace.",
        description = "By default, the state is isolated by namespace **and** flow, setting to `true` will allow to share the state between the **same** namespace"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private final Boolean namespace = false;

    @Schema(
        title = "Isolate the state with `taskrun.value`.",
        description = "By default, the state will be isolated with `taskrun.value` (during iteration with each). Setting to `false` will allow using the same state for every run of the iteration."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private final Boolean taskrunValue = true;


    protected Map<String, Object> get(RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        try (InputStream taskStateFile = runContext.storage().getTaskStateFile("tasks-states", runContext.render(this.name), this.namespace, this.taskrunValue)) {
            return JacksonMapper.ofJson(false).readValue(taskStateFile, TYPE_REFERENCE);
        }
    }

    protected Pair<URI, Map<String, Object>> merge(RunContext runContext, Map<String, Object> map) throws IllegalVariableEvaluationException, IOException {
        Map<String, Object> current;

        try {
            current = this.get(runContext);
        } catch (FileNotFoundException e) {
            current = Map.of();
        }

        Map<String, Object> merge = MapUtils.merge(current, runContext.render(map));

        URI uri = runContext.storage().putTaskStateFile(
            JacksonMapper.ofJson(false).writeValueAsBytes(merge),
            "tasks-states",
            runContext.render(this.name),
            this.namespace,
            this.taskrunValue
        );

        return Pair.of(uri, merge);
    }

    protected boolean delete(RunContext runContext) throws IllegalVariableEvaluationException, IOException {
        return runContext.storage().deleteTaskStateFile("tasks-states", runContext.render(this.name), this.namespace, this.taskrunValue);
    }
}
